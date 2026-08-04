package com.tradeshop.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.tradeshop.gui.CheatCheckMenu;
import com.tradeshop.moderation.ModerationService;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /cheatcheck} — op-only. With no argument, opens the menu of reported players. With a player
 * argument, immediately starts watching that online player whether or not they've been reported.
 */
public final class CheatCheckCommand {
	private CheatCheckCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("cheatcheck")
				.requires(source -> com.tradeshop.moderation.AdminPerms.atLeast(source, 1))
				.executes(context -> {
					CheatCheckMenu.open(context.getSource().getPlayerOrException(), 0);
					return Command.SINGLE_SUCCESS;
				})
				.then(Commands.argument("player", EntityArgument.player())
						.requires(com.tradeshop.moderation.AdminPerms::isRealOp) // watching anyone online is op-only
						.executes(context -> watch(context.getSource(),
								EntityArgument.getPlayer(context, "player")))));
	}

	private static int watch(CommandSourceStack source, ServerPlayer target) throws CommandSyntaxException {
		ServerPlayer admin = source.getPlayerOrException();
		if (admin.getUUID().equals(target.getUUID())) {
			source.sendFailure(Component.literal("You can't watch yourself."));
			return 0;
		}
		ModerationService.startWatch(admin, target);
		return Command.SINGLE_SUCCESS;
	}
}
