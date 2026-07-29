package com.tradeshop.mixin;

import com.tradeshop.moderation.SafeModeManager;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Entry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Keeps a safemode admin from showing up grayed/italic in the tab list while they're in spectator. Vanilla
 * broadcasts their SPECTATOR gamemode in the player-info packet, and clients render spectators dimmed; here
 * we rewrite those entries to SURVIVAL on the way out so the name renders normally to <em>other</em> players.
 *
 * <p>Crucially, we never fake the gamemode in the copy sent to the admin themselves — a client reads its own
 * player-info entry to drive its local gamemode, so faking it would turn off spectator noclip (blocks would
 * become solid again). The admin keeps their true SPECTATOR mode and phases through blocks as normal; only
 * the copies sent to everyone else are rewritten. Because the rewrite is recipient-specific, we send a fresh
 * per-recipient packet rather than mutating the shared broadcast instance.
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public class ServerCommonPacketListenerMixin {
	@Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
	private void tradeshop$unGraySpectatorAdmins(Packet<?> packet, CallbackInfo ci) {
		if (!(packet instanceof ClientboundPlayerInfoUpdatePacket infoPacket)) {
			return;
		}
		EnumSet<Action> actions = infoPacket.actions();
		// The gamemode field only matters to the client when one of these actions is present.
		if (!actions.contains(Action.ADD_PLAYER) && !actions.contains(Action.UPDATE_GAME_MODE)) {
			return;
		}

		// The player receiving this packet; we must not fake their own gamemode to themselves.
		UUID recipientId = null;
		if (((Object) this) instanceof ServerGamePacketListenerImpl gameListener && gameListener.player != null) {
			recipientId = gameListener.player.getUUID();
		}

		List<Entry> entries = infoPacket.entries();
		List<Entry> rewritten = new ArrayList<>(entries.size());
		boolean changed = false;
		for (Entry entry : entries) {
			if (entry.gameMode() == GameType.SPECTATOR
					&& !entry.profileId().equals(recipientId)
					&& SafeModeManager.get().isSafeMode(entry.profileId())) {
				rewritten.add(new Entry(entry.profileId(), entry.profile(), entry.listed(), entry.latency(),
						GameType.SURVIVAL, entry.displayName(), entry.showHat(), entry.listOrder(), entry.chatSession()));
				changed = true;
			} else {
				rewritten.add(entry);
			}
		}
		if (!changed) {
			return;
		}

		ClientboundPlayerInfoUpdatePacket replacement =
				new ClientboundPlayerInfoUpdatePacket(EnumSet.copyOf(actions), Collections.<ServerPlayer>emptyList());
		((PlayerInfoUpdatePacketAccessor) replacement).tradeshop$setEntries(rewritten);
		ci.cancel();
		((ServerCommonPacketListenerImpl) (Object) this).send(replacement);
	}
}
