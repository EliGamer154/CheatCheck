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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * {@code /spawnbase} — op-only. Carves a random 5x5 to 7x7 enclosed room near the player and fills it with a
 * random spread of base furniture (chests, shulkers, barrels, beds, furnaces, crafting stations, lighting).
 * Different every time.
 */
public final class SpawnBaseCommand {
	private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

	private static final Block[] FURNITURE = {
			Blocks.CHEST, Blocks.BARREL, Blocks.SHULKER_BOX, Blocks.ENDER_CHEST, Blocks.FURNACE, Blocks.BLAST_FURNACE,
			Blocks.SMOKER, Blocks.CRAFTING_TABLE, Blocks.ANVIL, Blocks.ENCHANTING_TABLE, Blocks.BOOKSHELF,
			Blocks.CHISELED_BOOKSHELF, Blocks.BREWING_STAND, Blocks.CAULDRON, Blocks.JUKEBOX, Blocks.NOTE_BLOCK,
			Blocks.LOOM, Blocks.CARTOGRAPHY_TABLE, Blocks.SMITHING_TABLE, Blocks.GRINDSTONE, Blocks.STONECUTTER};
	private static final Block[] BED_COLORS = {
			Blocks.RED_BED, Blocks.BLUE_BED, Blocks.LIME_BED, Blocks.YELLOW_BED, Blocks.WHITE_BED, Blocks.CYAN_BED};

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

		int size = 5 + random.nextInt(3); // 5, 6, or 7
		int height = 5;                    // floor + 3 interior + ceiling
		BlockPos base = player.blockPosition();
		Direction dir = HORIZONTAL[random.nextInt(HORIZONTAL.length)];
		// Center the room a full room-length away so the player never ends up inside a wall.
		int originX = base.getX() + dir.getStepX() * size - size / 2;
		int originZ = base.getZ() + dir.getStepZ() * size - size / 2;
		int floorY = base.getY() - 1;

		// Shell (stone brick) + hollow interior.
		for (int dx = 0; dx < size; dx++) {
			for (int dz = 0; dz < size; dz++) {
				for (int dy = 0; dy < height; dy++) {
					BlockPos pos = new BlockPos(originX + dx, floorY + dy, originZ + dz);
					boolean shell = dx == 0 || dx == size - 1 || dz == 0 || dz == size - 1 || dy == 0 || dy == height - 1;
					level.setBlockAndUpdate(pos, shell ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.AIR.defaultBlockState());
				}
			}
		}

		// Light the ceiling.
		level.setBlockAndUpdate(new BlockPos(originX + size / 2, floorY + height - 1, originZ + size / 2),
				Blocks.GLOWSTONE.defaultBlockState());

		// Interior floor cells for furniture.
		List<BlockPos> floorCells = new ArrayList<>();
		for (int dx = 1; dx < size - 1; dx++) {
			for (int dz = 1; dz < size - 1; dz++) {
				floorCells.add(new BlockPos(originX + dx, floorY + 1, originZ + dz));
			}
		}
		Collections.shuffle(floorCells);
		Set<BlockPos> pool = new HashSet<>(floorCells);
		Set<BlockPos> used = new HashSet<>();

		// A couple of beds, then a random scatter of furniture on the rest.
		int beds = random.nextInt(2) + 1;
		for (int i = 0; i < beds; i++) {
			placeBed(level, floorCells, pool, used, random);
		}
		int furnitureCount = Math.max(2, floorCells.size() / 2);
		int placed = 0;
		for (BlockPos cell : floorCells) {
			if (placed >= furnitureCount) {
				break;
			}
			if (!used.contains(cell)) {
				level.setBlockAndUpdate(cell, FURNITURE[random.nextInt(FURNITURE.length)].defaultBlockState());
				used.add(cell);
				placed++;
			}
		}

		player.sendSystemMessage(Component.literal("Spawned a " + size + "x" + size + " base nearby.")
				.withStyle(ChatFormatting.GREEN));
		return Command.SINGLE_SUCCESS;
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
}
