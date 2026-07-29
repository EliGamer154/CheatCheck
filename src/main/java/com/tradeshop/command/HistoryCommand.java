package com.tradeshop.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.tradeshop.moderation.DurationParser;
import com.tradeshop.moderation.ModerationState;
import com.tradeshop.moderation.TempBan;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * {@code /history <name>} — op-only. Prints a player's moderation record: how many times they've been
 * reported and for what, plus their current ban status. Works on offline players by name.
 */
public final class HistoryCommand {
	private HistoryCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		SuggestionProvider<CommandSourceStack> names = (context, builder) -> {
			ModerationState state = ModerationState.get(context.getSource().getServer());
			Set<String> known = new LinkedHashSet<>();
			for (ServerPlayer p : context.getSource().getServer().getPlayerList().getPlayers()) {
				known.add(p.getGameProfile().name());
			}
			state.reportedPlayers().forEach(s -> known.add(s.targetName));
			state.activeBans().forEach(b -> known.add(b.targetName));
			return SharedSuggestionProvider.suggest(known, builder);
		};

		dispatcher.register(Commands.literal("history")
				.requires(com.tradeshop.TradeShop::canModerate)
				.then(Commands.argument("name", StringArgumentType.word())
						.suggests(names)
						.executes(context -> history(context.getSource(), StringArgumentType.getString(context, "name")))));
	}

	private static int history(CommandSourceStack source, String name) {
		ModerationState state = ModerationState.get(source.getServer());

		Optional<ModerationState.ReportSummary> reports = state.reportedPlayers().stream()
				.filter(s -> s.targetName.equalsIgnoreCase(name)).findFirst();
		Optional<TempBan> ban = state.activeBans().stream()
				.filter(b -> b.targetName.equalsIgnoreCase(name)).findFirst();

		source.sendSuccess(() -> Component.literal("— History for " + name + " —").withStyle(ChatFormatting.GOLD), false);

		if (reports.isPresent()) {
			ModerationState.ReportSummary summary = reports.get();
			source.sendSuccess(() -> Component.literal(
					"Reports: " + summary.count + " (" + String.join(", ", summary.reasons) + ")")
					.withStyle(ChatFormatting.YELLOW), false);
		} else {
			source.sendSuccess(() -> Component.literal("Reports: none").withStyle(ChatFormatting.GRAY), false);
		}

		if (ban.isPresent()) {
			TempBan b = ban.get();
			String remaining = b.isPermanent() ? "permanent" : DurationParser.format(b.remainingSeconds(System.currentTimeMillis()));
			source.sendSuccess(() -> Component.literal(
					"Ban: ACTIVE — " + b.reason + " (" + remaining + " left, by " + b.bannedBy + ")")
					.withStyle(ChatFormatting.RED), false);
		} else {
			source.sendSuccess(() -> Component.literal("Ban: not banned").withStyle(ChatFormatting.GRAY), false);
		}
		return Command.SINGLE_SUCCESS;
	}
}
