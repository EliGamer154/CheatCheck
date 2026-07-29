package com.tradeshop.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.tradeshop.moderation.DurationParser;
import com.tradeshop.moderation.ModerationService;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * {@code /tempban <time> <player> [reason]} — op-only. Temp-bans an online player for a parsed duration
 * ({@code 7d}, {@code 2h30m}, {@code perm}, …) and kicks them with a screen showing the remaining time.
 */
public final class TempBanCommand {
	private TempBanCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("tempban")
				.requires(com.tradeshop.TradeShop::canModerate)
				.then(Commands.argument("time", StringArgumentType.word())
						.then(Commands.argument("player", EntityArgument.player())
								.executes(context -> ban(context.getSource(),
										StringArgumentType.getString(context, "time"),
										EntityArgument.getPlayer(context, "player"), "Cheating"))
								.then(Commands.argument("reason", StringArgumentType.greedyString())
										.executes(context -> ban(context.getSource(),
												StringArgumentType.getString(context, "time"),
												EntityArgument.getPlayer(context, "player"),
												StringArgumentType.getString(context, "reason")))))));
	}

	private static int ban(CommandSourceStack source, String time, ServerPlayer target, String reason) throws CommandSyntaxException {
		Optional<Long> seconds = DurationParser.parseSeconds(time);
		if (seconds.isEmpty()) {
			source.sendFailure(Component.literal(
					"Invalid time \"" + time + "\". Use e.g. 30m, 2h, 7d, 1d12h, or perm."));
			return 0;
		}

		String bannedBy = source.getEntity() instanceof ServerPlayer admin ? admin.getGameProfile().name() : "Console";
		String targetName = target.getGameProfile().name();
		ModerationService.ban(target, seconds.get(), reason, bannedBy);

		String duration = seconds.get() == DurationParser.PERMANENT ? "permanently" : "for " + DurationParser.format(seconds.get());
		source.sendSuccess(() -> Component.literal(
				"Banned " + targetName + " " + duration + " (" + reason + ").").withStyle(ChatFormatting.GREEN), true);
		return Command.SINGLE_SUCCESS;
	}
}
