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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * {@code /spawnores <rarity>} — op-only. Carves a natural-looking ore vein into the ground next to the
 * player and casings any exposed sides in stone so it doesn't float. Rarity 1 = coal, 2 = iron, 3 = diamond;
 * deepslate variants are used below y=0. Rarity 4 = a small ancient debris vein cased in netherrack.
 */
public final class SpawnOresCommand {
	private SpawnOresCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("spawnores")
				.requires(com.tradeshop.TradeShop::canModerate)
				.then(Commands.argument("rarity", IntegerArgumentType.integer(1, 4))
						.executes(context -> spawn(context.getSource(), IntegerArgumentType.getInteger(context, "rarity")))));
	}

	private static int spawn(CommandSourceStack source, int rarity) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ServerLevel level = (ServerLevel) player.level();
		ThreadLocalRandom random = ThreadLocalRandom.current();

		Direction[] horizontal = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
		Direction dir = horizontal[random.nextInt(horizontal.length)];
		BlockPos center = player.blockPosition().below().relative(dir, 2);

		int veinSize = switch (rarity) {
			case 1 -> rand(random, 8, 14);
			case 2 -> rand(random, 6, 10);
			case 4 -> rand(random, 2, 4); // ancient debris veins are tiny
			default -> rand(random, 4, 7);
		};

		// Build a connected blob by random-walking from the center.
		Set<BlockPos> ores = new HashSet<>();
		ores.add(center);
		BlockPos cursor = center;
		int guard = 0;
		while (ores.size() < veinSize && guard++ < veinSize * 8) {
			cursor = cursor.relative(Direction.values()[random.nextInt(6)]);
			ores.add(cursor);
			if (random.nextInt(100) < 25) {
				cursor = center;
			}
		}

		// Case any exposed faces so an above-ground vein looks embedded rather than floating.
		// Deepslate/stone and the ore variant are chosen per-block by that block's own Y (deepslate below y=0).
		for (BlockPos orePos : ores) {
			for (Direction face : Direction.values()) {
				BlockPos neighbor = orePos.relative(face);
				if (!ores.contains(neighbor) && level.getBlockState(neighbor).canBeReplaced()) {
					level.setBlockAndUpdate(neighbor, casingFor(rarity, neighbor.getY()).defaultBlockState());
				}
			}
		}
		for (BlockPos orePos : ores) {
			level.setBlockAndUpdate(orePos, oreFor(rarity, orePos.getY()).defaultBlockState());
		}

		String label = switch (rarity) {
			case 1 -> "coal";
			case 2 -> "iron";
			case 4 -> "ancient debris";
			default -> "diamond";
		};
		player.sendSystemMessage(Component.literal("Spawned a " + label + " ore vein (" + ores.size() + " ores).")
				.withStyle(ChatFormatting.GREEN));
		return Command.SINGLE_SUCCESS;
	}

	/** Deepslate below y=0, stone at or above — matching vanilla generation. */
	private static Block stoneFor(int y) {
		return y < 0 ? Blocks.DEEPSLATE : Blocks.STONE;
	}

	/** Casing block around a vein: netherrack for ancient debris, otherwise stone/deepslate by Y. */
	private static Block casingFor(int rarity, int y) {
		return rarity == 4 ? Blocks.NETHERRACK : stoneFor(y);
	}

	/** The ore block for a rarity, using the deepslate variant below y=0 (ancient debris has no variant). */
	private static Block oreFor(int rarity, int y) {
		boolean deep = y < 0;
		return switch (rarity) {
			case 1 -> deep ? Blocks.DEEPSLATE_COAL_ORE : Blocks.COAL_ORE;
			case 2 -> deep ? Blocks.DEEPSLATE_IRON_ORE : Blocks.IRON_ORE;
			case 4 -> Blocks.ANCIENT_DEBRIS;
			default -> deep ? Blocks.DEEPSLATE_DIAMOND_ORE : Blocks.DIAMOND_ORE;
		};
	}

	private static int rand(ThreadLocalRandom random, int min, int max) {
		return random.nextInt(min, max + 1);
	}
}
