package com.tradeshop.moderation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tradeshop.TradeShop;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistent moderation data for a server: the editable list of cheat {@link Violation}s, all filed
 * {@link Report}s, and every active {@link TempBan}. Saved per-world via {@link SavedData}, exactly like
 * {@code ShopState}. Fresh worlds are seeded with a default violation list.
 */
public class ModerationState extends SavedData {
	/** Default preset ban time for seeded violations: 7 days. */
	private static final long DEFAULT_BAN_SECONDS = 7L * 86400L;

	private static final Codec<Violation> VIOLATION_CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("name").forGetter(v -> v.name),
			Codec.LONG.fieldOf("banSeconds").forGetter(v -> v.banSeconds)
	).apply(instance, Violation::new));

	private static final Codec<Report> REPORT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.CODEC.fieldOf("reporterId").forGetter(r -> r.reporterId),
			Codec.STRING.fieldOf("reporterName").forGetter(r -> r.reporterName),
			UUIDUtil.CODEC.fieldOf("targetId").forGetter(r -> r.targetId),
			Codec.STRING.fieldOf("targetName").forGetter(r -> r.targetName),
			Codec.STRING.fieldOf("reason").forGetter(r -> r.reason),
			Codec.LONG.fieldOf("timestampMs").forGetter(r -> r.timestampMs)
	).apply(instance, Report::new));

	private static final Codec<TempBan> BAN_CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.CODEC.fieldOf("targetId").forGetter(b -> b.targetId),
			Codec.STRING.fieldOf("targetName").forGetter(b -> b.targetName),
			Codec.STRING.fieldOf("reason").forGetter(b -> b.reason),
			Codec.LONG.fieldOf("expiryMs").forGetter(b -> b.expiryMs),
			Codec.STRING.fieldOf("bannedBy").forGetter(b -> b.bannedBy)
	).apply(instance, TempBan::new));

	private static final Codec<JailPoint> JAIL_POINT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("dimension").forGetter(JailPoint::dimension),
			Codec.DOUBLE.fieldOf("x").forGetter(JailPoint::x),
			Codec.DOUBLE.fieldOf("y").forGetter(JailPoint::y),
			Codec.DOUBLE.fieldOf("z").forGetter(JailPoint::z),
			Codec.FLOAT.fieldOf("yaw").forGetter(JailPoint::yaw),
			Codec.FLOAT.fieldOf("pitch").forGetter(JailPoint::pitch)
	).apply(instance, JailPoint::new));

	public static final Codec<ModerationState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			VIOLATION_CODEC.listOf().fieldOf("violations").forGetter(s -> s.violations),
			REPORT_CODEC.listOf().fieldOf("reports").forGetter(s -> s.reports),
			BAN_CODEC.listOf().fieldOf("bans").forGetter(s -> s.bans),
			JAIL_POINT_CODEC.optionalFieldOf("jail").forGetter(s -> Optional.ofNullable(s.jailPoint)),
			UUIDUtil.CODEC.listOf().optionalFieldOf("jailed", List.of()).forGetter(s -> s.jailed)
	).apply(instance, ModerationState::new));

	public static final SavedDataType<ModerationState> TYPE = new SavedDataType<>(
			Identifier.fromNamespaceAndPath(TradeShop.MOD_ID, "moderation_data"), ModerationState::new, CODEC, null);

	private final List<Violation> violations;
	private final List<Report> reports;
	private final List<TempBan> bans;
	private final List<UUID> jailed;
	private JailPoint jailPoint;

	public ModerationState() {
		this(defaultViolations(), new ArrayList<>(), new ArrayList<>(), Optional.empty(), new ArrayList<>());
	}

	private ModerationState(List<Violation> violations, List<Report> reports, List<TempBan> bans,
			Optional<JailPoint> jailPoint, List<UUID> jailed) {
		this.violations = new ArrayList<>(violations);
		this.reports = new ArrayList<>(reports);
		this.bans = new ArrayList<>(bans);
		this.jailPoint = jailPoint.orElse(null);
		this.jailed = new ArrayList<>(jailed);
	}

	private static List<Violation> defaultViolations() {
		List<Violation> defaults = new ArrayList<>();
		for (String name : new String[]{"esp", "range", "xray", "kill aura", "auto totem"}) {
			defaults.add(new Violation(name, DEFAULT_BAN_SECONDS));
		}
		return defaults;
	}

	public static ModerationState get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	// --- Violations -------------------------------------------------------

	public List<Violation> violations() {
		return new ArrayList<>(violations);
	}

	public Optional<Violation> findViolation(String name) {
		return violations.stream().filter(v -> v.matches(name)).findFirst();
	}

	/** Adds a new violation. Returns false if one with that name already exists. */
	public boolean addViolation(String name, long banSeconds) {
		if (findViolation(name).isPresent()) {
			return false;
		}
		violations.add(new Violation(name.trim(), banSeconds));
		setDirty();
		return true;
	}

	public boolean removeViolation(String name) {
		boolean removed = violations.removeIf(v -> v.matches(name));
		if (removed) {
			setDirty();
		}
		return removed;
	}

	public void setViolationBanSeconds(String name, long banSeconds) {
		findViolation(name).ifPresent(v -> {
			v.banSeconds = banSeconds;
			setDirty();
		});
	}

	// --- Reports ----------------------------------------------------------

	public void addReport(ServerPlayer reporter, ServerPlayer target, String reason) {
		reports.add(new Report(reporter.getUUID(), reporter.getGameProfile().name(),
				target.getUUID(), target.getGameProfile().name(), reason, System.currentTimeMillis()));
		setDirty();
	}

	/** Adds a report attributed to the automatic checker rather than a player. */
	public void addSystemReport(ServerPlayer target, String reason) {
		reports.add(new Report(new UUID(0, 0), "AntiCheat",
				target.getUUID(), target.getGameProfile().name(), reason, System.currentTimeMillis()));
		setDirty();
	}

	/** Removes every report filed against the given player. Returns how many were cleared. */
	public int clearReportsFor(UUID targetId) {
		int before = reports.size();
		reports.removeIf(r -> r.targetId.equals(targetId));
		int cleared = before - reports.size();
		if (cleared > 0) {
			setDirty();
		}
		return cleared;
	}

	/** Reported players grouped into one summary each, most-recently-reported first. */
	public List<ReportSummary> reportedPlayers() {
		Map<UUID, ReportSummary> grouped = new LinkedHashMap<>();
		for (Report report : reports) {
			ReportSummary summary = grouped.computeIfAbsent(report.targetId,
					id -> new ReportSummary(report.targetId, report.targetName));
			summary.add(report);
		}
		List<ReportSummary> result = new ArrayList<>(grouped.values());
		result.sort((a, b) -> Long.compare(b.latestMs, a.latestMs));
		return result;
	}

	// --- Bans -------------------------------------------------------------

	public void addBan(TempBan ban) {
		bans.removeIf(b -> b.targetId.equals(ban.targetId));
		bans.add(ban);
		setDirty();
	}

	/** Lifts a ban early. Returns true if the player had an active ban. */
	public boolean pardon(UUID targetId) {
		boolean removed = bans.removeIf(b -> b.targetId.equals(targetId));
		if (removed) {
			setDirty();
		}
		return removed;
	}

	/**
	 * Returns the player's active ban, pruning it first if it has expired. Returns empty when the player
	 * isn't banned (or their ban just lapsed).
	 */
	public Optional<TempBan> activeBan(UUID targetId) {
		long now = System.currentTimeMillis();
		Optional<TempBan> found = bans.stream().filter(b -> b.targetId.equals(targetId)).findFirst();
		if (found.isPresent() && found.get().isExpired(now)) {
			bans.remove(found.get());
			setDirty();
			return Optional.empty();
		}
		return found;
	}

	public List<TempBan> activeBans() {
		return new ArrayList<>(bans);
	}

	/** Drops every expired ban. Returns the list that was pruned so callers can log/notify. */
	public List<TempBan> pruneExpired() {
		long now = System.currentTimeMillis();
		List<TempBan> expired = new ArrayList<>();
		for (TempBan ban : bans) {
			if (ban.isExpired(now)) {
				expired.add(ban);
			}
		}
		if (!expired.isEmpty()) {
			bans.removeAll(expired);
			setDirty();
		}
		return expired;
	}

	// --- Jail -------------------------------------------------------------

	public Optional<JailPoint> jailPoint() {
		return Optional.ofNullable(jailPoint);
	}

	public void setJailPoint(JailPoint point) {
		this.jailPoint = point;
		setDirty();
	}

	public boolean isJailed(UUID id) {
		return jailed.contains(id);
	}

	public void jail(UUID id) {
		if (!jailed.contains(id)) {
			jailed.add(id);
			setDirty();
		}
	}

	public boolean unjail(UUID id) {
		boolean removed = jailed.remove(id);
		if (removed) {
			setDirty();
		}
		return removed;
	}

	/** A saved jail location: dimension id (e.g. {@code minecraft:overworld}) plus position and facing. */
	public record JailPoint(String dimension, double x, double y, double z, float yaw, float pitch) {
	}

	/** A per-player rollup of every report filed against one target. */
	public static final class ReportSummary {
		public final UUID targetId;
		public final String targetName;
		public final List<String> reasons = new ArrayList<>();
		public int count;
		public long latestMs;

		private ReportSummary(UUID targetId, String targetName) {
			this.targetId = targetId;
			this.targetName = targetName;
		}

		private void add(Report report) {
			count++;
			latestMs = Math.max(latestMs, report.timestampMs);
			if (!reasons.contains(report.reason)) {
				reasons.add(report.reason);
			}
		}
	}
}
