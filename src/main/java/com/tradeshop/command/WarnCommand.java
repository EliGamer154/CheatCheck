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
 * {@code /warn <player> <reason>} — level 2+. Warns a player (only while actively checking someone), once per
 * day per target.
 */
public final class WarnCommand {
	private WarnCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("warn")
				.requires(source -> AdminPerms.atLeast(source, 2))
				.then(Commands.argument("player", EntityArgument.player())
						.then(Commands.argument("reason", StringArgumentType.greedyString())
								.executes(context -> warn(context.getSource(),
										EntityArgument.getPlayer(context, "player"),
										StringArgumentType.getString(context, "reason"))))));
	}

	private static int warn(CommandSourceStack source, ServerPlayer target, String reason) throws CommandSyntaxException {
		ServerPlayer admin = source.getPlayerOrException();
		if (!AdminPerms.canActNow(admin)) {
			source.sendFailure(Component.literal("You can only /warn while checking someone."));
			return 0;
		}
		if (!AdminLog.get().allowDaily(admin.getUUID(), target.getUUID(), "warn")) {
			source.sendFailure(Component.literal("You've already warned " + target.getGameProfile().name() + " today."));
			return 0;
		}
		AdminLog.get().markDaily(admin.getUUID(), target.getUUID(), "warn");

		target.sendSystemMessage(Component.literal("⚠ You have been warned by " + admin.getGameProfile().name()
				+ ": " + reason).withStyle(ChatFormatting.RED));
		AdminLog.get().record(admin.getGameProfile().name() + " warned " + target.getGameProfile().name() + ": " + reason);
		source.sendSuccess(() -> Component.literal("Warned " + target.getGameProfile().name() + ".")
				.withStyle(ChatFormatting.GREEN), false);
		return Command.SINGLE_SUCCESS;
	}
}
