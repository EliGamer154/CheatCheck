package com.tradeshop.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * {@code /spawnstash [rarity]} — op-only. Places a random "stash" arrangement of containers (and sometimes
 * a bed) clustered next to the player, like a hidden player base. With no argument the containers are empty;
 * with a rarity of 1-3 the <em>shulker boxes</em> get a small, player-looking loot spread:
 * rarity 1 is below iron, rarity 2 is iron-tier, and rarity 3 is diamond-tier.
 */
public final class SpawnStashCommand {
	private enum Piece {
		BARREL(1), CHEST(1), ENDER_CHEST(1), FURNACE(1), CRAFTING_TABLE(1), SHULKER(1), BED(2);

		final int width;

		Piece(int width) {
			this.width = width;
		}
	}

	private static final Block[] BED_COLORS = {
			Blocks.RED_BED, Blocks.BLUE_BED, Blocks.LIME_BED, Blocks.YELLOW_BED, Blocks.WHITE_BED, Blocks.PURPLE_BED
	};

	private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

	private SpawnStashCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("spawnstash")
				.requires(com.tradeshop.TradeShop::canModerate)
				.executes(context -> spawn(context.getSource(), 0))
				.then(Commands.argument("rarity", IntegerArgumentType.integer(1, 3))
						.executes(context -> spawn(context.getSource(), IntegerArgumentType.getInteger(context, "rarity")))));
	}

	private static int spawn(CommandSourceStack source, int rarity) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ServerLevel level = (ServerLevel) player.level();

		List<Piece> pieces = randomLayout();
		// Loot only lives in shulkers, so make sure a rarity request actually has somewhere to put it.
		if (rarity > 0 && !pieces.contains(Piece.SHULKER)) {
			pieces.add(Piece.SHULKER);
		}
		// Place wider pieces (beds) first so they can still find an adjacent free cell.
		pieces.sort((a, b) -> Integer.compare(b.width, a.width));
		int cellsNeeded = pieces.stream().mapToInt(p -> p.width).sum();

		BlockPos base = player.blockPosition();
		List<Direction> dirs = new ArrayList<>(List.of(HORIZONTAL));
		Collections.shuffle(dirs);

		for (int distance : new int[]{2, 3}) {
			for (Direction dir : dirs) {
				Set<BlockPos> pool = collectCells(level, base.relative(dir, distance));
				if (pool.size() >= cellsNeeded && placeCluster(level, pieces, pool, rarity)) {
					String suffix = rarity > 0 ? " (rarity " + rarity + " loot in the shulkers)" : "";
					player.sendSystemMessage(Component.literal("Spawned a stash next to you" + suffix + ".")
							.withStyle(ChatFormatting.GREEN));
					return Command.SINGLE_SUCCESS;
				}
			}
		}

		source.sendFailure(Component.literal("Not enough clear space around you to spawn a stash. Move somewhere more open."));
		return 0;
	}

	private static List<Piece> randomLayout() {
		List<Piece> p = new ArrayList<>();
		switch (ThreadLocalRandom.current().nextInt(11)) {
			case 0 -> add(p, Piece.BARREL, Piece.CRAFTING_TABLE, Piece.BED);
			case 1 -> add(p, Piece.SHULKER, Piece.BED);
			case 2 -> add(p, Piece.SHULKER);
			case 3 -> add(p, Piece.BARREL);
			case 4 -> add(p, Piece.BARREL, Piece.SHULKER);
			case 5 -> add(p, Piece.CHEST, Piece.BARREL, Piece.SHULKER, Piece.BED);
			case 6 -> add(p, Piece.BARREL, Piece.BARREL, Piece.SHULKER, Piece.SHULKER, Piece.ENDER_CHEST);
			case 7 -> add(p, Piece.CHEST, Piece.CHEST, Piece.FURNACE, Piece.CRAFTING_TABLE);
			case 8 -> add(p, Piece.ENDER_CHEST, Piece.SHULKER, Piece.BARREL, Piece.BED);
			case 9 -> add(p, Piece.SHULKER, Piece.SHULKER, Piece.SHULKER, Piece.BARREL);
			default -> add(p, Piece.BARREL, Piece.CHEST, Piece.FURNACE, Piece.SHULKER, Piece.CRAFTING_TABLE, Piece.BED);
		}
		return p;
	}

	private static void add(List<Piece> list, Piece... pieces) {
		Collections.addAll(list, pieces);
	}

	/** Replaceable, ground-supported cells in a small footprint around {@code anchor}, at the anchor's Y. */
	private static Set<BlockPos> collectCells(ServerLevel level, BlockPos anchor) {
		Set<BlockPos> cells = new HashSet<>();
		for (int dx = -1; dx <= 2; dx++) {
			for (int dz = -1; dz <= 2; dz++) {
				BlockPos pos = anchor.offset(dx, 0, dz);
				boolean replaceable = level.getBlockState(pos).canBeReplaced();
				boolean supported = !level.getBlockState(pos.below()).canBeReplaced();
				if (replaceable && supported) {
					cells.add(pos);
				}
			}
		}
		return cells;
	}

	private static boolean placeCluster(ServerLevel level, List<Piece> pieces, Set<BlockPos> pool, int rarity) {
		List<BlockPos> free = new ArrayList<>(pool);
		Collections.shuffle(free);
		Set<BlockPos> used = new HashSet<>();

		for (Piece piece : pieces) {
			if (piece == Piece.BED) {
				if (!placeBed(level, free, pool, used)) {
					return false;
				}
			} else {
				BlockPos cell = null;
				for (BlockPos candidate : free) {
					if (!used.contains(candidate)) {
						cell = candidate;
						break;
					}
				}
				if (cell == null) {
					return false;
				}
				placeSingle(level, cell, piece, rarity);
				used.add(cell);
			}
		}
		return true;
	}

	private static boolean placeBed(ServerLevel level, List<BlockPos> free, Set<BlockPos> pool, Set<BlockPos> used) {
		for (BlockPos foot : free) {
			if (used.contains(foot)) {
				continue;
			}
			for (Direction dir : HORIZONTAL) {
				BlockPos head = foot.relative(dir);
				if (pool.contains(head) && !used.contains(head)) {
					Block bed = BED_COLORS[ThreadLocalRandom.current().nextInt(BED_COLORS.length)];
					BlockState footState = bed.defaultBlockState()
							.setValue(BedBlock.FACING, dir).setValue(BedBlock.PART, BedPart.FOOT);
					level.setBlockAndUpdate(foot, footState);
					level.setBlockAndUpdate(head, footState.setValue(BedBlock.PART, BedPart.HEAD));
					used.add(foot);
					used.add(head);
					return true;
				}
			}
		}
		return false;
	}

	private static void placeSingle(ServerLevel level, BlockPos pos, Piece piece, int rarity) {
		Block block = switch (piece) {
			case BARREL -> Blocks.BARREL;
			case CHEST -> Blocks.CHEST;
			case ENDER_CHEST -> Blocks.ENDER_CHEST;
			case FURNACE -> Blocks.FURNACE;
			case CRAFTING_TABLE -> Blocks.CRAFTING_TABLE;
			default -> Blocks.SHULKER_BOX;
		};
		level.setBlockAndUpdate(pos, block.defaultBlockState());
		if (piece == Piece.SHULKER && rarity > 0) {
			fillShulker(level, pos, rarity);
		}
	}

	// --- Loot -------------------------------------------------------------

	private static void fillShulker(ServerLevel level, BlockPos pos, int rarity) {
		BlockEntity be = level.getBlockEntity(pos);
		if (!(be instanceof ShulkerBoxBlockEntity) || !(be instanceof Container container)) {
			return;
		}
		ThreadLocalRandom random = ThreadLocalRandom.current();
		List<Supplier<ItemStack>> pool = new ArrayList<>(lootPool(rarity));
		Collections.shuffle(pool);

		// "A bit" of loot: a handful of stacks, scattered into random slots so it reads as player-placed.
		int stacks = Math.min(pool.size(), rand(2, 2 + rarity));
		List<Integer> slots = new ArrayList<>();
		for (int i = 0; i < container.getContainerSize(); i++) {
			slots.add(i);
		}
		Collections.shuffle(slots);

		for (int i = 0; i < stacks; i++) {
			container.setItem(slots.get(i), pool.get(i).get());
		}
		be.setChanged();
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
