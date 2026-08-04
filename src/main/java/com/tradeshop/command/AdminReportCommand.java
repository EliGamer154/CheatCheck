package com.tradeshop.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.tradeshop.moderation.AdminLog;
import com.tradeshop.moderation.AdminPerms;
import com.tradeshop.moderation.ModerationState;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /adminreport <player> <reason>} — level 2+. Files an admin report (shown in the /adminreports menu
 * to level-3 admins and real ops) and pings those higher admins in chat. Only usable while checking someone.
 */
public final class AdminReportCommand {
	private AdminReportCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("adminreport")
				.requires(source -> AdminPerms.atLeast(source, 2))
				.then(Commands.argument("player", EntityArgument.player())
						.then(Commands.argument("reason", StringArgumentType.greedyString())
								.executes(context -> report(context.getSource(),
										EntityArgument.getPlayer(context, "player"),
										StringArgumentType.getString(context, "reason"))))));
	}

	private static int report(CommandSourceStack source, ServerPlayer target, String reason) throws CommandSyntaxException {
		ServerPlayer admin = source.getPlayerOrException();
		if (!AdminPerms.canActNow(admin)) {
			source.sendFailure(Component.literal("You can only /adminreport while checking someone."));
			return 0;
		}

		ModerationState.get(source.getServer()).addAdminReport(admin, target, reason);
		AdminLog.get().record(admin.getGameProfile().name() + " admin-reported " + target.getGameProfile().name() + ": " + reason);

		// Ping online level-3 admins and real ops.
		Component ping = Component.literal("[AdminReport] " + target.getGameProfile().name() + " — " + reason
				+ " (by " + admin.getGameProfile().name() + ")").withStyle(ChatFormatting.LIGHT_PURPLE);
		for (ServerPlayer online : source.getServer().getPlayerList().getPlayers()) {
			boolean canReceive = AdminPerms.isRealOp(online)
					|| AdminPerms.level(online).map(l -> l.rank >= 3).orElse(false);
			if (canReceive) {
				online.sendSystemMessage(ping);
			}
		}

		source.sendSuccess(() -> Component.literal("Admin report filed on " + target.getGameProfile().name() + ".")
				.withStyle(ChatFormatting.GREEN), false);
		return Command.SINGLE_SUCCESS;
	}
}
