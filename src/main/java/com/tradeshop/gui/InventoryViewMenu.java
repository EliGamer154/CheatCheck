package com.tradeshop.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Live view of another player's inventory or ender chest, backed by their real container. When {@code canTake}
 * is set, left-click removes an item and gives it to the viewer (real ops); otherwise it's read-only (custom
 * checker admins). Right-click a shulker to peek inside either way.
 */
public class InventoryViewMenu extends ShopMenu {
	private final ServerPlayer target;
	private final Container source;
	private final String title;
	private final int page;
	private final boolean canTake;

	private InventoryViewMenu(int containerId, ServerPlayer viewer, ServerPlayer target, Container source,
			String title, int page, boolean canTake) {
		super(containerId, viewer);
		this.target = target;
		this.source = source;
		this.title = title;
		this.page = page;
		this.canTake = canTake;
		render();
	}

	public static void open(ServerPlayer viewer, ServerPlayer target, Container source, String title, int page, boolean canTake) {
		viewer.openMenu(new SimpleMenuProvider(
				(id, inv, p) -> new InventoryViewMenu(id, viewer, target, source, title, page, canTake), Component.literal(title)));
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
					canTake ? "§eLeft-click to take" : "§8(read-only)", Icons.peekHint(stack));
			setItemButton(contentSlot(i), icon,
					canTake ? () -> take(index) : () -> InventoryViewMenu.open(player, target, source, title, page, false),
					() -> InventoryViewMenu.open(player, target, source, title, page, canTake));
		}

		setDisplay(49, Icons.of(new ItemStack(Items.PAPER), "Page " + (page + 1),
				filled + " item stack(s)", canTake ? "§7Left-click an item to take it" : "§7Read-only"));
		if (page > 0) {
			setButton(46, Icons.of(new ItemStack(Items.SPECTRAL_ARROW), "Previous Page"),
					() -> openLater(() -> InventoryViewMenu.open(player, target, source, title, page - 1, canTake)));
		}
		if (start + CONTENT_PAGE_SIZE < size) {
			setButton(52, Icons.of(new ItemStack(Items.SPECTRAL_ARROW), "Next Page"),
					() -> openLater(() -> InventoryViewMenu.open(player, target, source, title, page + 1, canTake)));
		}
		refresh();
	}

	private void take(int index) {
		if (!canTake) {
			return;
		}
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
		openLater(() -> InventoryViewMenu.open(player, target, source, title, page, true));
	}
}
