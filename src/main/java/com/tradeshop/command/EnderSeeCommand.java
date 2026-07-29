package com.tradeshop.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.tradeshop.gui.InventoryViewMenu;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** {@code /endersee <player>} — op-only. Opens a read-only snapshot of a player's ender chest. */
public final class EnderSeeCommand {
	private EnderSeeCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("endersee")
				.requires(com.tradeshop.TradeShop::canModerate)
				.then(Commands.argument("player", EntityArgument.player())
						.executes(context -> view(context.getSource(), EntityArgument.getPlayer(context, "player")))));
	}

	private static int view(CommandSourceStack source, ServerPlayer target) throws CommandSyntaxException {
		ServerPlayer viewer = source.getPlayerOrException();
		PlayerEnderChestContainer ender = target.getEnderChestInventory();
		List<ItemStack> snapshot = new ArrayList<>();
		for (int i = 0; i < ender.getContainerSize(); i++) {
			snapshot.add(ender.getItem(i).copy());
		}
		InventoryViewMenu.open(viewer, target.getGameProfile().name() + "'s ender chest", snapshot, 0);
		return Command.SINGLE_SUCCESS;
	}
}
