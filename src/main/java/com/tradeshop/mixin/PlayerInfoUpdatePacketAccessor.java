package com.tradeshop.mixin;

import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** Exposes a setter for the otherwise-final {@code entries} list so the send mixin can rewrite it. */
@Mixin(ClientboundPlayerInfoUpdatePacket.class)
public interface PlayerInfoUpdatePacketAccessor {
	@Mutable
	@Accessor("entries")
	void tradeshop$setEntries(List<ClientboundPlayerInfoUpdatePacket.Entry> entries);
}
