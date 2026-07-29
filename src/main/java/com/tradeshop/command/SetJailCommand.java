package com.tradeshop.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.tradeshop.moderation.ModerationState;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** {@code /setjail} — op-only. Saves your current position (and facing) as the jail that {@code /jail} sends players to. */
public final class SetJailCommand {
	private SetJailCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("setjail")
				.requires(com.tradeshop.TradeShop::canModerate)
				.executes(context -> setJail(context.getSource())));
	}

	private static int setJail(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer admin = source.getPlayerOrException();
		ModerationState.get(source.getServer()).setJailPoint(new ModerationState.JailPoint(
				admin.level().dimension().identifier().toString(),
				admin.getX(), admin.getY(), admin.getZ(), admin.getYRot(), admin.getXRot()));
		source.sendSuccess(() -> Component.literal("Jail location set here.").withStyle(ChatFormatting.GREEN), true);
		return Command.SINGLE_SUCCESS;
	}
}
