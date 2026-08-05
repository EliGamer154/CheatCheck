package com.tradeshop.moderation;

import com.tradeshop.TradeShop;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * Central permission checks for the moderation commands. A "real op" (server operator / console) bypasses
 * every restriction; otherwise access is granted by the player's custom {@link AdminLevel}.
 */
public final class AdminPerms {
	private AdminPerms() {
	}

	public static boolean isRealOp(CommandSourceStack source) {
		return TradeShop.canModerate(source);
	}

	public static boolean isRealOp(ServerPlayer player) {
		// Use the permission level (not the ops.json list) so it's consistent with the command-source check
		// and works for singleplayer hosts / anyone op'd by permission level.
		return TradeShop.canModerate(player.createCommandSourceStack());
	}

	public static Optional<AdminLevel> level(ServerPlayer player) {
		return ModerationState.get(player.level().getServer()).adminLevel(player.getUUID());
	}

	/** True for a real op/console, or a custom admin whose rank is at least {@code rank}. */
	public static boolean atLeast(CommandSourceStack source, int rank) {
		if (isRealOp(source)) {
			return true;
		}
		if (source.getEntity() instanceof ServerPlayer player) {
			return ModerationState.get(source.getServer()).adminLevel(player.getUUID())
					.map(lvl -> lvl.rank >= rank).orElse(false);
		}
		return false;
	}

	/** True if the player may use a level-granted action right now: real ops always; custom admins only while checking. */
	public static boolean canActNow(ServerPlayer player) {
		return isRealOp(player) || AdminCheckSession.get().isChecking(player.getUUID());
	}

	/** True for a real op, or a custom admin allowed to inspect inventories (level 3+). */
	public static boolean canInspect(CommandSourceStack source) {
		if (isRealOp(source)) {
			return true;
		}
		if (source.getEntity() instanceof ServerPlayer player) {
			return level(player).map(lvl -> lvl.canInspectInventories).orElse(false);
		}
		return false;
	}
}
