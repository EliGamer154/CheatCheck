package com.tradeshop.moderation;

import java.util.UUID;

/** A single player-filed report: who reported whom, for what, and when. */
public final class Report {
	public final UUID reporterId;
	public final String reporterName;
	public final UUID targetId;
	public final String targetName;
	public final String reason;
	public final long timestampMs;

	public Report(UUID reporterId, String reporterName, UUID targetId, String targetName, String reason, long timestampMs) {
		this.reporterId = reporterId;
		this.reporterName = reporterName;
		this.targetId = targetId;
		this.targetName = targetName;
		this.reason = reason;
		this.timestampMs = timestampMs;
	}
}
