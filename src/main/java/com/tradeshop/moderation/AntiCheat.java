package com.tradeshop.moderation;

import com.tradeshop.TradeShop;
import com.tradeshop.config.TradeShopConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Lightweight automatic ("AI") cheat heuristics. Nothing here is authoritative — each detector just adds an
 * {@code [AUTO]} report to the {@code /cheatcheck} list and pings online admins, who review and decide. It
 * never bans on its own, and each detector is deliberately conservative to limit false positives.
 */
public final class AntiCheat {
	private static final Set<Block> VALUABLE_ORES = Set.of(
			Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE, Blocks.ANCIENT_DEBRIS,
			Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE, Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE,
			Blocks.NETHER_GOLD_ORE);

	/** Consecutive ticks over the speed/hover threshold before flagging (~1-1.5s), to ignore momentary bursts. */
	private static final int SPEED_TICKS = 20;
	private static final int HOVER_TICKS = 30;
	/** Above this blocks/sec we assume a teleport, not real movement, and skip the sample. */
	private static final double TELEPORT_GUARD = 40.0;

	private final Map<UUID, Deque<Long>> oreBreaks = new HashMap<>();
	private final Map<UUID, Deque<Long>> reachViolations = new HashMap<>();
	private final Map<UUID, Track> tracks = new HashMap<>();
	private final Map<String, Long> flagCooldown = new HashMap<>();

	private static final AntiCheat INSTANCE = new AntiCheat();

	private AntiCheat() {
	}

	public static void register() {
		INSTANCE.registerEvents();
	}

	private void registerEvents() {
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (enabled() && player instanceof ServerPlayer attacker && entity instanceof LivingEntity && attacker != entity) {
				GameType mode = attacker.gameMode.getGameModeForPlayer();
				if (mode == GameType.SURVIVAL || mode == GameType.ADVENTURE) {
					// Measure to the nearest point of the target's hitbox, not its center, and require
					// several violations before flagging so a single laggy/knockback hit doesn't trip it.
					Vec3 eye = attacker.getEyePosition();
					AABB box = entity.getBoundingBox();
					double nx = clamp(eye.x, box.minX, box.maxX);
					double ny = clamp(eye.y, box.minY, box.maxY);
					double nz = clamp(eye.z, box.minZ, box.maxZ);
					double dist = eye.distanceTo(new Vec3(nx, ny, nz));
					if (dist > TradeShopConfig.get().aiReachThreshold) {
						recordReach(attacker, dist);
					}
				}
			}
			return InteractionResult.PASS;
		});

		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
			if (enabled() && player instanceof ServerPlayer miner && VALUABLE_ORES.contains(state.getBlock())) {
				recordOreBreak(miner);
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(this::tickMovement);
	}

	private void recordReach(ServerPlayer attacker, double dist) {
		long now = System.currentTimeMillis();
		long windowMs = TradeShopConfig.get().aiReachWindowSeconds * 1000L;
		Deque<Long> hits = reachViolations.computeIfAbsent(attacker.getUUID(), k -> new ArrayDeque<>());
		hits.addLast(now);
		while (!hits.isEmpty() && now - hits.peekFirst() > windowMs) {
			hits.pollFirst();
		}
		if (hits.size() >= TradeShopConfig.get().aiReachViolationsToFlag) {
			flag(attacker, "reach", String.format("%.1f blocks, %dx", dist, hits.size()));
			hits.clear();
		}
	}

	private static double clamp(double value, double min, double max) {
		return value < min ? min : Math.min(value, max);
	}

	private void recordOreBreak(ServerPlayer miner) {
		long now = System.currentTimeMillis();
		long windowMs = TradeShopConfig.get().aiOreWindowSeconds * 1000L;
		Deque<Long> times = oreBreaks.computeIfAbsent(miner.getUUID(), k -> new ArrayDeque<>());
		times.addLast(now);
		while (!times.isEmpty() && now - times.peekFirst() > windowMs) {
			times.pollFirst();
		}
		if (times.size() >= TradeShopConfig.get().aiOreThreshold) {
			flag(miner, "x-ray", "mining valuable ores too fast (" + times.size() + " in "
					+ (TradeShopConfig.get().aiOreWindowSeconds / 60) + "m)");
			times.clear();
		}
	}

	private void tickMovement(MinecraftServer server) {
		if (!enabled()) {
			return;
		}
		double speedThreshold = TradeShopConfig.get().aiSpeedThreshold;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Track track = tracks.computeIfAbsent(player.getUUID(), k -> new Track());
			Vec3 now = player.position();
			Vec3 last = track.pos;
			track.pos = now;

			GameType mode = player.gameMode.getGameModeForPlayer();
			boolean skip = last == null
					|| player.level() != track.level
					|| mode == GameType.SPECTATOR || mode == GameType.CREATIVE
					|| player.isPassenger() || player.isFallFlying() || player.getAbilities().flying
					|| player.onClimbable() || player.isInWater() || player.isInLava()
					|| player.isAutoSpinAttack() || inCobweb(player)
					|| player.hasEffect(MobEffects.SPEED) || player.hasEffect(MobEffects.DOLPHINS_GRACE)
					|| player.hasEffect(MobEffects.LEVITATION);
			track.level = player.level();
			if (skip) {
				track.speedTicks = 0;
				track.hoverTicks = 0;
				continue;
			}

			double dx = now.x - last.x;
			double dz = now.z - last.z;
			double horizontal = Math.sqrt(dx * dx + dz * dz) * 20.0;
			if (horizontal > TELEPORT_GUARD) {
				track.speedTicks = 0;
				track.hoverTicks = 0;
				continue;
			}

			// Speed.
			if (horizontal > speedThreshold) {
				if (++track.speedTicks >= SPEED_TICKS) {
					flag(player, "speed", String.format("%.1f blocks/sec", horizontal));
					track.speedTicks = 0;
				}
			} else {
				track.speedTicks = 0;
			}

			// Fly / hover: airborne with near-zero vertical movement for a sustained stretch.
			double dy = now.y - last.y;
			if (!player.onGround() && Math.abs(dy) < 0.06) {
				if (++track.hoverTicks >= HOVER_TICKS) {
					flag(player, "fly", "hovering in mid-air");
					track.hoverTicks = 0;
				}
			} else {
				track.hoverTicks = 0;
			}
		}
	}

	private void flag(ServerPlayer target, String category, String detail) {
		long now = System.currentTimeMillis();
		String key = target.getUUID() + "|" + category;
		Long last = flagCooldown.get(key);
		if (last != null && now - last < TradeShopConfig.get().aiFlagCooldownSeconds * 1000L) {
			return;
		}
		flagCooldown.put(key, now);

		MinecraftServer server = target.level().getServer();
		ModerationState.get(server).addSystemReport(target, category + " — " + detail);

		Component alert = Component.literal(
				"[AntiCheat] " + target.getGameProfile().name() + " flagged for " + category + " (" + detail + ")")
				.withStyle(ChatFormatting.RED);
		for (ServerPlayer admin : server.getPlayerList().getPlayers()) {
			if (TradeShop.isOp(admin)) {
				admin.sendSystemMessage(alert);
			}
		}
	}

	/** Cobwebs suspend a player mid-air with near-zero vertical movement, which would otherwise look like flight. */
	private static boolean inCobweb(ServerPlayer player) {
		BlockPos feet = player.blockPosition();
		return player.level().getBlockState(feet).getBlock() == Blocks.COBWEB
				|| player.level().getBlockState(feet.above()).getBlock() == Blocks.COBWEB;
	}

	private static boolean enabled() {
		return TradeShopConfig.get().aiCheckerEnabled;
	}

	/** Per-player movement tracking for the speed/fly detectors. */
	private static final class Track {
		Vec3 pos;
		net.minecraft.world.level.Level level;
		int speedTicks;
		int hoverTicks;
	}
}
