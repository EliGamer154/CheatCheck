package com.tradeshop.gui;

import com.tradeshop.moderation.ModerationService;
import com.tradeshop.moderation.ModerationState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * OP-only screen listing every reported player as a head. Left-click a player to start watching them
 * (spectate + teleport + safemode); right-click to clear their reports. Also links to a "watch anyone
 * online" list and the customizable violation menu.
 */
public class CheatCheckMenu extends ShopMenu {
	private final int page;

	private CheatCheckMenu(int containerId, ServerPlayer player, int page) {
		super(containerId, player);
		this.page = page;
		render();
	}

	public static void open(ServerPlayer player, int page) {
		player.openMenu(new SimpleMenuProvider(
				(id, inv, p) -> new CheatCheckMenu(id, player, page), Component.literal("Cheat Check: Reports")));
	}

	private void render() {
		fillBackground();
		MinecraftServer server = player.level().getServer();
		ModerationState state = ModerationState.get(server);
		List<ModerationState.ReportSummary> reported = state.reportedPlayers();

		int start = page * CONTENT_PAGE_SIZE;
		for (int i = 0; i < CONTENT_PAGE_SIZE; i++) {
			int index = start + i;
			if (index >= reported.size()) {
				break;
			}
			ModerationState.ReportSummary summary = reported.get(index);
			boolean online = server.getPlayerList().getPlayer(summary.targetId) != null;
			ItemStack head = Icons.head(summary.targetId, summary.targetName,
					"Reports: " + summary.count,
					"Reasons: " + String.join(", ", summary.reasons),
					"Status: " + (online ? "§aonline" : "§7offline"),
					online ? "§eLeft-click: watch them" : "§8(offline — can't watch)",
					"§eRight-click: clear reports");
			setButton(contentSlot(i), head,
					() -> watch(summary.targetId),
					() -> clearReports(summary.targetId));
		}

		// "Check anyone" and violation editing are real-op only; custom checker admins just get the reported list.
		boolean realOp = com.tradeshop.TradeShop.isOp(player);
		if (realOp) {
			setButton(45, Icons.of(new ItemStack(Items.ENDER_EYE), "Check any online player",
							"See everyone online and watch anyone"),
					() -> openLater(() -> OnlinePlayersMenu.open(player, 0)));
			setButton(53, Icons.of(new ItemStack(Items.COMPARATOR), "Edit violations & ban times",
							"Add, remove, and retune offenses"),
					() -> openLater(() -> OffenseConfigMenu.open(player, 0)));
		}
		setDisplay(49, Icons.of(new ItemStack(Items.PAPER), "Page " + (page + 1),
				reported.size() + " reported player(s)"));

		if (page > 0) {
			setButton(46, Icons.of(new ItemStack(Items.SPECTRAL_ARROW), "Previous Page"),
					() -> openLater(() -> CheatCheckMenu.open(player, page - 1)));
		}
		if (start + CONTENT_PAGE_SIZE < reported.size()) {
			setButton(52, Icons.of(new ItemStack(Items.SPECTRAL_ARROW), "Next Page"),
					() -> openLater(() -> CheatCheckMenu.open(player, page + 1)));
		}
		refresh();
	}

	private void watch(java.util.UUID targetId) {
		ServerPlayer target = player.level().getServer().getPlayerList().getPlayer(targetId);
		if (target == null) {
			player.sendSystemMessage(Component.literal("That player is offline — can't watch them right now."));
			openLater(() -> CheatCheckMenu.open(player, page));
			return;
		}
		if (target.getUUID().equals(player.getUUID())) {
			player.sendSystemMessage(Component.literal("You can't watch yourself."));
			openLater(() -> CheatCheckMenu.open(player, page));
			return;
		}
		openLater(() -> {
			player.closeContainer();
			ModerationService.startWatch(player, target);
			// Custom admins (non-ops) get a timed, restricted check session; real ops are unrestricted.
			if (!com.tradeshop.TradeShop.isOp(player)) {
				com.tradeshop.moderation.AdminPerms.level(player)
						.ifPresent(lvl -> com.tradeshop.moderation.AdminCheckSession.get().start(player, lvl));
			}
		});
	}

	private void clearReports(java.util.UUID targetId) {
		int cleared = ModerationState.get(player.level().getServer()).clearReportsFor(targetId);
		player.sendSystemMessage(Component.literal("Cleared " + cleared + " report(s)."));
		openLater(() -> CheatCheckMenu.open(player, page));
	}
}
