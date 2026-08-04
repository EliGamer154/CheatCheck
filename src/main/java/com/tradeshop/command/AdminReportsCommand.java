package com.tradeshop.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.tradeshop.gui.CheatCheckMenu;
import com.tradeshop.moderation.AdminPerms;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/** {@code /adminreports} — level 3+ / real op. Opens the cheat-check menu on the Admin-reports tab. */
public final class AdminReportsCommand {
	private AdminReportsCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("adminreports")
				.requires(source -> AdminPerms.atLeast(source, 3))
				.executes(context -> {
					CheatCheckMenu.open(context.getSource().getPlayerOrException(), 0, CheatCheckMenu.Section.ADMIN);
					return Command.SINGLE_SUCCESS;
				}));
	}
}
