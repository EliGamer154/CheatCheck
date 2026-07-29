package com.tradeshop.moderation;

/**
 * A named cheat category (e.g. {@code esp}, {@code xray}) that players can be reported for and admins
 * can act on with {@code /offend}. Each carries a preset ban duration in seconds, where
 * {@link DurationParser#PERMANENT} (-1) means a permanent ban.
 */
public final class Violation {
	public String name;
	public long banSeconds;

	public Violation(String name, long banSeconds) {
		this.name = name;
		this.banSeconds = banSeconds;
	}

	/** Case-insensitive comparison against a user-supplied name. */
	public boolean matches(String other) {
		return other != null && name.equalsIgnoreCase(other.trim());
	}
}
