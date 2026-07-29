package com.tradeshop.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.tradeshop.moderation.WatchTools;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.List;

/**
 * {@code /vanish} — op-only. Toggles going invisible and disappearing from the tab list, so you can watch a
 * cheater without them knowing you're around. Invisibility hides your body; the player-info removal (kept up
 * by {@code ServerCommonPacketListenerMixin}) hides you from everyone else's tab. Combined with spectator
 * from {@code /cheatcheck}, you leave no trace.
 */
public final class VanishCommand {
	private VanishCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("vanish")
				.requires(com.tradeshop.TradeShop::canModerate)
				.executes(context -> toggle(context.getSource())));
	}

	private static int toggle(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer admin = source.getPlayerOrException();
		boolean nowVanished = WatchTools.get().toggleVanish(admin.getUUID());

		if (nowVanished) {
			admin.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY,
					MobEffectInstance.INFINITE_DURATION, 0, false, false, false));
			broadcastToOthers(admin, new ClientboundPlayerInfoRemovePacket(List.of(admin.getUUID())));
			admin.sendSystemMessage(Component.literal("You are now vanished — invisible and hidden from the tab list.")
					.withStyle(ChatFormatting.GREEN));
		} else {
			admin.removeEffect(MobEffects.INVISIBILITY);
			broadcastToOthers(admin, ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(admin)));
			admin.sendSystemMessage(Component.literal("You are no longer vanished.").withStyle(ChatFormatting.YELLOW));
		}
		return Command.SINGLE_SUCCESS;
	}

	private static void broadcastToOthers(ServerPlayer admin, net.minecraft.network.protocol.Packet<?> packet) {
		for (ServerPlayer other : admin.level().getServer().getPlayerList().getPlayers()) {
			if (!other.getUUID().equals(admin.getUUID())) {
				other.connection.send(packet);
			}
		}
	}
}
