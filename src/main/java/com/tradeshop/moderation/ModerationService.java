package com.tradeshop.moderation;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.util.Set;

/** Shared moderation actions used by both the commands and the chest-GUI menus. */
public final class ModerationService {
	private ModerationService() {
	}

	/**
	 * Records a temp-ban for the target and, if they're online, kicks them immediately with a screen that
	 * shows the remaining time. {@code banSeconds} of {@link DurationParser#PERMANENT} bans permanently.
	 */
	public static void ban(ServerPlayer target, long banSeconds, String reason, String bannedBy) {
		long expiry = banSeconds == DurationParser.PERMANENT
				? DurationParser.PERMANENT
				: System.currentTimeMillis() + banSeconds * 1000L;
		TempBan ban = new TempBan(target.getUUID(), target.getGameProfile().name(), reason, expiry, bannedBy);
		ModerationState.get(target.level().getServer()).addBan(ban);
		target.connection.disconnect(banScreen(ban));
	}

	/** The disconnect/login-deny screen text for a ban, including its remaining time. */
	public static Component banScreen(TempBan ban) {
		Component durationLine = ban.isPermanent()
				? Component.literal("This ban is permanent.").withStyle(ChatFormatting.RED)
				: Component.literal("Time remaining: " + DurationParser.format(ban.remainingSeconds(System.currentTimeMillis())))
						.withStyle(ChatFormatting.YELLOW);
		return Component.literal("You are banned from this server.\n").withStyle(ChatFormatting.RED)
				.append(Component.literal("Reason: " + ban.reason + "\n").withStyle(ChatFormatting.GRAY))
				.append(durationLine);
	}

	/**
	 * Puts the admin into a watch session on the target: safemode on, target set as the leash anchor, admin
	 * switched to spectator and teleported to the target. Works cross-dimension.
	 */
	public static void startWatch(ServerPlayer admin, ServerPlayer target) {
		SafeModeManager safe = SafeModeManager.get();
		safe.setSafeMode(admin.getUUID(), true);
		safe.setWatchTarget(admin.getUUID(), target.getUUID());

		admin.setGameMode(GameType.SPECTATOR);
		ServerLevel targetLevel = (ServerLevel) target.level();
		admin.teleportTo(targetLevel, target.getX(), target.getY(), target.getZ(),
				Set.of(), target.getYRot(), target.getXRot(), true);

		admin.sendSystemMessage(Component.literal("Now watching " + target.getGameProfile().name()
				+ ". Safemode is ON — you're leashed to them. Use /safemode to stop.")
				.withStyle(ChatFormatting.AQUA));
	}
}
