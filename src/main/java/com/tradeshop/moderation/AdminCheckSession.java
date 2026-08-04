package com.tradeshop.moderation;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.GameType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

/**
 * Tracks a custom admin's active check on a suspect and enforces its restrictions: a boss-bar countdown
 * (per-level duration), hidden coordinates (F3 reduced debug), a forced spectator gamemode, and an
 * auto-return when the timer runs out. Real ops never get a session, so they're unrestricted.
 */
public final class AdminCheckSession {
	/** Vanilla entity events that toggle the client's reduced-debug (no-coordinates) HUD. */
	private static final byte ENABLE_REDUCED_DEBUG = 22;
	private static final byte DISABLE_REDUCED_DEBUG = 23;

	private static final AdminCheckSession INSTANCE = new AdminCheckSession();

	private final Map<UUID, Session> sessions = new HashMap<>();

	private AdminCheckSession() {
	}

	public static AdminCheckSession get() {
		return INSTANCE;
	}

	public boolean isChecking(UUID adminId) {
		return sessions.containsKey(adminId);
	}

	/** Starts a timed, restricted check for the admin. */
	public void start(ServerPlayer admin, AdminLevel level) {
		clear(admin); // drop any prior session's bar
		int seconds = level.timerMinutes * 60;
		ServerBossEvent bar = new ServerBossEvent(UUID.randomUUID(),
				timerName(seconds), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
		bar.addPlayer(admin);
		admin.connection.send(new ClientboundEntityEventPacket(admin, ENABLE_REDUCED_DEBUG));
		sessions.put(admin.getUUID(), new Session(bar, seconds, seconds));
	}

	/** Tears down the session's HUD/coord state (boss bar + reduced debug) without returning the admin. */
	public void clear(ServerPlayer admin) {
		Session session = sessions.remove(admin.getUUID());
		if (session != null) {
			session.bar.removeAllPlayers();
			admin.connection.send(new ClientboundEntityEventPacket(admin, DISABLE_REDUCED_DEBUG));
		}
	}

	public void tick(MinecraftServer server, boolean secondTick) {
		if (sessions.isEmpty()) {
			return;
		}
		List<ServerPlayer> expired = new ArrayList<>();
		for (Entry<UUID, Session> entry : new ArrayList<>(sessions.entrySet())) {
			ServerPlayer admin = server.getPlayerList().getPlayer(entry.getKey());
			if (admin == null) {
				entry.getValue().bar.removeAllPlayers();
				sessions.remove(entry.getKey());
				continue;
			}
			// Gamemode lock: keep them a spectator for the whole check.
			if (admin.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
				admin.setGameMode(GameType.SPECTATOR);
			}
			if (secondTick) {
				Session session = entry.getValue();
				session.remaining--;
				session.bar.setName(timerName(session.remaining));
				session.bar.setProgress(Math.max(0f, (float) session.remaining / session.total));
				if (session.remaining <= 0) {
					expired.add(admin);
				}
			}
		}
		for (ServerPlayer admin : expired) {
			admin.sendSystemMessage(Component.literal("Your check time is up — returning you.")
					.withStyle(ChatFormatting.YELLOW));
			ModerationService.endWatch(admin);
		}
	}

	private static Component timerName(int seconds) {
		int shown = Math.max(0, seconds);
		return Component.literal(String.format("§cCheck ends in %d:%02d", shown / 60, shown % 60));
	}

	private static final class Session {
		final ServerBossEvent bar;
		final int total;
		int remaining;

		Session(ServerBossEvent bar, int remaining, int total) {
			this.bar = bar;
			this.remaining = remaining;
			this.total = total;
		}
	}
}
