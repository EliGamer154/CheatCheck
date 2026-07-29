package com.tradeshop.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.tradeshop.gui.CheatConfigMenu;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/** {@code /cheatconfig} — op-only. Opens the in-game settings menu for the anti-cheat and moderation tunables. */
public final class CheatConfigCommand {
	private CheatConfigCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("cheatconfig")
				.requires(com.tradeshop.TradeShop::canModerate)
				.executes(context -> {
					CheatConfigMenu.open(context.getSource().getPlayerOrException());
					return Command.SINGLE_SUCCESS;
				}));
	}
}
