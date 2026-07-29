package com.tradeshop.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.tradeshop.moderation.WatchTools;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /freeze <player>} — op-only. Toggles a freeze on a suspect: they're pinned in place (can't run off
 * or combat-log) until you run it again. Enforced each tick in {@code ModerationEvents}.
 */
public final class FreezeCommand {
	private FreezeCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("freeze")
				.requires(com.tradeshop.TradeShop::canModerate)
				.then(Commands.argument("player", EntityArgument.player())
						.executes(context -> toggle(context.getSource(), EntityArgument.getPlayer(context, "player")))));
	}

	private static int toggle(CommandSourceStack source, ServerPlayer target) throws CommandSyntaxException {
		WatchTools tools = WatchTools.get();
		String name = target.getGameProfile().name();
		if (tools.isFrozen(target.getUUID())) {
			tools.unfreeze(target.getUUID());
			source.sendSuccess(() -> Component.literal("Unfroze " + name + ".").withStyle(ChatFormatting.GREEN), true);
			target.sendSystemMessage(Component.literal("You have been unfrozen.").withStyle(ChatFormatting.GREEN));
		} else {
			tools.freeze(target.getUUID(), new WatchTools.FreezePoint((ServerLevel) target.level(),
					target.getX(), target.getY(), target.getZ(), target.getYRot(), target.getXRot()));
			source.sendSuccess(() -> Component.literal("Froze " + name + ".").withStyle(ChatFormatting.GREEN), true);
			target.sendSystemMessage(Component.literal("You have been frozen by an admin. Do not log out.")
					.withStyle(ChatFormatting.RED));
		}
		return Command.SINGLE_SUCCESS;
	}
}
