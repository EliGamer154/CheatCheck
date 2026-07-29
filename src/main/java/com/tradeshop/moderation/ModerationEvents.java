package com.tradeshop.moderation;

import com.tradeshop.config.TradeShopConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Runtime enforcement of safemode: the watch leash (pulls a watching admin back to their target), the
 * block place/break lockout, and cleanup of a player's safemode state when they disconnect.
 */
public final class ModerationEvents {
	private ModerationEvents() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ModerationEvents::enforceLeash);

		// Block breaking while in safemode.
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
			if (SafeModeManager.get().isSafeMode(player.getUUID())) {
				player.sendSystemMessage(Component.literal("Safemode is on — you can't break blocks.")
						.withStyle(ChatFormatting.RED));
				return false;
			}
			return true;
		});

		// Block placement while in safemode (only cancel actual block items, so menus/interactions still work).
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (SafeModeManager.get().isSafeMode(player.getUUID())
					&& player.getItemInHand(hand).getItem() instanceof BlockItem) {
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});

		// Forget a player's safemode/watch/tool state when they leave.
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			SafeModeManager.get().forget(handler.player.getUUID());
			WatchTools.get().forget(handler.player.getUUID());
		});
	}

	private static void enforceLeash(net.minecraft.server.MinecraftServer server) {
		SafeModeManager safe = SafeModeManager.get();
		WatchTools tools = WatchTools.get();
		double radius = TradeShopConfig.get().safeModeLeashRadius;
		double radiusSq = radius * radius;

		for (ServerPlayer online : server.getPlayerList().getPlayers()) {
			// Keep frozen suspects pinned to where they were frozen.
			tools.frozenPoint(online.getUUID()).ifPresent(point -> {
				if (online.level() != point.level()
						|| online.position().distanceToSqr(point.x(), point.y(), point.z()) > 0.02) {
					online.teleportTo(point.level(), point.x(), point.y(), point.z(),
							java.util.Set.of(), point.yRot(), point.xRot(), true);
				}
				online.setDeltaMovement(0, 0, 0);
			});
		}

		for (ServerPlayer admin : server.getPlayerList().getPlayers()) {
			if (!safe.isSafeMode(admin.getUUID())) {
				continue;
			}
			Optional<UUID> targetId = safe.watchTarget(admin.getUUID());
			if (targetId.isEmpty()) {
				continue;
			}
			ServerPlayer target = server.getPlayerList().getPlayer(targetId.get());
			if (target == null) {
				safe.clearWatchTarget(admin.getUUID());
				admin.sendSystemMessage(Component.literal("The player you were watching went offline. Leash released.")
						.withStyle(ChatFormatting.YELLOW));
				continue;
			}
			boolean differentDimension = admin.level() != target.level();
			if (differentDimension || admin.position().distanceToSqr(target.position()) > radiusSq) {
				admin.teleportTo((ServerLevel) target.level(), target.getX(), target.getY(), target.getZ(),
						Set.of(), target.getYRot(), target.getXRot(), true);
			}
		}
	}
}
