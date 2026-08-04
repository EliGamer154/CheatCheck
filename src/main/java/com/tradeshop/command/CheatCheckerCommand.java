package com.tradeshop.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.tradeshop.moderation.AdminLevel;
import com.tradeshop.moderation.AdminPerms;
import com.tradeshop.moderation.ModerationState;
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
 * {@code /cheatchecker admin add/remove/list} — REAL op only. Manages the custom checker-admin ranks that
 * grant non-op players limited moderation powers. Levels: {@code 1}, {@code 2}, {@code 3}, {@code 3+}.
 */
public final class CheatCheckerCommand {
	private CheatCheckerCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("cheatchecker")
				.requires(AdminPerms::isRealOp)
				.then(Commands.literal("admin")
						.then(Commands.literal("add")
								.then(Commands.argument("player", EntityArgument.player())
										.then(Commands.argument("level", StringArgumentType.word())
												.suggests((ctx, b) -> SharedSuggestionProvider.suggest(new String[]{"1", "2", "3", "3+", "5"}, b))
												.executes(context -> add(context.getSource(),
														EntityArgument.getPlayer(context, "player"),
														StringArgumentType.getString(context, "level"))))))
						.then(Commands.literal("remove")
								.then(Commands.argument("player", EntityArgument.player())
										.executes(context -> remove(context.getSource(),
												EntityArgument.getPlayer(context, "player")))))
						.then(Commands.literal("list").executes(context -> list(context.getSource()))))
				.then(Commands.literal("activity").executes(context -> activity(context.getSource()))));
	}

	private static int activity(CommandSourceStack source) {
		var lines = com.tradeshop.moderation.AdminLog.get().recent(20);
		if (lines.isEmpty()) {
			source.sendSuccess(() -> Component.literal("No recent admin activity.").withStyle(ChatFormatting.GRAY), false);
			return Command.SINGLE_SUCCESS;
		}
		source.sendSuccess(() -> Component.literal("— Recent admin activity —").withStyle(ChatFormatting.GOLD), false);
		for (String line : lines) {
			source.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.GRAY), false);
		}
		return Command.SINGLE_SUCCESS;
	}

	private static int add(CommandSourceStack source, ServerPlayer target, String levelText) {
		Optional<AdminLevel> level = AdminLevel.parse(levelText);
		if (level.isEmpty()) {
			source.sendFailure(Component.literal("Invalid level \"" + levelText + "\". Use 1, 2, 3, 3+, or 5."));
			return 0;
		}
		AdminLevel lvl = level.get();
		ModerationState.get(source.getServer()).setAdmin(target.getUUID(), target.getGameProfile().name(), lvl);
		// Re-send their command tree so their new level's commands (/cheatcheck, /return, ...) work immediately.
		source.getServer().getCommands().sendCommands(target);

		String name = target.getGameProfile().name();
		source.sendSuccess(() -> Component.literal("Made " + name + " a level " + lvl.label + " checker admin.")
				.withStyle(ChatFormatting.GREEN), true);
		target.sendSystemMessage(Component.literal("You are now a level " + lvl.label + " checker admin.")
				.withStyle(ChatFormatting.AQUA));
		return Command.SINGLE_SUCCESS;
	}

	private static int remove(CommandSourceStack source, ServerPlayer target) {
		String name = target.getGameProfile().name();
		if (!ModerationState.get(source.getServer()).removeAdmin(target.getUUID())) {
			source.sendFailure(Component.literal(name + " isn't a checker admin."));
			return 0;
		}
		source.getServer().getCommands().sendCommands(target); // refresh their tree so the commands disappear
		source.sendSuccess(() -> Component.literal("Removed " + name + " as a checker admin.")
				.withStyle(ChatFormatting.GREEN), true);
		target.sendSystemMessage(Component.literal("Your checker-admin rank was removed.").withStyle(ChatFormatting.YELLOW));
		return Command.SINGLE_SUCCESS;
	}

	private static int list(CommandSourceStack source) {
		var admins = ModerationState.get(source.getServer()).admins();
		if (admins.isEmpty()) {
			source.sendSuccess(() -> Component.literal("No checker admins.").withStyle(ChatFormatting.GRAY), false);
			return Command.SINGLE_SUCCESS;
		}
		String listed = admins.stream().map(a -> a.name() + " (lvl " + a.level() + ")").collect(Collectors.joining(", "));
		source.sendSuccess(() -> Component.literal("Checker admins: " + listed).withStyle(ChatFormatting.AQUA), false);
		return Command.SINGLE_SUCCESS;
	}
}
