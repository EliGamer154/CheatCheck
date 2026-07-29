package com.tradeshop.moderation;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Tracks, per admin, whether safemode is on and which player (if any) they're actively watching.
 *
 * <p>Safemode is the master switch for the moderation tooling's client-affecting behavior: while it's on
 * the admin's tab-list name stays un-grayed even in spectator ({@code PlayerInfoUpdatePacketMixin}), they
 * can't place or break blocks, and if they're watching a target they're leashed within a radius of them.
 * Toggling safemode off clears all of it, including the current watch target.
 *
 * <p>State is in-memory only and keyed by player UUID; it naturally resets on server restart, which is
 * correct — a watch session doesn't outlive a reboot.
 */
public final class SafeModeManager {
	private static final SafeModeManager INSTANCE = new SafeModeManager();

	/** Admins with safemode currently enabled. */
	private final Map<UUID, Boolean> safeMode = new HashMap<>();
	/** For watching admins, the UUID of the player they're leashed to. */
	private final Map<UUID, UUID> watchTarget = new HashMap<>();

	private SafeModeManager() {
	}

	public static SafeModeManager get() {
		return INSTANCE;
	}

	public boolean isSafeMode(UUID adminId) {
		return safeMode.getOrDefault(adminId, false);
	}

	public boolean isSafeMode(ServerPlayer admin) {
		return isSafeMode(admin.getUUID());
	}

	public void setSafeMode(UUID adminId, boolean enabled) {
		if (enabled) {
			safeMode.put(adminId, true);
		} else {
			safeMode.remove(adminId);
			watchTarget.remove(adminId);
		}
	}

	/** Flips safemode for the admin and returns the new state. Disabling also clears their watch target. */
	public boolean toggle(UUID adminId) {
		boolean now = !isSafeMode(adminId);
		setSafeMode(adminId, now);
		return now;
	}

	public void setWatchTarget(UUID adminId, UUID targetId) {
		watchTarget.put(adminId, targetId);
	}

	public void clearWatchTarget(UUID adminId) {
		watchTarget.remove(adminId);
	}

	public Optional<UUID> watchTarget(UUID adminId) {
		return Optional.ofNullable(watchTarget.get(adminId));
	}

	/** Forgets an admin entirely (e.g. on disconnect). */
	public void forget(UUID adminId) {
		safeMode.remove(adminId);
		watchTarget.remove(adminId);
	}

	/** Stops any admin from watching the given target (e.g. when that target logs off). */
	public void clearWatchersOf(UUID targetId) {
		watchTarget.values().removeIf(id -> id.equals(targetId));
	}
}
