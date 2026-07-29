package com.tradeshop.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntFunction;

/**
 * {@code /spawnores} — op-only. Carves a natural-looking ore vein next to the player and cases the exposed
 * sides so it doesn't float. Two forms:
 * <ul>
 *   <li>{@code /spawnores <1-4>} — presets: 1 coal, 2 iron, 3 diamond, 4 ancient debris; deepslate/stone
 *       chosen per-block by Y.</li>
 *   <li>{@code /spawnores <ore> <background>} (either order) — a custom vein of any ore in any host block,
 *       e.g. {@code gold stone}, {@code deepslate diamond}, {@code quartz netherrack}.</li>
 * </ul>
 */
public final class SpawnOresCommand {
	private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

	private static final List<String> ORE_NAMES = List.of(
			"coal", "iron", "copper", "gold", "redstone", "emerald", "lapis", "diamond", "quartz", "ancient_debris");
	private static final List<String> BACKGROUND_NAMES = List.of(
			"stone", "deepslate", "netherrack", "blackstone", "basalt", "tuff", "granite", "andesite", "diorite",
			"calcite", "end_stone");

	private SpawnOresCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		SuggestionProvider<CommandSourceStack> oreSuggest = (ctx, b) -> SharedSuggestionProvider.suggest(ORE_NAMES, b);
		SuggestionProvider<CommandSourceStack> bgSuggest = (ctx, b) -> SharedSuggestionProvider.suggest(BACKGROUND_NAMES, b);
		// First word can be either an ore or a background, so suggest both.
		SuggestionProvider<CommandSourceStack> firstSuggest = (ctx, b) -> SharedSuggestionProvider.suggest(
				java.util.stream.Stream.concat(ORE_NAMES.stream(), BACKGROUND_NAMES.stream()).toList(), b);

		dispatcher.register(Commands.literal("spawnores")
				.requires(com.tradeshop.TradeShop::canModerate)
				.then(Commands.argument("rarity", IntegerArgumentType.integer(1, 4))
						.executes(context -> spawnPreset(context.getSource(), IntegerArgumentType.getInteger(context, "rarity"))))
				.then(Commands.argument("ore", StringArgumentType.word()).suggests(firstSuggest)
						.then(Commands.argument("background", StringArgumentType.word()).suggests(bgSuggest)
								.executes(context -> spawnCustom(context.getSource(),
										StringArgumentType.getString(context, "ore"),
										StringArgumentType.getString(context, "background"))))));
	}

	// --- Preset (numeric) form ------------------------------------------

	private static int spawnPreset(CommandSourceStack source, int rarity) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ServerLevel level = (ServerLevel) player.level();
		ThreadLocalRandom random = ThreadLocalRandom.current();

		int veinSize = switch (rarity) {
			case 1 -> rand(random, 8, 14);
			case 2 -> rand(random, 6, 10);
			case 4 -> rand(random, 2, 4);
			default -> rand(random, 4, 7);
		};
		int count = placeVein(level, veinCenter(player, random), veinSize,
				y -> presetOre(rarity, y), y -> presetCasing(rarity, y), random);

		String label = switch (rarity) {
			case 1 -> "coal";
			case 2 -> "iron";
			case 4 -> "ancient debris";
			default -> "diamond";
		};
		player.sendSystemMessage(Component.literal("Spawned a " + label + " ore vein (" + count + " ores).")
				.withStyle(ChatFormatting.GREEN));
		return Command.SINGLE_SUCCESS;
	}

	/** Deepslate below y=0, stone at/above. */
	private static Block presetCasing(int rarity, int y) {
		return rarity == 4 ? Blocks.NETHERRACK : (y < 0 ? Blocks.DEEPSLATE : Blocks.STONE);
	}

	private static Block presetOre(int rarity, int y) {
		boolean deep = y < 0;
		return switch (rarity) {
			case 1 -> deep ? Blocks.DEEPSLATE_COAL_ORE : Blocks.COAL_ORE;
			case 2 -> deep ? Blocks.DEEPSLATE_IRON_ORE : Blocks.IRON_ORE;
			case 4 -> Blocks.ANCIENT_DEBRIS;
			default -> deep ? Blocks.DEEPSLATE_DIAMOND_ORE : Blocks.DIAMOND_ORE;
		};
	}

	// --- Custom (ore + background) form ---------------------------------

	private static int spawnCustom(CommandSourceStack source, String first, String second) throws CommandSyntaxException {
		// Accept either order: figure out which word is the ore and which is the host block.
		String metal = parseMetal(first);
		Block background = parseBackground(second);
		if (metal == null || background == null) {
			metal = parseMetal(second);
			background = parseBackground(first);
		}
		if (metal == null || background == null) {
			source.sendFailure(Component.literal("Usage: /spawnores <ore> <background>. Ores: "
					+ String.join(", ", ORE_NAMES) + ". Backgrounds: " + String.join(", ", BACKGROUND_NAMES) + "."));
			return 0;
		}

		Block ore = customOre(metal, background);
		ServerPlayer player = source.getPlayerOrException();
		ServerLevel level = (ServerLevel) player.level();
		ThreadLocalRandom random = ThreadLocalRandom.current();

		final Block oreBlock = ore;
		final Block bgBlock = background;
		int count = placeVein(level, veinCenter(player, random), veinSizeFor(metal),
				y -> oreBlock, y -> bgBlock, random);

		player.sendSystemMessage(Component.literal("Spawned a " + metal + " vein in "
				+ background.getName().getString().toLowerCase() + " (" + count + " ores).").withStyle(ChatFormatting.GREEN));
		return Command.SINGLE_SUCCESS;
	}

	private static String parseMetal(String name) {
		return switch (name.toLowerCase()) {
			case "coal" -> "coal";
			case "iron" -> "iron";
			case "copper" -> "copper";
			case "gold" -> "gold";
			case "redstone" -> "redstone";
			case "emerald" -> "emerald";
			case "lapis", "lapis_lazuli" -> "lapis";
			case "diamond" -> "diamond";
			case "quartz", "nether_quartz" -> "quartz";
			case "debris", "ancient_debris", "ancientdebris", "netherite" -> "debris";
			default -> null;
		};
	}

	private static Block parseBackground(String name) {
		return switch (name.toLowerCase()) {
			case "stone" -> Blocks.STONE;
			case "deepslate" -> Blocks.DEEPSLATE;
			case "netherrack", "nether" -> Blocks.NETHERRACK;
			case "blackstone" -> Blocks.BLACKSTONE;
			case "basalt" -> Blocks.BASALT;
			case "tuff" -> Blocks.TUFF;
			case "granite" -> Blocks.GRANITE;
			case "andesite" -> Blocks.ANDESITE;
			case "diorite" -> Blocks.DIORITE;
			case "calcite" -> Blocks.CALCITE;
			case "end_stone", "endstone", "end" -> Blocks.END_STONE;
			default -> null;
		};
	}

	/** Picks the ore variant that matches the chosen host: deepslate variant in deepslate, nether variants in nether rock. */
	private static Block customOre(String metal, Block background) {
		boolean deep = background == Blocks.DEEPSLATE;
		boolean nether = background == Blocks.NETHERRACK || background == Blocks.BLACKSTONE || background == Blocks.BASALT;
		return switch (metal) {
			case "coal" -> deep ? Blocks.DEEPSLATE_COAL_ORE : Blocks.COAL_ORE;
			case "iron" -> deep ? Blocks.DEEPSLATE_IRON_ORE : Blocks.IRON_ORE;
			case "copper" -> deep ? Blocks.DEEPSLATE_COPPER_ORE : Blocks.COPPER_ORE;
			case "gold" -> nether ? Blocks.NETHER_GOLD_ORE : (deep ? Blocks.DEEPSLATE_GOLD_ORE : Blocks.GOLD_ORE);
			case "redstone" -> deep ? Blocks.DEEPSLATE_REDSTONE_ORE : Blocks.REDSTONE_ORE;
			case "emerald" -> deep ? Blocks.DEEPSLATE_EMERALD_ORE : Blocks.EMERALD_ORE;
			case "lapis" -> deep ? Blocks.DEEPSLATE_LAPIS_ORE : Blocks.LAPIS_ORE;
			case "quartz" -> Blocks.NETHER_QUARTZ_ORE;
			case "debris" -> Blocks.ANCIENT_DEBRIS;
			default -> deep ? Blocks.DEEPSLATE_DIAMOND_ORE : Blocks.DIAMOND_ORE; // diamond
		};
	}

	private static int veinSizeFor(String metal) {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		return switch (metal) {
			case "debris" -> rand(random, 2, 4);
			case "diamond", "emerald" -> rand(random, 4, 7);
			case "gold", "quartz" -> rand(random, 5, 8);
			default -> rand(random, 7, 12);
		};
	}

	// --- Shared placement -----------------------------------------------

	private static BlockPos veinCenter(ServerPlayer player, ThreadLocalRandom random) {
		Direction dir = HORIZONTAL[random.nextInt(HORIZONTAL.length)];
		return player.blockPosition().below().relative(dir, 2);
	}

	private static int placeVein(ServerLevel level, BlockPos center, int veinSize,
			IntFunction<Block> oreAt, IntFunction<Block> casingAt, ThreadLocalRandom random) {
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

		for (BlockPos orePos : ores) {
			for (Direction face : Direction.values()) {
				BlockPos neighbor = orePos.relative(face);
				if (!ores.contains(neighbor) && level.getBlockState(neighbor).canBeReplaced()) {
					level.setBlockAndUpdate(neighbor, casingAt.apply(neighbor.getY()).defaultBlockState());
				}
			}
		}
		for (BlockPos orePos : ores) {
			level.setBlockAndUpdate(orePos, oreAt.apply(orePos.getY()).defaultBlockState());
		}
		return ores.size();
	}

	private static int rand(ThreadLocalRandom random, int min, int max) {
		return random.nextInt(min, max + 1);
	}
}
