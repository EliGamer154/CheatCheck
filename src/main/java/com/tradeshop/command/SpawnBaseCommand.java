package com.tradeshop.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * {@code /spawnbase} — op-only. Carves a random enclosed room (5x5 up to 10x10) next to the player with a
 * randomly-chosen shell material, lines the walls with storage (chests/barrels facing inward), lights it,
 * and fills the middle according to a random style (storage vault, full base, cluttered stash, enchant room).
 * Different every time.
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
	private static final Block[] WALL_STORAGE = {
			Blocks.CHEST, Blocks.CHEST, Blocks.BARREL, Blocks.BARREL, Blocks.CHEST, Blocks.SHULKER_BOX};
	private static final Block[] STATIONS = {
			Blocks.CRAFTING_TABLE, Blocks.FURNACE, Blocks.BLAST_FURNACE, Blocks.SMOKER, Blocks.ANVIL,
			Blocks.CARTOGRAPHY_TABLE, Blocks.SMITHING_TABLE, Blocks.LOOM, Blocks.GRINDSTONE, Blocks.STONECUTTER,
			Blocks.LECTERN, Blocks.CAULDRON, Blocks.COMPOSTER, Blocks.ENDER_CHEST, Blocks.JUKEBOX, Blocks.NOTE_BLOCK,
			Blocks.CHEST, Blocks.BARREL};
	private static final Block[] BED_COLORS = {
			Blocks.RED_BED, Blocks.BLUE_BED, Blocks.LIME_BED, Blocks.YELLOW_BED, Blocks.WHITE_BED, Blocks.CYAN_BED};

	private enum Style {STORAGE_VAULT, FULL_BASE, CLUTTERED_STASH, ENCHANT_ROOM}

	private SpawnBaseCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("spawnbase")
				.requires(com.tradeshop.TradeShop::canModerate)
				.executes(context -> spawn(context.getSource())));
	}

	private static int spawn(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ServerLevel level = (ServerLevel) player.level();
		ThreadLocalRandom random = ThreadLocalRandom.current();

		int size = 5 + random.nextInt(6);              // 5..10
		int interiorHeight = size >= 8 ? 4 : 3;
		int height = interiorHeight + 2;               // floor + interior + ceiling
		Style style = Style.values()[random.nextInt(Style.values().length)];

		Block shell = SHELLS[random.nextInt(SHELLS.length)];
		Block floor = random.nextInt(100) < 50 ? shell : FLOORS[random.nextInt(FLOORS.length)];

		BlockPos base = player.blockPosition();
		Direction dir = HORIZONTAL[random.nextInt(HORIZONTAL.length)];
		int originX = base.getX() + dir.getStepX() * size - size / 2;
		int originZ = base.getZ() + dir.getStepZ() * size - size / 2;
		int floorY = base.getY() - 1;

		carveShell(level, originX, floorY, originZ, size, height, shell, floor);
		placeLights(level, originX, floorY, originZ, size, height, random);

		List<BlockPos> used = new ArrayList<>();
		lineWalls(level, originX, floorY, originZ, size, wallDensity(style), used, random);
		fillCenter(level, originX, floorY, originZ, size, style, used, random);

		player.sendSystemMessage(Component.literal("Spawned a " + size + "x" + size + " "
				+ style.name().toLowerCase().replace('_', ' ') + ".").withStyle(ChatFormatting.GREEN));
		return Command.SINGLE_SUCCESS;
	}

	private static double wallDensity(Style style) {
		return switch (style) {
			case STORAGE_VAULT -> 0.9;
			case FULL_BASE -> 0.55;
			case ENCHANT_ROOM -> 0.5;
			case CLUTTERED_STASH -> 0.7;
		};
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

	/** Places storage blocks along the interior walls, facing into the room. */
	private static void lineWalls(ServerLevel level, int ox, int floorY, int oz, int size, double density,
			List<BlockPos> used, ThreadLocalRandom random) {
		int y = floorY + 1;
		for (int dx = 1; dx < size - 1; dx++) {
			for (int dz = 1; dz < size - 1; dz++) {
				boolean perimeter = dx == 1 || dx == size - 2 || dz == 1 || dz == size - 2;
				if (!perimeter || random.nextDouble() > density) {
					continue;
				}
				Direction inward;
				if (dx == 1) {
					inward = Direction.EAST;
				} else if (dx == size - 2) {
					inward = Direction.WEST;
				} else if (dz == 1) {
					inward = Direction.SOUTH;
				} else {
					inward = Direction.NORTH;
				}
				BlockPos pos = new BlockPos(ox + dx, y, oz + dz);
				level.setBlockAndUpdate(pos, faced(WALL_STORAGE[random.nextInt(WALL_STORAGE.length)], inward));
				used.add(pos);
			}
		}
	}

	private static void fillCenter(ServerLevel level, int ox, int floorY, int oz, int size, Style style,
			List<BlockPos> used, ThreadLocalRandom random) {
		int y = floorY + 1;
		List<BlockPos> cells = new ArrayList<>();
		for (int dx = 2; dx < size - 2; dx++) {
			for (int dz = 2; dz < size - 2; dz++) {
				BlockPos pos = new BlockPos(ox + dx, y, oz + dz);
				if (!used.contains(pos)) {
					cells.add(pos);
				}
			}
		}
		Collections.shuffle(cells);
		if (cells.isEmpty()) {
			return;
		}

		// A bed in most styles.
		if (style != Style.ENCHANT_ROOM) {
			placeBed(level, cells, used, random);
		}

		switch (style) {
			case ENCHANT_ROOM -> {
				// Enchanting table ringed by bookshelves.
				BlockPos c = cells.get(0);
				level.setBlockAndUpdate(c, Blocks.ENCHANTING_TABLE.defaultBlockState());
				used.add(c);
				for (Direction d : Direction.values()) {
					if (d.getAxis().isHorizontal()) {
						BlockPos shelf = c.relative(d, 2);
						level.setBlockAndUpdate(shelf, (random.nextBoolean() ? Blocks.BOOKSHELF : Blocks.CHISELED_BOOKSHELF)
								.defaultBlockState());
					}
				}
				scatter(level, cells, used, random, 2, Blocks.BREWING_STAND, Blocks.ANVIL, Blocks.LECTERN);
			}
			case FULL_BASE -> scatter(level, cells, used, random, Math.max(3, cells.size() / 3),
					STATIONS);
			case CLUTTERED_STASH -> scatter(level, cells, used, random, Math.max(4, cells.size() * 2 / 3),
					STATIONS);
			case STORAGE_VAULT -> scatter(level, cells, used, random, Math.max(1, cells.size() / 5),
					Blocks.CRAFTING_TABLE, Blocks.ENDER_CHEST, Blocks.CHEST);
		}
	}

	private static void scatter(ServerLevel level, List<BlockPos> cells, List<BlockPos> used, ThreadLocalRandom random,
			int count, Block... palette) {
		int placed = 0;
		for (BlockPos cell : cells) {
			if (placed >= count) {
				break;
			}
			if (!used.contains(cell)) {
				level.setBlockAndUpdate(cell, palette[random.nextInt(palette.length)].defaultBlockState());
				used.add(cell);
				placed++;
			}
		}
	}

	private static void placeBed(ServerLevel level, List<BlockPos> cells, List<BlockPos> used, ThreadLocalRandom random) {
		for (BlockPos foot : cells) {
			if (used.contains(foot)) {
				continue;
			}
			for (Direction dir : HORIZONTAL) {
				BlockPos head = foot.relative(dir);
				if (cells.contains(head) && !used.contains(head)) {
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

	/** Default state, but faced toward {@code inward} if the block supports a horizontal facing (chests, furnaces...). */
	private static BlockState faced(Block block, Direction inward) {
		BlockState state = block.defaultBlockState();
		if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
			return state.setValue(BlockStateProperties.HORIZONTAL_FACING, inward);
		}
		return state;
	}
}
