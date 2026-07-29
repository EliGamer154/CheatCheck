package com.tradeshop.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.tradeshop.gui.OffenseConfigMenu;
import com.tradeshop.moderation.DurationParser;
import com.tradeshop.moderation.ModerationService;
import com.tradeshop.moderation.ModerationState;
import com.tradeshop.moderation.Violation;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.stream.Collectors;

/**
 * {@code /offend} — op-only. Applies a preset-ban violation to a player, or manages the violation list:
 * <ul>
 *   <li>{@code /offend <player> <reason>} — bans the player for that violation's preset time.</li>
 *   <li>{@code /offend add <name> <time>} — adds a new violation with a preset ban time.</li>
 *   <li>{@code /offend remove <name>} — deletes a violation.</li>
 *   <li>{@code /offend menu} — opens the customizable menu to edit preset ban times.</li>
 * </ul>
 */
public final class OffendCommand {
	private OffendCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		SuggestionProvider<CommandSourceStack> reasons = (context, builder) -> {
			ModerationState state = ModerationState.get(context.getSource().getServer());
			return SharedSuggestionProvider.suggest(
					state.violations().stream().map(v -> v.name).collect(Collectors.toList()), builder);
		};

		dispatcher.register(Commands.literal("offend")
				.requires(com.tradeshop.TradeShop::canModerate)
				.then(Commands.literal("menu").executes(context -> {
					OffenseConfigMenu.open(context.getSource().getPlayerOrException(), 0);
					return Command.SINGLE_SUCCESS;
				}))
				.then(Commands.literal("add")
						.then(Commands.argument("spec", StringArgumentType.greedyString())
								.executes(context -> addViolation(context.getSource(),
										StringArgumentType.getString(context, "spec")))))
				.then(Commands.literal("remove")
						.then(Commands.argument("name", StringArgumentType.greedyString())
								.suggests(reasons)
								.executes(context -> removeViolation(context.getSource(),
										StringArgumentType.getString(context, "name")))))
				.then(Commands.argument("player", EntityArgument.player())
						.then(Commands.argument("reason", StringArgumentType.greedyString())
								.suggests(reasons)
								.executes(context -> offend(context.getSource(),
										EntityArgument.getPlayer(context, "player"),
										StringArgumentType.getString(context, "reason"))))));
	}

	private static int offend(CommandSourceStack source, ServerPlayer target, String reason) throws CommandSyntaxException {
		ModerationState state = ModerationState.get(source.getServer());
		Optional<Violation> violation = state.findViolation(reason);
		if (violation.isEmpty()) {
			String valid = state.violations().stream().map(v -> v.name).collect(Collectors.joining(", "));
			source.sendFailure(Component.literal("No violation named \"" + reason.trim()
					+ "\". Add it with /offend add <name> <time>. Existing: " + valid));
			return 0;
		}

		Violation v = violation.get();
		String bannedBy = source.getEntity() instanceof ServerPlayer admin ? admin.getGameProfile().name() : "Console";
		String targetName = target.getGameProfile().name();
		ModerationService.ban(target, v.banSeconds, v.name, bannedBy);

		String duration = v.banSeconds == DurationParser.PERMANENT ? "permanently" : "for " + DurationParser.format(v.banSeconds);
		source.sendSuccess(() -> Component.literal(
				"Offended " + targetName + " for " + v.name + " — banned " + duration + ".").withStyle(ChatFormatting.GREEN), true);
		return Command.SINGLE_SUCCESS;
	}

	private static int addViolation(CommandSourceStack source, String spec) {
		// spec is "<name> <time>"; the time is the final whitespace-delimited token, the name is the rest.
		String trimmed = spec.trim();
		int lastSpace = trimmed.lastIndexOf(' ');
		if (lastSpace <= 0) {
			source.sendFailure(Component.literal("Usage: /offend add <name> <time>  (e.g. /offend add fly 3d)"));
			return 0;
		}
		String name = trimmed.substring(0, lastSpace).trim();
		String timeText = trimmed.substring(lastSpace + 1).trim();

		Optional<Long> seconds = DurationParser.parseSeconds(timeText);
		if (seconds.isEmpty()) {
			source.sendFailure(Component.literal(
					"Invalid time \"" + timeText + "\". Use e.g. 30m, 2h, 7d, 1d12h, or perm."));
			return 0;
		}

		ModerationState state = ModerationState.get(source.getServer());
		if (!state.addViolation(name, seconds.get())) {
			source.sendFailure(Component.literal("A violation named \"" + name + "\" already exists."));
			return 0;
		}
		String duration = seconds.get() == DurationParser.PERMANENT ? "permanent" : DurationParser.format(seconds.get());
		source.sendSuccess(() -> Component.literal(
				"Added violation \"" + name + "\" with a " + duration + " preset ban.").withStyle(ChatFormatting.GREEN), true);
		return Command.SINGLE_SUCCESS;
	}

	private static int removeViolation(CommandSourceStack source, String name) {
		ModerationState state = ModerationState.get(source.getServer());
		if (!state.removeViolation(name)) {
			source.sendFailure(Component.literal("No violation named \"" + name.trim() + "\"."));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Removed violation \"" + name.trim() + "\".")
				.withStyle(ChatFormatting.GREEN), true);
		return Command.SINGLE_SUCCESS;
	}
}
