package com.tradeshop.mixin;

import com.tradeshop.moderation.ModerationService;
import com.tradeshop.moderation.ModerationState;
import com.tradeshop.moderation.TempBan;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.SocketAddress;
import java.util.Optional;

/**
 * Enforces the mod's temp-bans at login. {@code canPlayerLogin} returns a non-null {@link Component} to
 * refuse a connection; we short-circuit it for anyone with an active {@link TempBan}, showing the reason
 * and remaining time. Expired bans are pruned by {@code activeBan} so they let the player back in.
 */
@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
	@Shadow
	public abstract MinecraftServer getServer();

	@Inject(method = "canPlayerLogin", at = @At("HEAD"), cancellable = true)
	private void tradeshop$denyBanned(SocketAddress address, NameAndId nameAndId, CallbackInfoReturnable<Component> cir) {
		ModerationState state = ModerationState.get(getServer());
		Optional<TempBan> ban = state.activeBan(nameAndId.id());
		ban.ifPresent(tempBan -> cir.setReturnValue(ModerationService.banScreen(tempBan)));
	}
}
