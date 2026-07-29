package com.tradeshop.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.tradeshop.moderation.ModerationState;
import com.tradeshop.moderation.TempBan;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.Optional;
import java.util.stream.Collectors;

/**
 * {@code /pardon <name>} (and its alias {@code /cheatcheckerunban <name>}) — op-only. Lifts an active
 * temp-ban early, matching the banned player by name.
 */
public final class PardonCommand {
	private PardonCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		// Both command names do exactly the same thing.
		for (String name : new String[]{"pardon", "cheatcheckerunban"}) {
			dispatcher.register(Commands.literal(name)
					.requires(com.tradeshop.TradeShop::canModerate)
					.then(Commands.argument("name", StringArgumentType.word())
							.suggests(PardonCommand::suggestBannedNames)
							.executes(context -> pardon(context.getSource(),
									StringArgumentType.getString(context, "name")))));
		}
	}

	private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestBannedNames(
			com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
			com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
		ModerationState state = ModerationState.get(context.getSource().getServer());
		return SharedSuggestionProvider.suggest(
				state.activeBans().stream().map(b -> b.targetName).collect(Collectors.toList()), builder);
	}

	private static int pardon(CommandSourceStack source, String name) {
		ModerationState state = ModerationState.get(source.getServer());
		Optional<TempBan> match = state.activeBans().stream()
				.filter(b -> b.targetName.equalsIgnoreCase(name)).findFirst();
		if (match.isEmpty()) {
			source.sendFailure(Component.literal("No active ban found for \"" + name + "\"."));
			return 0;
		}
		state.pardon(match.get().targetId);
		source.sendSuccess(() -> Component.literal("Pardoned " + match.get().targetName + ".")
				.withStyle(ChatFormatting.GREEN), true);
		return Command.SINGLE_SUCCESS;
	}
}
