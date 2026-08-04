package com.tradeshop.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.tradeshop.moderation.AdminLog;
import com.tradeshop.moderation.AdminPerms;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /adminkick <player> <reason>} — level 3+. Kicks a player (only while actively checking someone),
 * once per day per target. Named to avoid colliding with vanilla {@code /kick} (which stays op-only).
 */
public final class KickCommand {
	private KickCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("adminkick")
				.requires(source -> AdminPerms.atLeast(source, 3))
				.then(Commands.argument("player", EntityArgument.player())
						.then(Commands.argument("reason", StringArgumentType.greedyString())
								.executes(context -> kick(context.getSource(),
										EntityArgument.getPlayer(context, "player"),
										StringArgumentType.getString(context, "reason"))))));
	}

	private static int kick(CommandSourceStack source, ServerPlayer target, String reason) throws CommandSyntaxException {
		ServerPlayer admin = source.getPlayerOrException();
		if (!AdminPerms.canActNow(admin)) {
			source.sendFailure(Component.literal("You can only /kick while checking someone."));
			return 0;
		}
		if (!AdminLog.get().allowDaily(admin.getUUID(), target.getUUID(), "kick")) {
			source.sendFailure(Component.literal("You've already kicked " + target.getGameProfile().name() + " today."));
			return 0;
		}
		AdminLog.get().markDaily(admin.getUUID(), target.getUUID(), "kick");

		String name = target.getGameProfile().name();
		target.connection.disconnect(Component.literal("You were kicked: " + reason).withStyle(ChatFormatting.RED));
		AdminLog.get().record(admin.getGameProfile().name() + " kicked " + name + ": " + reason);
		source.sendSuccess(() -> Component.literal("Kicked " + name + ".").withStyle(ChatFormatting.GREEN), false);
		return Command.SINGLE_SUCCESS;
	}
}
