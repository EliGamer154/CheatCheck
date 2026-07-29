package com.tradeshop.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.tradeshop.moderation.SafeModeManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /safemode} — op-only toggle for the watching admin's safemode. On: their tab name stays
 * un-grayed in spectator, they can't place/break blocks, and (if watching) they're leashed to the target.
 * Off: all of that is cleared, including the current watch. The admin's gamemode/position are left as-is.
 */
public final class SafeModeCommand {
	private SafeModeCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("safemode")
				.requires(com.tradeshop.TradeShop::canModerate)
				.executes(context -> {
					ServerPlayer player = context.getSource().getPlayerOrException();
					boolean now = SafeModeManager.get().toggle(player.getUUID());
					if (now) {
						player.sendSystemMessage(Component.literal(
								"Safemode ON — spectator tab-name stays normal, block edits blocked, watch leash active.")
								.withStyle(ChatFormatting.GREEN));
					} else {
						player.sendSystemMessage(Component.literal(
								"Safemode OFF — restrictions and watch leash cleared. You can /gamemode back yourself.")
								.withStyle(ChatFormatting.YELLOW));
					}
					return Command.SINGLE_SUCCESS;
				}));
	}
}
