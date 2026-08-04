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
 * {@code /adminreturn} — ends a check: sends you back to where you were (and your prior gamemode) before you
 * started spectating, and clears safemode + the watch leash so you aren't pulled back to the target. Available
 * to everyone (no-op if you're not mid-check); named to avoid vanilla's {@code /return} functions command.
 */
public final class ReturnCommand {
	private ReturnCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		// Named /adminreturn to avoid vanilla's /return (a functions command). Ungated so it's always in the
		// command tree; it only does something mid-check, otherwise a harmless message.
		dispatcher.register(Commands.literal("adminreturn")
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
