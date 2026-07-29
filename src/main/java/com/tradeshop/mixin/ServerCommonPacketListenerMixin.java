package com.tradeshop.mixin;

import com.tradeshop.moderation.SafeModeManager;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Entry;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Keeps a safemode admin from showing up grayed/italic in the tab list while they're in spectator. Vanilla
 * broadcasts their SPECTATOR gamemode in the player-info packet, and clients render spectators dimmed; here
 * we rewrite those entries to SURVIVAL on the way out so the name renders normally. Server-side only — the
 * admin stays a real spectator; we just change what the packet reports.
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public class ServerCommonPacketListenerMixin {
	@Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"))
	private void tradeshop$unGraySpectatorAdmins(Packet<?> packet, CallbackInfo ci) {
		if (!(packet instanceof ClientboundPlayerInfoUpdatePacket infoPacket)) {
			return;
		}
		EnumSet<Action> actions = infoPacket.actions();
		// The gamemode field only matters to the client when one of these actions is present.
		if (!actions.contains(Action.ADD_PLAYER) && !actions.contains(Action.UPDATE_GAME_MODE)) {
			return;
		}

		List<Entry> entries = infoPacket.entries();
		List<Entry> rewritten = new ArrayList<>(entries.size());
		boolean changed = false;
		for (Entry entry : entries) {
			if (entry.gameMode() == GameType.SPECTATOR && SafeModeManager.get().isSafeMode(entry.profileId())) {
				rewritten.add(new Entry(entry.profileId(), entry.profile(), entry.listed(), entry.latency(),
						GameType.SURVIVAL, entry.displayName(), entry.showHat(), entry.listOrder(), entry.chatSession()));
				changed = true;
			} else {
				rewritten.add(entry);
			}
		}
		if (changed) {
			((PlayerInfoUpdatePacketAccessor) infoPacket).tradeshop$setEntries(rewritten);
		}
	}
}
