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
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * {@code /spawnstash} — op-only. Places one of several random "stash" arrangements of empty containers
 * (and a bed) in a clear line next to the player. Layouts: barrel+table+bed (shuffled), shulker+bed,
 * lone shulker, lone barrel, or barrel+shulker.
 */
public final class SpawnStashCommand {
	private enum Piece {
		BARREL(1), CRAFTING_TABLE(1), SHULKER(1), BED(2);

		final int width;

		Piece(int width) {
			this.width = width;
		}
	}

	private static final Block[] BED_COLORS = {
			Blocks.RED_BED, Blocks.BLUE_BED, Blocks.LIME_BED, Blocks.YELLOW_BED, Blocks.WHITE_BED, Blocks.PURPLE_BED
	};

	private SpawnStashCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("spawnstash")
				.requires(com.tradeshop.TradeShop::canModerate)
				.executes(context -> spawn(context.getSource())));
	}

	private static int spawn(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ServerLevel level = (ServerLevel) player.level();

		List<Piece> pieces = randomLayout();
		int cells = pieces.stream().mapToInt(p -> p.width).sum();

		// Try each horizontal direction (shuffled) until we find one with a clear run of cells beside the player.
		List<Direction> directions = new ArrayList<>(List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST));
		Collections.shuffle(directions);
		BlockPos base = player.blockPosition();

		for (Direction dir : directions) {
			List<BlockPos> line = clearLine(level, base, dir, cells);
			if (line != null) {
				place(level, pieces, line, dir);
				player.sendSystemMessage(Component.literal("Spawned a stash next to you.").withStyle(ChatFormatting.GREEN));
				return Command.SINGLE_SUCCESS;
			}
		}

		source.sendFailure(Component.literal("Not enough clear space around you to spawn a stash. Move somewhere more open."));
		return 0;
	}

	private static List<Piece> randomLayout() {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		List<Piece> pieces = new ArrayList<>();
		switch (random.nextInt(5)) {
			case 0 -> {
				pieces.add(Piece.BARREL);
				pieces.add(Piece.CRAFTING_TABLE);
				pieces.add(Piece.BED);
				Collections.shuffle(pieces);
			}
			case 1 -> {
				pieces.add(Piece.SHULKER);
				pieces.add(Piece.BED);
			}
			case 2 -> pieces.add(Piece.SHULKER);
			case 3 -> pieces.add(Piece.BARREL);
			default -> {
				pieces.add(Piece.BARREL);
				pieces.add(Piece.SHULKER);
			}
		}
		return pieces;
	}

	/** Returns {@code cells} consecutive replaceable positions starting one block out in {@code dir}, or null. */
	private static List<BlockPos> clearLine(ServerLevel level, BlockPos base, Direction dir, int cells) {
		List<BlockPos> positions = new ArrayList<>();
		for (int i = 1; i <= cells; i++) {
			BlockPos pos = base.relative(dir, i);
			if (!level.getBlockState(pos).canBeReplaced()) {
				return null;
			}
			positions.add(pos);
		}
		return positions;
	}

	private static void place(ServerLevel level, List<Piece> pieces, List<BlockPos> line, Direction dir) {
		int cell = 0;
		for (Piece piece : pieces) {
			if (piece == Piece.BED) {
				BlockPos foot = line.get(cell);
				BlockPos head = line.get(cell + 1);
				Block bed = BED_COLORS[ThreadLocalRandom.current().nextInt(BED_COLORS.length)];
				BlockState footState = bed.defaultBlockState()
						.setValue(BedBlock.FACING, dir).setValue(BedBlock.PART, BedPart.FOOT);
				level.setBlockAndUpdate(foot, footState);
				level.setBlockAndUpdate(head, footState.setValue(BedBlock.PART, BedPart.HEAD));
				cell += 2;
			} else {
				Block block = switch (piece) {
					case BARREL -> Blocks.BARREL;
					case CRAFTING_TABLE -> Blocks.CRAFTING_TABLE;
					default -> Blocks.SHULKER_BOX;
				};
				level.setBlockAndUpdate(line.get(cell), block.defaultBlockState());
				cell += 1;
			}
		}
	}
}
