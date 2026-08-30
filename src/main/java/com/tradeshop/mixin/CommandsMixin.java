package com.tradeshop.mixin;

import com.mojang.brigadier.ParseResults;
import com.tradeshop.moderation.CombatTracker;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/** Blocks escape/teleport commands while a player is combat-tagged (so they can't run from a fight). */
@Mixin(Commands.class)
public class CommandsMixin {
	private static final Set<String> BLOCKED_IN_COMBAT = Set.of("tpa", "tpahere", "tpaccept", "home", "back", "rtp");

	@Inject(method = "performCommand", at = @At("HEAD"), cancellable = true)
	private void tradeshop$blockInCombat(ParseResults<CommandSourceStack> parseResults, String command, CallbackInfo ci) {
		if (!(parseResults.getContext().getSource().getEntity() instanceof ServerPlayer player)) {
			return;
		}
		if (!CombatTracker.get().isInCombat(player.getUUID())) {
			return;
		}
		String name = firstWord(command);
		if (BLOCKED_IN_COMBAT.contains(name)) {
			player.sendSystemMessage(Component.literal("You can't use that command while in combat.")
					.withStyle(ChatFormatting.RED));
			ci.cancel();
		}
	}

	private static String firstWord(String command) {
		String trimmed = command.startsWith("/") ? command.substring(1) : command;
		int space = trimmed.indexOf(' ');
		return (space >= 0 ? trimmed.substring(0, space) : trimmed).toLowerCase();
	}
}
