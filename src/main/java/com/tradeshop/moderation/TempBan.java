package com.tradeshop.moderation;

import java.util.UUID;

/**
 * An active temp-ban record. {@code expiryMs} is a wall-clock epoch-millis instant, or
 * {@link DurationParser#PERMANENT} (-1) for a permanent ban that never auto-expires.
 */
public final class TempBan {
	public final UUID targetId;
	public final String targetName;
	public final String reason;
	public final long expiryMs;
	public final String bannedBy;

	public TempBan(UUID targetId, String targetName, String reason, long expiryMs, String bannedBy) {
		this.targetId = targetId;
		this.targetName = targetName;
		this.reason = reason;
		this.expiryMs = expiryMs;
		this.bannedBy = bannedBy;
	}

	public boolean isPermanent() {
		return expiryMs == DurationParser.PERMANENT;
	}

	/** True once the ban's expiry instant has passed. Permanent bans never expire. */
	public boolean isExpired(long nowMs) {
		return !isPermanent() && nowMs >= expiryMs;
	}

	/** Remaining seconds until expiry, or {@link DurationParser#PERMANENT} for a permanent ban. */
	public long remainingSeconds(long nowMs) {
		if (isPermanent()) {
			return DurationParser.PERMANENT;
		}
		return Math.max(0, (expiryMs - nowMs) / 1000);
	}
}
