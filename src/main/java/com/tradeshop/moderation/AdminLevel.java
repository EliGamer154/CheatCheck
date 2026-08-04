package com.tradeshop.moderation;

import java.util.Optional;

/**
 * A custom checker-admin rank, granting a subset of moderation powers to non-op players.
 * <ul>
 *   <li>{@link #LEVEL_1} — open /cheatcheck and check reported players (3-minute checks).</li>
 *   <li>{@link #LEVEL_2} — + /adminreport, /warn, /spawnstash (empty), /spawnores (4-minute checks).</li>
 *   <li>{@link #LEVEL_3} — + /kick and the Admin/AI report sections (5-minute checks).</li>
 *   <li>{@link #LEVEL_3_PLUS} — + /invsee and /endersee (5-minute checks).</li>
 * </ul>
 * {@code rank} is used for "at least level N" checks; {@link #LEVEL_3_PLUS} shares rank 3 with {@link #LEVEL_3}.
 */
public enum AdminLevel {
	LEVEL_1("1", 1, 3, false),
	LEVEL_2("2", 2, 4, false),
	LEVEL_3("3", 3, 5, false),
	LEVEL_3_PLUS("3+", 3, 5, true);

	public final String label;
	public final int rank;
	public final int timerMinutes;
	public final boolean canInspectInventories;

	AdminLevel(String label, int rank, int timerMinutes, boolean canInspectInventories) {
		this.label = label;
		this.rank = rank;
		this.timerMinutes = timerMinutes;
		this.canInspectInventories = canInspectInventories;
	}

	/** Parses a user-typed level: {@code 1}, {@code 2}, {@code 3}, or {@code 3+}. */
	public static Optional<AdminLevel> parse(String input) {
		return switch (input.trim().toLowerCase()) {
			case "1" -> Optional.of(LEVEL_1);
			case "2" -> Optional.of(LEVEL_2);
			case "3" -> Optional.of(LEVEL_3);
			case "3+", "3plus" -> Optional.of(LEVEL_3_PLUS);
			default -> Optional.empty();
		};
	}

	/** Resolves a stored label back to a level. */
	public static Optional<AdminLevel> fromLabel(String label) {
		for (AdminLevel level : values()) {
			if (level.label.equals(label)) {
				return Optional.of(level);
			}
		}
		return Optional.empty();
	}
}
