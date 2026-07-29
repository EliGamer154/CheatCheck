package com.tradeshop.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.tradeshop.moderation.FakePlayers;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /spawnplayer <1-5>} — op-only. Spawns a fake-player combat dummy for testing (levels: 1 AFK,
 * 2 wanders, 3 fists, 4 iron gear, 5 diamond gear). {@code /unspawnfakeplayers} removes them all.
 */
public final class SpawnPlayerCommand {
	private SpawnPlayerCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("spawnplayer")
				.requires(com.tradeshop.TradeShop::canModerate)
				.then(Commands.argument("level", IntegerArgumentType.integer(1, 5))
						.executes(context -> spawn(context.getSource(), IntegerArgumentType.getInteger(context, "level")))));

		dispatcher.register(Commands.literal("unspawnfakeplayers")
				.requires(com.tradeshop.TradeShop::canModerate)
				.executes(context -> unspawn(context.getSource())));
	}

	private static int spawn(CommandSourceStack source, int level) throws CommandSyntaxException {
		ServerPlayer admin = source.getPlayerOrException();
		if (!FakePlayers.get().spawn(admin, level)) {
			source.sendFailure(Component.literal("Couldn't spawn a fake player."));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Spawned a level " + level + " fake player.")
				.withStyle(ChatFormatting.GREEN), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int unspawn(CommandSourceStack source) {
		int removed = FakePlayers.get().unspawnAll(source.getServer());
		source.sendSuccess(() -> Component.literal("Removed " + removed + " fake player(s).")
				.withStyle(ChatFormatting.GREEN), false);
		return Command.SINGLE_SUCCESS;
	}
}
