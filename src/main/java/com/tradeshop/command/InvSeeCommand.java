package com.tradeshop.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.tradeshop.gui.InventoryViewMenu;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /invsee <player>} — op-only. Opens a read-only snapshot of a player's full inventory (main, hotbar,
 * armor, offhand) to check for duped or illegal items.
 */
public final class InvSeeCommand {
	private InvSeeCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("invsee")
				.requires(com.tradeshop.TradeShop::canModerate)
				.then(Commands.argument("player", EntityArgument.player())
						.executes(context -> view(context.getSource(), EntityArgument.getPlayer(context, "player")))));
	}

	private static int view(CommandSourceStack source, ServerPlayer target) throws CommandSyntaxException {
		ServerPlayer viewer = source.getPlayerOrException();
		Inventory inventory = target.getInventory();
		List<ItemStack> snapshot = new ArrayList<>();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			snapshot.add(inventory.getItem(i).copy());
		}
		InventoryViewMenu.open(viewer, target.getGameProfile().name() + "'s inventory", snapshot, 0);
		return Command.SINGLE_SUCCESS;
	}
}
