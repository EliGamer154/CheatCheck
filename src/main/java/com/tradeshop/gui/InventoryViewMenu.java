package com.tradeshop.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * OP-only read-only view of a snapshot of another player's inventory or ender chest. Items are shown as
 * icons (right-click a shulker to peek inside); nothing can be moved, so it's safe and dupe-proof.
 */
public class InventoryViewMenu extends ShopMenu {
	private final String title;
	private final List<ItemStack> items;
	private final int page;

	private InventoryViewMenu(int containerId, ServerPlayer viewer, String title, List<ItemStack> items, int page) {
		super(containerId, viewer);
		this.title = title;
		this.items = items;
		this.page = page;
		render();
	}

	public static void open(ServerPlayer viewer, String title, List<ItemStack> items, int page) {
		viewer.openMenu(new SimpleMenuProvider(
				(id, inv, p) -> new InventoryViewMenu(id, viewer, title, items, page), Component.literal(title)));
	}

	private void render() {
		fillBackground();
		int start = page * CONTENT_PAGE_SIZE;
		for (int i = 0; i < CONTENT_PAGE_SIZE; i++) {
			int index = start + i;
			if (index >= items.size()) {
				break;
			}
			ItemStack stack = items.get(index);
			if (!stack.isEmpty()) {
				setItemDisplay(contentSlot(i), stack, () -> InventoryViewMenu.open(player, title, items, page));
			}
		}

		int filled = (int) items.stream().filter(s -> !s.isEmpty()).count();
		setDisplay(49, Icons.of(new ItemStack(Items.PAPER), "Page " + (page + 1), filled + " item stack(s)"));
		if (page > 0) {
			setButton(46, Icons.of(new ItemStack(Items.SPECTRAL_ARROW), "Previous Page"),
					() -> openLater(() -> InventoryViewMenu.open(player, title, items, page - 1)));
		}
		if (start + CONTENT_PAGE_SIZE < items.size()) {
			setButton(52, Icons.of(new ItemStack(Items.SPECTRAL_ARROW), "Next Page"),
					() -> openLater(() -> InventoryViewMenu.open(player, title, items, page + 1)));
		}
		refresh();
	}
}
