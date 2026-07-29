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
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * {@code /spawnbase [rarity]} — op-only. Carves a random enclosed room (5x5 up to 10x10) with a random shell
 * material, lines the walls with storage and utility blocks facing inward (like a player built it), keeps the
 * middle mostly open, lights it, and usually adds a bed. With a rarity of 1-3 the chests/barrels/shulkers are
 * stocked with the same tiered {@link StashLoot} as {@code /spawnstash}.
 */
public final class SpawnBaseCommand {
	private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

	private static final Block[] SHELLS = {
			Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.COBBLESTONE,
			Blocks.MOSSY_COBBLESTONE, Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_TILES, Blocks.POLISHED_DEEPSLATE,
			Blocks.STONE, Blocks.ANDESITE, Blocks.POLISHED_ANDESITE, Blocks.BLACKSTONE,
			Blocks.POLISHED_BLACKSTONE, Blocks.POLISHED_BLACKSTONE_BRICKS};
	private static final Block[] FLOORS = {
			Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_PLANKS, Blocks.STONE_BRICKS,
			Blocks.DEEPSLATE_TILES, Blocks.COBBLESTONE, Blocks.POLISHED_ANDESITE};
	private static final Block[] LIGHTS = {Blocks.GLOWSTONE, Blocks.SEA_LANTERN};
	private static final Block[] BED_COLORS = {
			Blocks.RED_BED, Blocks.BLUE_BED, Blocks.LIME_BED, Blocks.YELLOW_BED, Blocks.WHITE_BED, Blocks.CYAN_BED};

	/** What lines the walls — heavily weighted toward storage, with utility stations mixed in. */
	private static final Block[] WALL = {
			Blocks.CHEST, Blocks.CHEST, Blocks.CHEST, Blocks.CHEST, Blocks.BARREL, Blocks.BARREL, Blocks.BARREL,
			Blocks.SHULKER_BOX, Blocks.FURNACE, Blocks.BLAST_FURNACE, Blocks.SMOKER, Blocks.CRAFTING_TABLE,
			Blocks.ENDER_CHEST, Blocks.ANVIL, Blocks.BOOKSHELF, Blocks.CHISELED_BOOKSHELF, Blocks.BREWING_STAND,
			Blocks.CARTOGRAPHY_TABLE, Blocks.SMITHING_TABLE, Blocks.LOOM, Blocks.GRINDSTONE, Blocks.STONECUTTER,
			Blocks.LECTERN, Blocks.CAULDRON, Blocks.COMPOSTER, Blocks.JUKEBOX, Blocks.NOTE_BLOCK};

	private SpawnBaseCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("spawnbase")
				.requires(com.tradeshop.TradeShop::canModerate)
				.executes(context -> spawn(context.getSource(), 0))
				.then(Commands.argument("rarity", IntegerArgumentType.integer(1, 3))
						.executes(context -> spawn(context.getSource(), IntegerArgumentType.getInteger(context, "rarity")))));
	}

	private static int spawn(CommandSourceStack source, int rarity) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ServerLevel level = (ServerLevel) player.level();
		ThreadLocalRandom random = ThreadLocalRandom.current();

		int size = 5 + random.nextInt(6);              // 5..10
		int interiorHeight = size >= 8 ? 4 : 3;
		int height = interiorHeight + 2;

		Block shell = SHELLS[random.nextInt(SHELLS.length)];
		Block floor = random.nextInt(100) < 50 ? shell : FLOORS[random.nextInt(FLOORS.length)];

		BlockPos base = player.blockPosition();
		Direction dir = HORIZONTAL[random.nextInt(HORIZONTAL.length)];
		int ox = base.getX() + dir.getStepX() * size - size / 2;
		int oz = base.getZ() + dir.getStepZ() * size - size / 2;
		int floorY = base.getY() - 1;

		carveShell(level, ox, floorY, oz, size, height, shell, floor);
		placeLights(level, ox, floorY, oz, size, height, random);

		int y = floorY + 1;
		List<BlockPos> interior = new ArrayList<>();
		Set<BlockPos> interiorSet = new HashSet<>();
		Set<BlockPos> perimeter = new HashSet<>();
		for (int dx = 1; dx < size - 1; dx++) {
			for (int dz = 1; dz < size - 1; dz++) {
				BlockPos pos = new BlockPos(ox + dx, y, oz + dz);
				interior.add(pos);
				interiorSet.add(pos);
				if (dx == 1 || dx == size - 2 || dz == 1 || dz == size - 2) {
					perimeter.add(pos);
				}
			}
		}
		Collections.shuffle(interior);
		Set<BlockPos> used = new HashSet<>();

		// Beds first (so they have room). Most bases get one; big ones sometimes two.
		if (random.nextInt(100) < 85) {
			int beds = size >= 8 && random.nextBoolean() ? 2 : 1;
			for (int i = 0; i < beds; i++) {
				placeBed(level, interior, interiorSet, used, random);
			}
		}

		// Line the walls with storage/stations facing inward, leaving gaps; stock containers if a rarity was given.
		// Some spots become double chests, and 32% of those get a second double chest stacked on top.
		int placed = 0;
		for (int dx = 1; dx < size - 1; dx++) {
			for (int dz = 1; dz < size - 1; dz++) {
				BlockPos pos = new BlockPos(ox + dx, y, oz + dz);
				if (!perimeter.contains(pos) || used.contains(pos) || random.nextDouble() > 0.55) {
					continue; // gap
				}
				Direction in = inward(dx, dz, size);
				BlockPos partner = pos.relative(in.getClockWise());
				if (random.nextInt(100) < 35 && perimeter.contains(partner) && !used.contains(partner)) {
					placeDoubleChest(level, pos, partner, in, rarity);
					used.add(pos);
					used.add(partner);
					placed += 2;
					if (random.nextInt(100) < 32) {
						placeDoubleChest(level, pos.above(), partner.above(), in, rarity);
						placed += 2;
					}
				} else {
					placeWall(level, pos, WALL[random.nextInt(WALL.length)], in, rarity);
					used.add(pos);
					placed++;
				}
			}
		}

		String suffix = rarity > 0 ? " (rarity " + rarity + " loot)" : "";
		player.sendSystemMessage(Component.literal("Spawned a " + size + "x" + size + " base with "
				+ placed + " furnishings" + suffix + ".").withStyle(ChatFormatting.GREEN));
		return Command.SINGLE_SUCCESS;
	}

	private static Direction inward(int dx, int dz, int size) {
		if (dx == 1) {
			return Direction.EAST;
		}
		if (dx == size - 2) {
			return Direction.WEST;
		}
		if (dz == 1) {
			return Direction.SOUTH;
		}
		return Direction.NORTH;
	}

	private static void placeWall(ServerLevel level, BlockPos pos, Block block, Direction inward, int rarity) {
		level.setBlockAndUpdate(pos, faced(block, inward));
		if (rarity > 0 && (block == Blocks.CHEST || block == Blocks.BARREL || block == Blocks.SHULKER_BOX)) {
			StashLoot.fill(level.getBlockEntity(pos), rarity);
		}
	}

	/** Places a connected double chest: {@code left} + {@code right}, both facing inward. */
	private static void placeDoubleChest(ServerLevel level, BlockPos left, BlockPos right, Direction inward, int rarity) {
		BlockState base = Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, inward);
		level.setBlockAndUpdate(left, base.setValue(BlockStateProperties.CHEST_TYPE, ChestType.LEFT));
		level.setBlockAndUpdate(right, base.setValue(BlockStateProperties.CHEST_TYPE, ChestType.RIGHT));
		if (rarity > 0) {
			StashLoot.fill(level.getBlockEntity(left), rarity);
			StashLoot.fill(level.getBlockEntity(right), rarity);
		}
	}

	private static void carveShell(ServerLevel level, int ox, int floorY, int oz, int size, int height,
			Block shell, Block floor) {
		for (int dx = 0; dx < size; dx++) {
			for (int dz = 0; dz < size; dz++) {
				for (int dy = 0; dy < height; dy++) {
					BlockPos pos = new BlockPos(ox + dx, floorY + dy, oz + dz);
					boolean edge = dx == 0 || dx == size - 1 || dz == 0 || dz == size - 1 || dy == 0 || dy == height - 1;
					if (dy == 0) {
						level.setBlockAndUpdate(pos, floor.defaultBlockState());
					} else {
						level.setBlockAndUpdate(pos, edge ? shell.defaultBlockState() : Blocks.AIR.defaultBlockState());
					}
				}
			}
		}
	}

	private static void placeLights(ServerLevel level, int ox, int floorY, int oz, int size, int height,
			ThreadLocalRandom random) {
		Block light = LIGHTS[random.nextInt(LIGHTS.length)];
		int ceilY = floorY + height - 1;
		int step = size >= 8 ? 3 : 2;
		for (int dx = 1; dx < size - 1; dx += step) {
			for (int dz = 1; dz < size - 1; dz += step) {
				level.setBlockAndUpdate(new BlockPos(ox + dx, ceilY, oz + dz), light.defaultBlockState());
			}
		}
	}

	private static void placeBed(ServerLevel level, List<BlockPos> cells, Set<BlockPos> pool, Set<BlockPos> used,
			ThreadLocalRandom random) {
		for (BlockPos foot : cells) {
			if (used.contains(foot)) {
				continue;
			}
			for (Direction dir : HORIZONTAL) {
				BlockPos head = foot.relative(dir);
				if (pool.contains(head) && !used.contains(head)) {
					Block bed = BED_COLORS[random.nextInt(BED_COLORS.length)];
					BlockState footState = bed.defaultBlockState()
							.setValue(BedBlock.FACING, dir).setValue(BedBlock.PART, BedPart.FOOT);
					level.setBlockAndUpdate(foot, footState);
					level.setBlockAndUpdate(head, footState.setValue(BedBlock.PART, BedPart.HEAD));
					used.add(foot);
					used.add(head);
					return;
				}
			}
		}
	}

	/** Default state, faced toward {@code inward} if the block supports a horizontal facing (chests, furnaces...). */
	private static BlockState faced(Block block, Direction inward) {
		BlockState state = block.defaultBlockState();
		if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
			return state.setValue(BlockStateProperties.HORIZONTAL_FACING, inward);
		}
		return state;
	}
}
