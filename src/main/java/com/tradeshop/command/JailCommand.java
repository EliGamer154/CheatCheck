package com.tradeshop.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
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
 * {@code /jail <player>} and {@code /unjail <player>} — op-only. Jail confines a player to the {@code /setjail}
 * point (they're teleported there and held within {@link com.tradeshop.config.TradeShopConfig#jailRadius}
 * blocks, and can't place/break blocks); unjail releases them. Jail status persists across restarts.
 */
public final class JailCommand {
	private JailCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("jail")
				.requires(com.tradeshop.TradeShop::canModerate)
				.then(Commands.argument("player", EntityArgument.player())
						.executes(context -> jail(context.getSource(), EntityArgument.getPlayer(context, "player")))));

		dispatcher.register(Commands.literal("unjail")
				.requires(com.tradeshop.TradeShop::canModerate)
				.then(Commands.argument("player", EntityArgument.player())
						.executes(context -> unjail(context.getSource(), EntityArgument.getPlayer(context, "player")))));
	}

	private static int jail(CommandSourceStack source, ServerPlayer target) throws CommandSyntaxException {
		ModerationState state = ModerationState.get(source.getServer());
		Optional<ModerationState.JailPoint> point = state.jailPoint();
		if (point.isEmpty()) {
			source.sendFailure(Component.literal("No jail set. Stand where you want it and run /setjail first."));
			return 0;
		}

		state.jail(target.getUUID());
		ModerationState.JailPoint p = point.get();
		ServerLevel level = ModerationService.resolveLevel(source.getServer(), p.dimension());
		target.teleportTo(level, p.x(), p.y(), p.z(), Set.of(), p.yaw(), p.pitch(), true);

		String name = target.getGameProfile().name();
		source.sendSuccess(() -> Component.literal("Jailed " + name + ".").withStyle(ChatFormatting.GREEN), true);
		target.sendSystemMessage(Component.literal("You have been jailed by an admin.").withStyle(ChatFormatting.RED));
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
