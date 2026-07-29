package com.tradeshop;

import com.tradeshop.command.CheatCheckCommand;
import com.tradeshop.command.OffendCommand;
import com.tradeshop.command.PardonCommand;
import com.tradeshop.command.ReportCommand;
import com.tradeshop.command.RtpCommand;
import com.tradeshop.command.SafeModeCommand;
import com.tradeshop.command.ShopCommand;
import com.tradeshop.command.SpawnStashCommand;
import com.tradeshop.command.TempBanCommand;
import com.tradeshop.config.TradeShopConfig;
import com.tradeshop.moderation.ModerationEvents;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.players.NameAndId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TradeShop implements ModInitializer {
	public static final String MOD_ID = "tradeshop";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		TradeShopConfig.load();
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> {
			ShopCommand.register(dispatcher);
			RtpCommand.register(dispatcher);
			ReportCommand.register(dispatcher);
			CheatCheckCommand.register(dispatcher);
			SafeModeCommand.register(dispatcher);
			OffendCommand.register(dispatcher);
			TempBanCommand.register(dispatcher);
			PardonCommand.register(dispatcher);
			SpawnStashCommand.register(dispatcher);
		});
		ModerationEvents.register();
		LOGGER.info("TradeShop initialized");
	}

	public static boolean isOp(ServerPlayer player) {
		return player.level().getServer().getPlayerList().isOp(new NameAndId(player.getGameProfile()));
	}

	/** True when the command source may use the moderation commands (operator / gamemaster level). */
	public static boolean canModerate(CommandSourceStack source) {
		return source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
	}
}
