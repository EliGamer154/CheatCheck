package com.tradeshop.gui;

import com.tradeshop.moderation.AdminPerms;
import com.tradeshop.moderation.ModerationService;
import com.tradeshop.moderation.ModerationState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.UUID;

/**
 * The cheat-check screen. Reports are split into three sections shown as tabs: <b>Player</b> ({@code /report}),
 * <b>AI</b> (auto-flags), and <b>Admin</b> ({@code /adminreport}). Levels 1-2 only see the Player section;
 * level 3 and real ops see all three. Left-click a player to check them; right-click clears their reports
 * (level 3 / real op). Real ops also get "check any online" and violation editing.
 */
public class CheatCheckMenu extends ShopMenu {
	public enum Section {
		PLAYER("Player Reports", Items.WRITABLE_BOOK),
		AI("AI Flags", Items.OBSERVER),
		ADMIN("Admin Reports", Items.NAME_TAG);

		final String title;
		final net.minecraft.world.item.Item icon;

		Section(String title, net.minecraft.world.item.Item icon) {
			this.title = title;
			this.icon = icon;
		}
	}

	private final int page;
	private final Section section;

	private CheatCheckMenu(int containerId, ServerPlayer player, int page, Section section) {
		super(containerId, player);
		this.page = page;
		this.section = section;
		render();
	}

	public static void open(ServerPlayer player, int page) {
		open(player, page, Section.PLAYER);
	}

	public static void open(ServerPlayer player, int page, Section section) {
		player.openMenu(new SimpleMenuProvider(
				(id, inv, p) -> new CheatCheckMenu(id, player, page, section),
				Component.literal("Cheat Check: " + section.title)));
	}

	private void render() {
		fillBackground();
		MinecraftServer server = player.level().getServer();
		ModerationState state = ModerationState.get(server);
		boolean realOp = com.tradeshop.TradeShop.isOp(player);
		boolean canSeeAll = realOp || AdminPerms.level(player).map(l -> l.rank >= 3).orElse(false);

		// Levels 1-2 are locked to the Player section.
		Section sec = (section != Section.PLAYER && !canSeeAll) ? Section.PLAYER : section;
		List<ModerationState.ReportSummary> list = switch (sec) {
			case PLAYER -> state.reportedPlayers();
			case AI -> state.aiFlaggedPlayers();
			case ADMIN -> state.adminReportedPlayers();
		};

		int start = page * CONTENT_PAGE_SIZE;
		for (int i = 0; i < CONTENT_PAGE_SIZE; i++) {
			int index = start + i;
			if (index >= list.size()) {
				break;
			}
			ModerationState.ReportSummary summary = list.get(index);
			boolean online = server.getPlayerList().getPlayer(summary.targetId) != null;
			ItemStack head = Icons.head(summary.targetId, summary.targetName,
					"Reports: " + summary.count,
					"Reasons: " + String.join(", ", summary.reasons),
					"Status: " + (online ? "§aonline" : "§7offline"),
					online ? "§eLeft-click: check them" : "§8(offline — can't check)",
					canSeeAll ? "§eRight-click: clear reports" : "");
			setButton(contentSlot(i), head,
					() -> watch(summary.targetId),
					canSeeAll ? () -> clearReports(summary.targetId) : () -> watch(summary.targetId));
		}

		// Section tabs (top row). AI/Admin only for level 3 / real ops.
		tab(3, Section.PLAYER, sec);
		if (canSeeAll) {
			tab(4, Section.AI, sec);
			tab(5, Section.ADMIN, sec);
		}

		if (realOp) {
			setButton(45, Icons.of(new ItemStack(Items.ENDER_EYE), "Check any online player",
							"See everyone online and watch anyone"),
					() -> openLater(() -> OnlinePlayersMenu.open(player, 0)));
			setButton(53, Icons.of(new ItemStack(Items.COMPARATOR), "Edit violations & ban times",
							"Add, remove, and retune offenses"),
					() -> openLater(() -> OffenseConfigMenu.open(player, 0)));
		}
		setDisplay(49, Icons.of(new ItemStack(Items.PAPER), "Page " + (page + 1),
				list.size() + " " + sec.title.toLowerCase()));

		if (page > 0) {
			setButton(46, Icons.of(new ItemStack(Items.SPECTRAL_ARROW), "Previous Page"),
					() -> openLater(() -> CheatCheckMenu.open(player, page - 1, sec)));
		}
		if (start + CONTENT_PAGE_SIZE < list.size()) {
			setButton(52, Icons.of(new ItemStack(Items.SPECTRAL_ARROW), "Next Page"),
					() -> openLater(() -> CheatCheckMenu.open(player, page + 1, sec)));
		}
		refresh();
	}

	private void tab(int slot, Section tabSection, Section current) {
		String name = (tabSection == current ? "§a▶ " : "§7") + tabSection.title;
		setButton(slot, Icons.of(new ItemStack(tabSection.icon), name),
				() -> openLater(() -> CheatCheckMenu.open(player, 0, tabSection)));
	}

	private void watch(UUID targetId) {
		ServerPlayer target = player.level().getServer().getPlayerList().getPlayer(targetId);
		if (target == null) {
			player.sendSystemMessage(Component.literal("That player is offline — can't check them right now."));
			openLater(() -> CheatCheckMenu.open(player, page, section));
			return;
		}
		if (target.getUUID().equals(player.getUUID())) {
			player.sendSystemMessage(Component.literal("You can't check yourself."));
			openLater(() -> CheatCheckMenu.open(player, page, section));
			return;
		}
		openLater(() -> {
			player.closeContainer();
			ModerationService.startWatch(player, target);
			// Custom admins (non-ops) get a timed, restricted check session; real ops are unrestricted.
			if (!com.tradeshop.TradeShop.isOp(player)) {
				AdminPerms.level(player).ifPresent(lvl -> com.tradeshop.moderation.AdminCheckSession.get().start(player, lvl));
			}
		});
	}

	private void clearReports(UUID targetId) {
		int cleared = ModerationState.get(player.level().getServer()).clearReportsFor(targetId);
		player.sendSystemMessage(Component.literal("Cleared " + cleared + " report(s)."));
		openLater(() -> CheatCheckMenu.open(player, page, section));
	}
}
