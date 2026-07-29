package com.tradeshop.gui;

import com.tradeshop.config.TradeShopConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;

/**
 * OP-only in-game settings screen for the anti-cheat / moderation tunables. Left-click a setting to raise
 * it, right-click to lower it (booleans toggle). Every change writes {@code config/tradeshop.json}
 * immediately, so there's no need to edit the file or reload by hand.
 */
public class CheatConfigMenu extends ShopMenu {
	private CheatConfigMenu(int containerId, ServerPlayer player) {
		super(containerId, player);
		render();
	}

	public static void open(ServerPlayer player) {
		player.openMenu(new SimpleMenuProvider(
				(id, inv, p) -> new CheatConfigMenu(id, player), Component.literal("Cheat Config")));
	}

	private void render() {
		fillBackground();
		TradeShopConfig c = TradeShopConfig.get();

		boolSetting(10, Items.REDSTONE_TORCH, "AI Checker", c.aiCheckerEnabled, v -> c.aiCheckerEnabled = v);
		doubleSetting(11, Items.IRON_SWORD, "Reach threshold", c.aiReachThreshold, 0.5, 3.0, 10.0, " blocks", v -> c.aiReachThreshold = v);
		intSetting(12, Items.CLOCK, "Reach window", c.aiReachWindowSeconds, 5, 1, 120, "s", v -> c.aiReachWindowSeconds = v);
		intSetting(13, Items.PAPER, "Reach hits to flag", c.aiReachViolationsToFlag, 1, 1, 20, "", v -> c.aiReachViolationsToFlag = v);
		intSetting(14, Items.DIAMOND_ORE, "X-ray ore threshold", c.aiOreThreshold, 2, 1, 200, " ores", v -> c.aiOreThreshold = v);
		intSetting(15, Items.COAL_ORE, "X-ray ore window", c.aiOreWindowSeconds, 30, 30, 3600, "s", v -> c.aiOreWindowSeconds = v);
		doubleSetting(16, Items.FEATHER, "Speed threshold", c.aiSpeedThreshold, 1.0, 3.0, 40.0, " b/s", v -> c.aiSpeedThreshold = v);

		intSetting(20, Items.BARRIER, "Flag cooldown", c.aiFlagCooldownSeconds, 30, 0, 3600, "s", v -> c.aiFlagCooldownSeconds = v);
		intSetting(21, Items.WRITABLE_BOOK, "Report cooldown", c.reportCooldownSeconds, 10, 0, 600, "s", v -> c.reportCooldownSeconds = v);
		doubleSetting(22, Items.LEAD, "Safemode leash radius", c.safeModeLeashRadius, 10, 5, 500, " blocks", v -> c.safeModeLeashRadius = v);
		doubleSetting(23, Items.IRON_BARS, "Jail radius", c.jailRadius, 5, 2, 200, " blocks", v -> c.jailRadius = v);

		setDisplay(49, Icons.of(new ItemStack(Items.COMPARATOR), "Cheat Config",
				"Left-click a setting to raise it", "Right-click to lower it", "Changes save instantly"));
		refresh();
	}

	private void boolSetting(int slot, Item icon, String name, boolean value, Consumer<Boolean> setter) {
		ItemStack stack = Icons.of(new ItemStack(icon), name,
				"Value: " + (value ? "§aON" : "§cOFF"), "§eClick to toggle");
		Runnable toggle = () -> {
			setter.accept(!value);
			apply();
		};
		setButton(slot, stack, toggle, toggle);
	}

	private void doubleSetting(int slot, Item icon, String name, double value, double step, double min, double max,
			String unit, DoubleConsumer setter) {
		ItemStack stack = Icons.of(new ItemStack(icon), name,
				"Value: §e" + fmt(value) + unit, "§eLeft-click: +" + fmt(step), "§eRight-click: -" + fmt(step));
		setButton(slot, stack,
				() -> { setter.accept(clamp(value + step, min, max)); apply(); },
				() -> { setter.accept(clamp(value - step, min, max)); apply(); });
	}

	private void intSetting(int slot, Item icon, String name, int value, int step, int min, int max,
			String unit, IntConsumer setter) {
		ItemStack stack = Icons.of(new ItemStack(icon), name,
				"Value: §e" + value + unit, "§eLeft-click: +" + step, "§eRight-click: -" + step);
		setButton(slot, stack,
				() -> { setter.accept((int) clamp(value + step, min, max)); apply(); },
				() -> { setter.accept((int) clamp(value - step, min, max)); apply(); });
	}

	private void apply() {
		TradeShopConfig.get().save();
		openLater(() -> CheatConfigMenu.open(player));
	}

	private static double clamp(double value, double min, double max) {
		return value < min ? min : Math.min(value, max);
	}

	private static String fmt(double value) {
		return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
	}
}
