package com.tradeshop.command;

import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Tiered, player-looking loot for containers spawned by {@code /spawnstash} and {@code /spawnbase}. Rarity 1
 * is below iron, 2 iron-tier, 3 diamond-tier; items are partial stacks and worn tools scattered into random
 * slots so it reads as a real player's stash.
 */
public final class StashLoot {
	private StashLoot() {
	}

	/** Fills a container block entity with a small tiered loot spread. No-op for rarity &lt;= 0 or non-containers. */
	public static void fill(BlockEntity blockEntity, int rarity) {
		if (rarity <= 0 || !(blockEntity instanceof Container container)) {
			return;
		}
		List<Supplier<ItemStack>> pool = new ArrayList<>(lootPool(rarity));
		Collections.shuffle(pool);

		int stacks = Math.min(pool.size(), rand(2, 2 + rarity));
		List<Integer> slots = new ArrayList<>();
		for (int i = 0; i < container.getContainerSize(); i++) {
			slots.add(i);
		}
		Collections.shuffle(slots);

		for (int i = 0; i < stacks; i++) {
			container.setItem(slots.get(i), pool.get(i).get());
		}
		blockEntity.setChanged();
	}

	private static List<Supplier<ItemStack>> lootPool(int rarity) {
		return switch (rarity) {
			case 1 -> List.of(
					count(Items.COBBLESTONE, 16, 47), count(Items.COAL, 4, 15), count(Items.TORCH, 8, 24),
					count(Items.OAK_PLANKS, 12, 40), count(Items.BREAD, 2, 7), count(Items.APPLE, 1, 4),
					count(Items.ARROW, 5, 20), count(Items.DIRT, 12, 32), count(Items.STICK, 4, 12),
					tool(Items.STONE_PICKAXE), tool(Items.STONE_SWORD), tool(Items.STONE_AXE),
					tool(Items.WOODEN_PICKAXE), tool(Items.LEATHER_CHESTPLATE), tool(Items.LEATHER_BOOTS));
			case 2 -> List.of(
					count(Items.IRON_INGOT, 3, 12), count(Items.COOKED_BEEF, 4, 12), count(Items.ENDER_PEARL, 1, 3),
					count(Items.REDSTONE, 8, 24), count(Items.IRON_BLOCK, 1, 2), count(Items.COAL, 8, 24),
					count(Items.OAK_LOG, 8, 16), count(Items.GOLDEN_CARROT, 2, 6), single(Items.WATER_BUCKET),
					tool(Items.IRON_PICKAXE), tool(Items.IRON_SWORD), tool(Items.IRON_AXE),
					tool(Items.IRON_CHESTPLATE), tool(Items.IRON_HELMET), tool(Items.SHIELD));
			default -> List.of(
					count(Items.DIAMOND, 2, 8), count(Items.GOLDEN_APPLE, 1, 3), count(Items.ENDER_PEARL, 2, 8),
					count(Items.OBSIDIAN, 2, 8), count(Items.DIAMOND_BLOCK, 1, 1), count(Items.EXPERIENCE_BOTTLE, 4, 16),
					count(Items.COOKED_BEEF, 8, 16), count(Items.GUNPOWDER, 4, 12), single(Items.ENCHANTED_BOOK),
					tool(Items.DIAMOND_PICKAXE), tool(Items.DIAMOND_SWORD), tool(Items.DIAMOND_CHESTPLATE),
					tool(Items.DIAMOND_LEGGINGS), tool(Items.DIAMOND_HELMET), tool(Items.DIAMOND_BOOTS));
		};
	}

	private static Supplier<ItemStack> count(Item item, int min, int max) {
		return () -> new ItemStack(item, rand(min, max));
	}

	private static Supplier<ItemStack> single(Item item) {
		return () -> new ItemStack(item);
	}

	/** A single tool/armor piece with some wear, so it looks used rather than freshly crafted. */
	private static Supplier<ItemStack> tool(Item item) {
		return () -> {
			ItemStack stack = new ItemStack(item);
			int max = stack.getMaxDamage();
			if (stack.isDamageableItem() && max > 0) {
				stack.setDamageValue(rand(max / 10, max * 3 / 5));
			}
			return stack;
		};
	}

	private static int rand(int min, int max) {
		return max <= min ? min : ThreadLocalRandom.current().nextInt(min, max + 1);
	}
}
