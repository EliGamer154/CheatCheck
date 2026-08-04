package com.tradeshop.command;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Remembers, per player, the blocks each {@code /spawnores|stash|base} overwrote (and their previous state)
 * so the matching {@code /unspawn...} can restore them. Only the most recent spawn of each kind is kept.
 * In-memory (resets on restart).
 */
public final class SpawnHistory {
	public enum Kind {
		ORES, STASH, BASE
	}

	public record Change(BlockPos pos, BlockState previous) {
	}

	private record Snapshot(ServerLevel level, List<Change> changes) {
	}

	private static final SpawnHistory INSTANCE = new SpawnHistory();

	private final Map<UUID, Map<Kind, Snapshot>> history = new HashMap<>();

	private SpawnHistory() {
	}

	public static SpawnHistory get() {
		return INSTANCE;
	}

	/** Stores the changes from a spawn, replacing any prior spawn of the same kind by this player. */
	public void record(UUID player, Kind kind, ServerLevel level, List<Change> changes) {
		history.computeIfAbsent(player, k -> new EnumMap<>(Kind.class)).put(kind, new Snapshot(level, List.copyOf(changes)));
	}

	/** Restores the player's most recent spawn of {@code kind}. Returns how many blocks were restored (0 if none). */
	public int undo(UUID player, Kind kind) {
		Map<Kind, Snapshot> byKind = history.get(player);
		if (byKind == null) {
			return 0;
		}
		Snapshot snapshot = byKind.remove(kind);
		if (snapshot == null) {
			return 0;
		}
		for (Change change : snapshot.changes()) {
			snapshot.level().setBlockAndUpdate(change.pos(), change.previous());
		}
		return snapshot.changes().size();
	}
}
