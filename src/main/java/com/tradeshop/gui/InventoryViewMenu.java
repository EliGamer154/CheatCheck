package com.tradeshop.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * OP-only live view of another player's inventory or ender chest. Left-click an item to take it (it's
 * removed from them and given to you); right-click a shulker to peek inside. Backed by the target's real
 * container, so takes are immediate and persist.
 */
public class InventoryViewMenu extends ShopMenu {
	private final ServerPlayer target;
	private final Container source;
	private final String title;
	private final int page;

	private InventoryViewMenu(int containerId, ServerPlayer viewer, ServerPlayer target, Container source,
			String title, int page) {
		super(containerId, viewer);
		this.target = target;
		this.source = source;
		this.title = title;
		this.page = page;
		render();
	}

	public static void open(ServerPlayer viewer, ServerPlayer target, Container source, String title, int page) {
		viewer.openMenu(new SimpleMenuProvider(
				(id, inv, p) -> new InventoryViewMenu(id, viewer, target, source, title, page), Component.literal(title)));
	}

	private void render() {
		fillBackground();
		int size = source.getContainerSize();
		int start = page * CONTENT_PAGE_SIZE;
		int filled = 0;
		for (int i = 0; i < size; i++) {
			if (!source.getItem(i).isEmpty()) {
				filled++;
			}
		}

		for (int i = 0; i < CONTENT_PAGE_SIZE; i++) {
			int index = start + i;
			if (index >= size) {
				break;
			}
			ItemStack stack = source.getItem(index);
			if (stack.isEmpty()) {
				continue;
			}
			ItemStack icon = Icons.of(stack.copy(), stack.getHoverName().getString(),
					"§eLeft-click to take", Icons.peekHint(stack));
			setItemButton(contentSlot(i), icon,
					() -> take(index),
					() -> InventoryViewMenu.open(player, target, source, title, page));
		}

		setDisplay(49, Icons.of(new ItemStack(Items.PAPER), "Page " + (page + 1),
				filled + " item stack(s)", "§7Left-click an item to take it"));
		if (page > 0) {
			setButton(46, Icons.of(new ItemStack(Items.SPECTRAL_ARROW), "Previous Page"),
					() -> openLater(() -> InventoryViewMenu.open(player, target, source, title, page - 1)));
		}
		if (start + CONTENT_PAGE_SIZE < size) {
			setButton(52, Icons.of(new ItemStack(Items.SPECTRAL_ARROW), "Next Page"),
					() -> openLater(() -> InventoryViewMenu.open(player, target, source, title, page + 1)));
		}
		refresh();
	}

	private void take(int index) {
		if (player.level().getServer().getPlayerList().getPlayer(target.getUUID()) == null) {
			player.sendSystemMessage(Component.literal("That player went offline — can't take their items."));
			return;
		}
		ItemStack stack = source.removeItemNoUpdate(index);
		if (!stack.isEmpty()) {
			source.setChanged();
			// Sync the target's own screen if we pulled from their main inventory.
			if (source == target.getInventory()) {
				target.inventoryMenu.broadcastChanges();
			}
			if (!player.getInventory().add(stack)) {
				player.drop(stack, false);
			}
		}
		openLater(() -> InventoryViewMenu.open(player, target, source, title, page));
	}
}
