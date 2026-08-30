package com.tradeshop.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tradeshop.TradeShop;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Admin-editable settings, stored as JSON under config/tradeshop.json. Reload in-game with /shop reload. */
public class TradeShopConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("tradeshop.json");

	private static TradeShopConfig instance = load();

	/** How many listings a single player may have open at once. */
	public int maxActiveListingsPerPlayer = 15;

	/** How many distinct item types a single offer may contain. */
	public int maxOfferItemTypes = 9;

	/** Minimum seconds between a player's /report submissions. */
	public int reportCooldownSeconds = 60;

	/** How long (seconds) a player stays "in combat" after PvP damage, blocking escape commands like /tpa, /home. */
	public int combatTagSeconds = 15;

	/** In safemode, how far (blocks) a watching admin may stray from their target before being pulled back. */
	public double safeModeLeashRadius = 100.0;

	/** How far (blocks) a jailed player may move from the jail point before being pulled back (capped at 6 in code). */
	public double jailRadius = 5.0;

	/** Master switch for the automatic ("AI") cheat detectors. */
	public boolean aiCheckerEnabled = true;

	/** Attack distance (blocks, eye to nearest point of the target's hitbox) above which a hit counts as a reach violation. */
	public double aiReachThreshold = 4.5;

	/** Rolling window (seconds) over which reach violations are counted. */
	public int aiReachWindowSeconds = 10;

	/** Number of reach violations within the window before a player is actually flagged (avoids one-off lag hits). */
	public int aiReachViolationsToFlag = 4;

	/** Rolling window (seconds) over which valuable-ore mining is counted for the x-ray heuristic. */
	public int aiOreWindowSeconds = 300;

	/** Valuable ores mined within the window that trips an x-ray flag. */
	public int aiOreThreshold = 16;

	/** Sustained horizontal speed (blocks/second) above which movement is flagged as speed. */
	public double aiSpeedThreshold = 11.0;

	/** Minimum seconds between repeat auto-flags of the same player for the same category. */
	public int aiFlagCooldownSeconds = 120;

	public static TradeShopConfig get() {
		return instance;
	}

	public static TradeShopConfig load() {
		TradeShopConfig config = new TradeShopConfig();
		if (Files.exists(PATH)) {
			try {
				TradeShopConfig loaded = GSON.fromJson(Files.readString(PATH), TradeShopConfig.class);
				if (loaded != null) {
					config = loaded;
				}
			} catch (IOException e) {
				TradeShop.LOGGER.warn("Failed to read tradeshop.json, using defaults", e);
			}
		}
		config.save();
		instance = config;
		return config;
	}

	public void save() {
		try {
			Files.createDirectories(PATH.getParent());
			Files.writeString(PATH, GSON.toJson(this));
		} catch (IOException e) {
			TradeShop.LOGGER.warn("Failed to write tradeshop.json", e);
		}
	}
}
