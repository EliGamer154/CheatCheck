package com.tradeshop.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.tradeshop.moderation.DurationParser;
import com.tradeshop.moderation.ModerationService;
import com.tradeshop.moderation.ModerationState;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.Set;

/**
 * {@code /jail <player> [time]} and {@code /unjail <player>} — op-only. Jail confines a player to the
 * {@code /setjail} point (teleported there, held within {@link com.tradeshop.config.TradeShopConfig#jailRadius}
 * blocks, and can't place/break blocks). With a time (e.g. {@code 30m}, {@code 2h}) the sentence only counts
 * down while the player is online, and they're released automatically when it runs out; with no time it lasts
 * until {@code /unjail}. Jail status and remaining time persist across restarts.
 */
public final class JailCommand {
	private JailCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("jail")
				.requires(com.tradeshop.TradeShop::canModerate)
				.then(Commands.argument("player", EntityArgument.player())
						.executes(context -> jail(context.getSource(), EntityArgument.getPlayer(context, "player"), DurationParser.PERMANENT))
						.then(Commands.argument("time", StringArgumentType.word())
								.executes(context -> jailTimed(context.getSource(),
										EntityArgument.getPlayer(context, "player"),
										StringArgumentType.getString(context, "time"))))));

		dispatcher.register(Commands.literal("unjail")
				.requires(com.tradeshop.TradeShop::canModerate)
				.then(Commands.argument("player", EntityArgument.player())
						.executes(context -> unjail(context.getSource(), EntityArgument.getPlayer(context, "player")))));
	}

	private static int jailTimed(CommandSourceStack source, ServerPlayer target, String time) throws CommandSyntaxException {
		Optional<Long> seconds = DurationParser.parseSeconds(time);
		if (seconds.isEmpty()) {
			source.sendFailure(Component.literal("Invalid time \"" + time + "\". Use e.g. 10m, 2h, 1d, or perm."));
			return 0;
		}
		return jail(source, target, seconds.get());
	}

	private static int jail(CommandSourceStack source, ServerPlayer target, long seconds) throws CommandSyntaxException {
		ModerationState state = ModerationState.get(source.getServer());
		Optional<ModerationState.JailPoint> point = state.jailPoint();
		if (point.isEmpty()) {
			source.sendFailure(Component.literal("No jail set. Stand where you want it and run /setjail first."));
			return 0;
		}

		state.jail(target.getUUID(), target.getGameProfile().name(), seconds);
		ModerationState.JailPoint p = point.get();
		ServerLevel level = ModerationService.resolveLevel(source.getServer(), p.dimension());
		target.teleportTo(level, p.x(), p.y(), p.z(), Set.of(), p.yaw(), p.pitch(), true);

		String name = target.getGameProfile().name();
		String duration = seconds == DurationParser.PERMANENT
				? "until an admin unjails them"
				: "for " + DurationParser.format(seconds) + " of online time";
		source.sendSuccess(() -> Component.literal("Jailed " + name + " " + duration + ".").withStyle(ChatFormatting.GREEN), true);
		target.sendSystemMessage(Component.literal("You have been jailed by an admin " + duration + ".").withStyle(ChatFormatting.RED));
		return Command.SINGLE_SUCCESS;
	}

	private static int unjail(CommandSourceStack source, ServerPlayer target) throws CommandSyntaxException {
		ModerationState state = ModerationState.get(source.getServer());
		String name = target.getGameProfile().name();
		if (!state.unjail(target.getUUID())) {
			source.sendFailure(Component.literal(name + " isn't jailed."));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Released " + name + " from jail.").withStyle(ChatFormatting.GREEN), true);
		target.sendSystemMessage(Component.literal("You have been released from jail.").withStyle(ChatFormatting.GREEN));
		return Command.SINGLE_SUCCESS;
	}
}
