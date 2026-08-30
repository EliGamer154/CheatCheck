package com.tradeshop.moderation;

import com.tradeshop.config.TradeShopConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tags players as "in combat" for a short window after player-vs-player damage, so certain escape commands
 * (/tpa, /home, ...) can be blocked while tagged. In-memory; resets on restart.
 */
public final class CombatTracker {
	private static final CombatTracker INSTANCE = new CombatTracker();

	private final Map<UUID, Long> combatUntil = new HashMap<>();

	private CombatTracker() {
	}

	public static CombatTracker get() {
		return INSTANCE;
	}

	/** Marks the player in combat for the configured duration. */
	public void tag(UUID id) {
		combatUntil.put(id, System.currentTimeMillis() + TradeShopConfig.get().combatTagSeconds * 1000L);
	}

	public boolean isInCombat(UUID id) {
		Long until = combatUntil.get(id);
		return until != null && System.currentTimeMillis() < until;
	}
}
