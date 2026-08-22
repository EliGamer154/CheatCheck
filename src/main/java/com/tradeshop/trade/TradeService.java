package com.tradeshop.trade;

import com.tradeshop.data.Listing;
import com.tradeshop.data.ListingStatus;
import com.tradeshop.data.Offer;
import com.tradeshop.data.OfferStatus;
import com.tradeshop.data.ShopState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;

public final class TradeService {
	private TradeService() {
	}

	public enum Result {
		SUCCESS,
		MISSING_ITEMS,
		OFFLINE,
		NOT_FOUND
	}

	/**
	 * Finalizes a trade. Listing items are held in escrow (already removed from
	 * the seller's inventory when the listing was created), so only the buyer's
	 * offered items need to still be present here - the seller's side can't fail.
	 */
	public static Result buyerConfirm(MinecraftServer server, ShopState state, UUID offerId) {
		Offer offer = state.findOffer(offerId).orElse(null);
		if (offer == null || offer.status != OfferStatus.SELLER_ACCEPTED) {
			return Result.NOT_FOUND;
		}
		Listing listing = state.findListing(offer.listingId).orElse(null);
		if (listing == null) {
			return Result.NOT_FOUND;
		}

		ServerPlayer seller = server.getPlayerList().getPlayer(listing.ownerId);
		ServerPlayer buyer = server.getPlayerList().getPlayer(offer.offererId);
		if (seller == null || buyer == null) {
			return Result.OFFLINE;
		}

		if (!hasAll(buyer, offer.items)) {
			offer.status = OfferStatus.FAILED;
			listing.status = ListingStatus.OPEN;
			state.setDirty();
			seller.sendSystemMessage(Component.literal("Trade with " + buyer.getGameProfile().name() + " failed - they no longer have the offered items. Your listing is open again."));
			buyer.sendSystemMessage(Component.literal("Trade failed - you no longer have the items you offered."));
			return Result.MISSING_ITEMS;
		}

		removeAll(buyer, offer.items);
		giveAll(buyer, listing.items);
		giveAll(seller, offer.items);

		offer.status = OfferStatus.COMPLETED;
		listing.status = ListingStatus.CLOSED;
		state.setDirty();

		seller.sendSystemMessage(Component.literal("Trade with " + buyer.getGameProfile().name() + " completed!"));
		buyer.sendSystemMessage(Component.literal("Trade completed!"));

		return Result.SUCCESS;
	}

	public static boolean hasAll(ServerPlayer player, List<ItemStack> required) {
		for (ItemStack req : required) {
			if (!hasEnough(player, req)) {
				return false;
			}
		}
		return true;
	}

	private static boolean hasEnough(ServerPlayer player, ItemStack required) {
		int need = required.getCount();
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (matches(stack, required)) {
				need -= stack.getCount();
				if (need <= 0) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean matches(ItemStack a, ItemStack b) {
		return !a.isEmpty() && ItemStack.isSameItemSameComponents(a, b);
	}

	public static void removeAll(ServerPlayer player, List<ItemStack> required) {
		Inventory inventory = player.getInventory();
		for (ItemStack req : required) {
			int remaining = req.getCount();
			for (int i = 0; i < inventory.getContainerSize(); i++) {
				if (remaining <= 0) {
					break;
				}
				ItemStack stack = inventory.getItem(i);
				if (matches(stack, req)) {
					int take = Math.min(remaining, stack.getCount());
					stack.shrink(take);
					remaining -= take;
				}
			}
		}
	}

	public static void giveAll(ServerPlayer player, List<ItemStack> items) {
		for (ItemStack template : items) {
			ItemStack toGive = template.copy();
			if (!player.getInventory().add(toGive)) {
				player.drop(toGive, false);
			}
		}
	}
}
