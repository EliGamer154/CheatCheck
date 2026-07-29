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
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /glow <player>} — op-only. Toggles a glowing outline on a suspect that renders through walls, so
 * you can track their movement (useful for spotting speed/movement cheats). Visible to everyone.
 */
public final class GlowCommand {
	private GlowCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("glow")
				.requires(com.tradeshop.TradeShop::canModerate)
				.then(Commands.argument("player", EntityArgument.player())
						.executes(context -> toggle(context.getSource(), EntityArgument.getPlayer(context, "player")))));
	}

	private static int toggle(CommandSourceStack source, ServerPlayer target) throws CommandSyntaxException {
		WatchTools tools = WatchTools.get();
		String name = target.getGameProfile().name();
		boolean nowGlowing = !tools.isGlowing(target.getUUID());
		tools.setGlowing(target.getUUID(), nowGlowing);
		target.setGlowingTag(nowGlowing);
		source.sendSuccess(() -> Component.literal((nowGlowing ? "Now glowing: " : "Stopped glowing: ") + name + ".")
				.withStyle(ChatFormatting.GREEN), true);
		return Command.SINGLE_SUCCESS;
	}
}
