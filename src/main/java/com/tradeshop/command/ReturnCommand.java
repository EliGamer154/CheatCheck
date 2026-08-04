package com.tradeshop.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.tradeshop.moderation.SafeModeManager;
import com.tradeshop.moderation.WatchTools;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.Set;

/**
 * {@code /return} — op-only. Ends a watch: sends you back to where you were (and your prior gamemode) before
 * you started spectating, and clears safemode + the watch leash so you aren't pulled back to the target.
 */
public final class ReturnCommand {
	private ReturnCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("return")
				.requires(source -> com.tradeshop.moderation.AdminPerms.atLeast(source, 1))
				.executes(context -> back(context.getSource())));
	}

	private static int back(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer admin = source.getPlayerOrException();
		if (!com.tradeshop.moderation.ModerationService.endWatch(admin)) {
			source.sendFailure(Component.literal("No saved spot to return to — start a check with /cheatcheck first."));
			return 0;
		}
		admin.sendSystemMessage(Component.literal("Returned to where you started. Safemode off.")
				.withStyle(ChatFormatting.AQUA));
		return Command.SINGLE_SUCCESS;
	}
}
