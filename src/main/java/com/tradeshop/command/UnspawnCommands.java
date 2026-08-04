package com.tradeshop.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.tradeshop.moderation.AdminPerms;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /unspawnores}, {@code /unspawnstash}, {@code /unspawnbase} — undo the caller's most recent spawn of
 * that kind, restoring the blocks it overwrote. Same access as the spawn commands (level 2+, only while
 * checking; real ops anytime).
 */
public final class UnspawnCommands {
	private UnspawnCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		register(dispatcher, "unspawnores", SpawnHistory.Kind.ORES, "ore vein");
		register(dispatcher, "unspawnstash", SpawnHistory.Kind.STASH, "stash");
		register(dispatcher, "unspawnbase", SpawnHistory.Kind.BASE, "base");
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher, String name,
			SpawnHistory.Kind kind, String label) {
		dispatcher.register(Commands.literal(name)
				.requires(source -> AdminPerms.atLeast(source, 2))
				.executes(context -> undo(context.getSource(), kind, label)));
	}

	private static int undo(CommandSourceStack source, SpawnHistory.Kind kind, String label) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		if (!AdminPerms.canActNow(player)) {
			source.sendFailure(Component.literal("You can only do that while checking someone."));
			return 0;
		}
		int restored = SpawnHistory.get().undo(player.getUUID(), kind);
		if (restored == 0) {
			source.sendFailure(Component.literal("Nothing to undo — you haven't spawned a " + label + " recently."));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Removed your last " + label + " (" + restored + " blocks restored).")
				.withStyle(ChatFormatting.GREEN), false);
		return Command.SINGLE_SUCCESS;
	}
}
