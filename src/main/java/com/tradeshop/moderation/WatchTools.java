package com.tradeshop.moderation;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * In-memory state for the extra admin watch tools: which suspects are frozen (and where), who is vanished,
 * who is glowing, and each admin's pre-watch return point. All keyed by player UUID and reset on restart.
 */
public final class WatchTools {
	private static final WatchTools INSTANCE = new WatchTools();

	private final Map<UUID, FreezePoint> frozen = new HashMap<>();
	private final Set<UUID> vanished = new HashSet<>();
	private final Set<UUID> glowing = new HashSet<>();
	private final Map<UUID, ReturnPoint> returnPoints = new HashMap<>();

	private WatchTools() {
	}

	public static WatchTools get() {
		return INSTANCE;
	}

	// --- Freeze ---
	public boolean isFrozen(UUID id) {
		return frozen.containsKey(id);
	}

	public void freeze(UUID id, FreezePoint point) {
		frozen.put(id, point);
	}

	public void unfreeze(UUID id) {
		frozen.remove(id);
	}

	public Optional<FreezePoint> frozenPoint(UUID id) {
		return Optional.ofNullable(frozen.get(id));
	}

	// --- Vanish ---
	public boolean isVanished(UUID id) {
		return vanished.contains(id);
	}

	/** Flips vanish and returns the new state. */
	public boolean toggleVanish(UUID id) {
		if (vanished.remove(id)) {
			return false;
		}
		vanished.add(id);
		return true;
	}

	// --- Glow ---
	public boolean isGlowing(UUID id) {
		return glowing.contains(id);
	}

	public void setGlowing(UUID id, boolean on) {
		if (on) {
			glowing.add(id);
		} else {
			glowing.remove(id);
		}
	}

	// --- Return point ---
	public void setReturnPoint(UUID adminId, ReturnPoint point) {
		returnPoints.put(adminId, point);
	}

	public Optional<ReturnPoint> returnPoint(UUID adminId) {
		return Optional.ofNullable(returnPoints.get(adminId));
	}

	public void clearReturnPoint(UUID adminId) {
		returnPoints.remove(adminId);
	}

	/** Forget everything about a player (e.g. on disconnect). */
	public void forget(UUID id) {
		frozen.remove(id);
		vanished.remove(id);
		glowing.remove(id);
		returnPoints.remove(id);
	}

	/** Where a frozen player is pinned. */
	public record FreezePoint(ServerLevel level, double x, double y, double z, float yRot, float xRot) {
	}

	/** Where (and in what gamemode) an admin was before they started watching. */
	public record ReturnPoint(ServerLevel level, double x, double y, double z, float yRot, float xRot, GameType mode) {
	}
}
