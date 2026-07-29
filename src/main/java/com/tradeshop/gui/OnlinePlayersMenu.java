package com.tradeshop.gui;

import com.tradeshop.moderation.ModerationService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/** OP-only screen listing everyone currently online (except the viewer) so an admin can watch anyone. */
public class OnlinePlayersMenu extends ShopMenu {
	private final int page;

	private OnlinePlayersMenu(int containerId, ServerPlayer player, int page) {
		super(containerId, player);
		this.page = page;
		render();
	}

	public static void open(ServerPlayer player, int page) {
		player.openMenu(new SimpleMenuProvider(
				(id, inv, p) -> new OnlinePlayersMenu(id, player, page), Component.literal("Cheat Check: Online")));
	}

	private void render() {
		fillBackground();
		MinecraftServer server = player.level().getServer();
		List<ServerPlayer> players = server.getPlayerList().getPlayers().stream()
				.filter(p -> !p.getUUID().equals(player.getUUID()))
				.toList();

		int start = page * CONTENT_PAGE_SIZE;
		for (int i = 0; i < CONTENT_PAGE_SIZE; i++) {
			int index = start + i;
			if (index >= players.size()) {
				break;
			}
			ServerPlayer target = players.get(index);
			ItemStack head = Icons.head(target.getUUID(), target.getGameProfile().name(),
					"§eLeft-click: watch them");
			setButton(contentSlot(i), head, () -> watch(target.getUUID()));
		}

		setButton(45, Icons.of(new ItemStack(Items.ARROW), "Back to reports"),
				() -> openLater(() -> CheatCheckMenu.open(player, 0)));
		setDisplay(49, Icons.of(new ItemStack(Items.PAPER), "Page " + (page + 1),
				players.size() + " player(s) online"));

		if (page > 0) {
			setButton(46, Icons.of(new ItemStack(Items.SPECTRAL_ARROW), "Previous Page"),
					() -> openLater(() -> OnlinePlayersMenu.open(player, page - 1)));
		}
		if (start + CONTENT_PAGE_SIZE < players.size()) {
			setButton(52, Icons.of(new ItemStack(Items.SPECTRAL_ARROW), "Next Page"),
					() -> openLater(() -> OnlinePlayersMenu.open(player, page + 1)));
		}
		refresh();
	}

	private void watch(java.util.UUID targetId) {
		ServerPlayer target = player.level().getServer().getPlayerList().getPlayer(targetId);
		if (target == null) {
			player.sendSystemMessage(Component.literal("That player just went offline."));
			openLater(() -> OnlinePlayersMenu.open(player, page));
			return;
		}
		openLater(() -> {
			player.closeContainer();
			ModerationService.startWatch(player, target);
		});
	}
}
