package com.tradeshop.moderation;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * In-memory admin activity log (recent moderation actions, for real ops to review) plus a per-day action
 * limiter used by {@code /warn} and {@code /kick}. Resets on server restart.
 */
public final class AdminLog {
	private static final AdminLog INSTANCE = new AdminLog();
	private static final int MAX_ENTRIES = 100;
	private static final long DAY_MS = 86_400_000L;
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

	private final Deque<String> entries = new ArrayDeque<>();
	private final Map<String, Long> dailyActions = new HashMap<>();

	private AdminLog() {
	}

	public static AdminLog get() {
		return INSTANCE;
	}

	public void record(String line) {
		entries.addLast(LocalTime.now().format(TIME) + " " + line);
		while (entries.size() > MAX_ENTRIES) {
			entries.pollFirst();
		}
	}

	/** Most recent {@code count} log lines, oldest-first. */
	public List<String> recent(int count) {
		List<String> all = new ArrayList<>(entries);
		int from = Math.max(0, all.size() - count);
		return all.subList(from, all.size());
	}

	/** True if the admin hasn't done {@code action} on the target within the last day. */
	public boolean allowDaily(UUID adminId, UUID targetId, String action) {
		Long last = dailyActions.get(key(adminId, targetId, action));
		return last == null || System.currentTimeMillis() - last >= DAY_MS;
	}

	public void markDaily(UUID adminId, UUID targetId, String action) {
		dailyActions.put(key(adminId, targetId, action), System.currentTimeMillis());
	}

	private static String key(UUID adminId, UUID targetId, String action) {
		return adminId + "|" + targetId + "|" + action;
	}
}
