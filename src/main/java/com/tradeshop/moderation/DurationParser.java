package com.tradeshop.moderation;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses human ban durations like {@code 7d}, {@code 2h}, {@code 30m}, {@code 45s} (and combinations
 * such as {@code 1d12h}) into a number of seconds, and formats a remaining duration back into a short
 * human string. The literal {@code perm}/{@code permanent}/{@code forever} maps to {@link #PERMANENT}.
 */
public final class DurationParser {
	/** Sentinel {@code banSeconds}/remaining value meaning "never expires". */
	public static final long PERMANENT = -1L;

	private static final Pattern UNIT = Pattern.compile("(\\d+)\\s*([smhdw])", Pattern.CASE_INSENSITIVE);

	private DurationParser() {
	}

	/**
	 * Parses a duration string into seconds. Returns empty when the input isn't a valid duration.
	 * {@code perm}/{@code permanent}/{@code forever} return {@link #PERMANENT}.
	 */
	public static Optional<Long> parseSeconds(String input) {
		if (input == null) {
			return Optional.empty();
		}
		String trimmed = input.trim().toLowerCase();
		if (trimmed.isEmpty()) {
			return Optional.empty();
		}
		if (trimmed.equals("perm") || trimmed.equals("permanent") || trimmed.equals("forever")) {
			return Optional.of(PERMANENT);
		}

		// Every character (ignoring whitespace) must belong to a "<number><unit>" token; reject junk like "7x".
		String compact = trimmed.replaceAll("\\s+", "");
		if (!compact.matches("(?i)(\\d+[smhdw])+")) {
			return Optional.empty();
		}

		Matcher matcher = UNIT.matcher(compact);
		long total = 0;
		while (matcher.find()) {
			long value;
			try {
				value = Long.parseLong(matcher.group(1));
			} catch (NumberFormatException e) {
				return Optional.empty();
			}
			total += value * unitSeconds(matcher.group(2).charAt(0));
		}
		return total > 0 ? Optional.of(total) : Optional.empty();
	}

	private static long unitSeconds(char unit) {
		return switch (Character.toLowerCase(unit)) {
			case 's' -> 1L;
			case 'm' -> 60L;
			case 'h' -> 3600L;
			case 'd' -> 86400L;
			case 'w' -> 604800L;
			default -> 0L;
		};
	}

	/** Formats a number of seconds as a compact string like {@code 7d 3h 12m}. */
	public static String format(long seconds) {
		if (seconds == PERMANENT) {
			return "permanent";
		}
		if (seconds <= 0) {
			return "0s";
		}
		long days = seconds / 86400;
		long hours = (seconds % 86400) / 3600;
		long minutes = (seconds % 3600) / 60;
		long secs = seconds % 60;
		StringBuilder sb = new StringBuilder();
		if (days > 0) {
			sb.append(days).append("d ");
		}
		if (hours > 0) {
			sb.append(hours).append("h ");
		}
		if (minutes > 0) {
			sb.append(minutes).append("m ");
		}
		if (secs > 0 && days == 0 && hours == 0) {
			sb.append(secs).append("s ");
		}
		return sb.toString().trim();
	}
}
