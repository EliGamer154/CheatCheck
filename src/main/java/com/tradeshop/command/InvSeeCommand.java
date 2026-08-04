package com.tradeshop.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.tradeshop.gui.InventoryViewMenu;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /invsee <player>} — op-only. Opens a live view of a player's full inventory (main, hotbar, armor,
 * offhand). Left-click an item to take it from them.
 */
public final class InvSeeCommand {
	private InvSeeCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("invsee")
				.requires(com.tradeshop.moderation.AdminPerms::canInspect)
				.then(Commands.argument("player", EntityArgument.player())
						.executes(context -> view(context.getSource(), EntityArgument.getPlayer(context, "player")))));
	}

	private static int view(CommandSourceStack source, ServerPlayer target) throws CommandSyntaxException {
		ServerPlayer viewer = source.getPlayerOrException();
		if (!com.tradeshop.moderation.AdminPerms.canActNow(viewer)) {
			source.sendFailure(net.minecraft.network.chat.Component.literal("You can only /invsee while checking someone."));
			return 0;
		}
		boolean canTake = com.tradeshop.moderation.AdminPerms.isRealOp(viewer); // custom admins get read-only
		InventoryViewMenu.open(viewer, target, target.getInventory(),
				target.getGameProfile().name() + "'s inventory", 0, canTake);
		return Command.SINGLE_SUCCESS;
	}
}
