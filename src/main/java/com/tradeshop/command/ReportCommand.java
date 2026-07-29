package com.tradeshop.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.tradeshop.config.TradeShopConfig;
import com.tradeshop.moderation.ModerationState;
import com.tradeshop.moderation.Violation;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * {@code /report <player> <reason>} — lets any player flag a suspected cheater. The reason must be one of
 * the server's configured {@link Violation} names. Targets must be online, and each reporter is limited to
 * one report per {@link TradeShopConfig#reportCooldownSeconds}.
 */
public final class ReportCommand {
	private static final Map<UUID, Long> LAST_REPORT = new HashMap<>();

	private ReportCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		SuggestionProvider<CommandSourceStack> reasons = (context, builder) -> {
			ModerationState state = ModerationState.get(context.getSource().getServer());
			return SharedSuggestionProvider.suggest(
					state.violations().stream().map(v -> v.name).collect(Collectors.toList()), builder);
		};

		dispatcher.register(Commands.literal("report")
				.then(Commands.argument("player", EntityArgument.player())
						.then(Commands.argument("reason", StringArgumentType.greedyString())
								.suggests(reasons)
								.executes(context -> report(
										context.getSource(),
										EntityArgument.getPlayer(context, "player"),
										StringArgumentType.getString(context, "reason"))))));
	}

	private static int report(CommandSourceStack source, ServerPlayer target, String reason) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer reporter = source.getPlayerOrException();
		ModerationState state = ModerationState.get(source.getServer());

		if (state.findViolation(reason).isEmpty()) {
			String valid = state.violations().stream().map(v -> v.name).collect(Collectors.joining(", "));
			reporter.sendSystemMessage(Component.literal("Unknown report reason. Valid reasons: " + valid)
					.withStyle(ChatFormatting.RED));
			return 0;
		}

		int cooldownSeconds = TradeShopConfig.get().reportCooldownSeconds;
		long now = System.currentTimeMillis();
		Long last = LAST_REPORT.get(reporter.getUUID());
		if (last != null) {
			long remaining = cooldownSeconds * 1000L - (now - last);
			if (remaining > 0) {
				reporter.sendSystemMessage(Component.literal(
						"You're reporting too fast. Wait " + ((remaining + 999) / 1000) + "s.")
						.withStyle(ChatFormatting.RED));
				return 0;
			}
		}

		state.addReport(reporter, target, reason.trim());
		LAST_REPORT.put(reporter.getUUID(), now);
		reporter.sendSystemMessage(Component.literal(
				"Reported " + target.getGameProfile().name() + " for " + reason.trim() + ". Thanks — admins will review it.")
				.withStyle(ChatFormatting.GREEN));
		return Command.SINGLE_SUCCESS;
	}
}
