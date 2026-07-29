package com.tradeshop.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.tradeshop.moderation.WatchTools;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /radar} — op-only. Toggles a glow on every player so you can track their movement through walls.
 * The glow is kept applied to all players (including joiners) each tick by {@code ModerationEvents} while on.
 * Note: the glow is a shared entity flag, so everyone sees it while radar is active.
 */
public final class RadarCommand {
	private RadarCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("radar")
				.requires(com.tradeshop.TradeShop::canModerate)
				.executes(context -> toggle(context.getSource())));
	}

	private static int toggle(CommandSourceStack source) {
		boolean nowOn = WatchTools.get().toggleRadar();
		if (nowOn) {
			source.sendSuccess(() -> Component.literal("Radar ON — all players glow through walls.")
					.withStyle(ChatFormatting.GREEN), false);
		} else {
			// Clear the radar glow, but leave individually /glow'd players glowing.
			for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
				if (!WatchTools.get().isGlowing(player.getUUID())) {
					player.setGlowingTag(false);
				}
			}
			source.sendSuccess(() -> Component.literal("Radar OFF.").withStyle(ChatFormatting.YELLOW), false);
		}
		return Command.SINGLE_SUCCESS;
	}
}
