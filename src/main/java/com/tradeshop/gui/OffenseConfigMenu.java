package com.tradeshop.gui;

import com.tradeshop.moderation.DurationParser;
import com.tradeshop.moderation.ModerationState;
import com.tradeshop.moderation.Violation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * OP-only "customizable menu": lists every {@link Violation} with its preset ban time. Left-click a row to
 * bump the ban time up the preset ladder, right-click to step it down. Violations are added/removed with
 * {@code /offend add} / {@code /offend remove}.
 */
public class OffenseConfigMenu extends ShopMenu {
	/** Preset ban-time ladder in seconds, ascending; {@link DurationParser#PERMANENT} sits above them all. */
	private static final long[] LADDER = {3600, 21600, 43200, 86400, 259200, 604800, 1209600, 2592000};

	private final int page;

	private OffenseConfigMenu(int containerId, ServerPlayer player, int page) {
		super(containerId, player);
		this.page = page;
		render();
	}

	public static void open(ServerPlayer player, int page) {
		player.openMenu(new SimpleMenuProvider(
				(id, inv, p) -> new OffenseConfigMenu(id, player, page), Component.literal("Edit Violations")));
	}

	private void render() {
		fillBackground();
		ModerationState state = ModerationState.get(player.level().getServer());
		List<Violation> violations = state.violations();

		int start = page * CONTENT_PAGE_SIZE;
		for (int i = 0; i < CONTENT_PAGE_SIZE; i++) {
			int index = start + i;
			if (index >= violations.size()) {
				break;
			}
			Violation v = violations.get(index);
			ItemStack icon = Icons.of(new ItemStack(Items.CLOCK), "§f" + v.name,
					"Preset ban: §e" + DurationParser.format(v.banSeconds),
					"§eLeft-click: longer",
					"§eRight-click: shorter",
					"§8Delete with /offend remove " + v.name);
			setButton(contentSlot(i), icon,
					() -> adjust(v.name, true),
					() -> adjust(v.name, false));
		}

		setButton(45, Icons.of(new ItemStack(Items.ARROW), "Back to reports"),
				() -> openLater(() -> CheatCheckMenu.open(player, 0)));
		setDisplay(49, Icons.of(new ItemStack(Items.PAPER), "Page " + (page + 1),
				violations.size() + " violation(s)"));
		setDisplay(53, Icons.of(new ItemStack(Items.WRITABLE_BOOK), "Add a violation",
				"Use /offend add <name> <time>", "e.g. /offend add fly 3d"));

		if (page > 0) {
			setButton(46, Icons.of(new ItemStack(Items.SPECTRAL_ARROW), "Previous Page"),
					() -> openLater(() -> OffenseConfigMenu.open(player, page - 1)));
		}
		if (start + CONTENT_PAGE_SIZE < violations.size()) {
			setButton(52, Icons.of(new ItemStack(Items.SPECTRAL_ARROW), "Next Page"),
					() -> openLater(() -> OffenseConfigMenu.open(player, page + 1)));
		}
		refresh();
	}

	private void adjust(String name, boolean up) {
		ModerationState state = ModerationState.get(player.level().getServer());
		state.findViolation(name).ifPresent(v -> {
			long next = up ? stepUp(v.banSeconds) : stepDown(v.banSeconds);
			state.setViolationBanSeconds(name, next);
		});
		openLater(() -> OffenseConfigMenu.open(player, page));
	}

	private static long stepUp(long current) {
		if (current == DurationParser.PERMANENT) {
			return DurationParser.PERMANENT;
		}
		for (long value : LADDER) {
			if (value > current) {
				return value;
			}
		}
		return DurationParser.PERMANENT;
	}

	private static long stepDown(long current) {
		if (current == DurationParser.PERMANENT) {
			return LADDER[LADDER.length - 1];
		}
		long result = LADDER[0];
		for (long value : LADDER) {
			if (value < current) {
				result = value;
			}
		}
		return result;
	}
}
