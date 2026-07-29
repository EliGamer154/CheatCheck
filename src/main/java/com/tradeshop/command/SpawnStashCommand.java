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
 * {@code /spawnstash [rarity]} — op-only. Places a random, base-like "stash" of containers and utility
 * blocks (furnaces, crafting stations, an enchanting setup, lighting, a bed, ...) clustered next to the
 * player. Works underground: it overwrites whatever blocks are there. With no argument the containers are
 * empty; with a rarity of 1-3 the <em>shulker boxes</em> get a small, player-looking loot spread
 * (rarity 1 below iron, 2 iron-tier, 3 diamond-tier).
 */
public final class SpawnStashCommand {
	private enum Piece {
		BARREL(Blocks.BARREL), CHEST(Blocks.CHEST), ENDER_CHEST(Blocks.ENDER_CHEST), SHULKER(Blocks.SHULKER_BOX),
		CRAFTING_TABLE(Blocks.CRAFTING_TABLE), FURNACE(Blocks.FURNACE), BLAST_FURNACE(Blocks.BLAST_FURNACE),
		SMOKER(Blocks.SMOKER), ANVIL(Blocks.ANVIL), ENCHANTING_TABLE(Blocks.ENCHANTING_TABLE),
		BREWING_STAND(Blocks.BREWING_STAND), SMITHING_TABLE(Blocks.SMITHING_TABLE), GRINDSTONE(Blocks.GRINDSTONE),
		STONECUTTER(Blocks.STONECUTTER), CARTOGRAPHY_TABLE(Blocks.CARTOGRAPHY_TABLE), LOOM(Blocks.LOOM),
		LECTERN(Blocks.LECTERN), COMPOSTER(Blocks.COMPOSTER), HOPPER(Blocks.HOPPER), CAULDRON(Blocks.CAULDRON),
		BOOKSHELF(Blocks.BOOKSHELF), CHISELED_BOOKSHELF(Blocks.CHISELED_BOOKSHELF),
		TORCH(Blocks.TORCH), LANTERN(Blocks.LANTERN), GLOWSTONE(Blocks.GLOWSTONE), SEA_LANTERN(Blocks.SEA_LANTERN),
		CAMPFIRE(Blocks.CAMPFIRE), JUKEBOX(Blocks.JUKEBOX), NOTE_BLOCK(Blocks.NOTE_BLOCK),
		BED(null, 2);

		final Block block;
		final int width;

		Piece(Block block) {
			this(block, 1);
		}

		Piece(Block block, int width) {
			this.block = block;
			this.width = width;
		}
	}

	private static final Piece[] STORAGE = {Piece.BARREL, Piece.CHEST, Piece.ENDER_CHEST, Piece.SHULKER};
	private static final Piece[] SMELTERS = {Piece.FURNACE, Piece.BLAST_FURNACE, Piece.SMOKER};
	private static final Piece[] STATIONS = {
			Piece.CRAFTING_TABLE, Piece.ANVIL, Piece.BREWING_STAND, Piece.SMITHING_TABLE, Piece.GRINDSTONE,
			Piece.STONECUTTER, Piece.CARTOGRAPHY_TABLE, Piece.LOOM, Piece.LECTERN, Piece.COMPOSTER,
			Piece.HOPPER, Piece.CAULDRON};
	private static final Piece[] LIGHTS = {Piece.TORCH, Piece.LANTERN, Piece.GLOWSTONE, Piece.SEA_LANTERN, Piece.CAMPFIRE};
	private static final Piece[] DECO = {Piece.JUKEBOX, Piece.NOTE_BLOCK, Piece.CHISELED_BOOKSHELF};

	private static final Block[] BED_COLORS = {
			Blocks.RED_BED, Blocks.BLUE_BED, Blocks.LIME_BED, Blocks.YELLOW_BED, Blocks.WHITE_BED,
			Blocks.PURPLE_BED, Blocks.CYAN_BED, Blocks.ORANGE_BED, Blocks.PINK_BED, Blocks.GREEN_BED};

	private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

	/** Curated themed stashes, mixed in alongside the procedural generator. */
	private static final List<Piece[]> CURATED = List.of(
			new Piece[]{Piece.BARREL, Piece.CRAFTING_TABLE, Piece.BED, Piece.TORCH},
			new Piece[]{Piece.SHULKER, Piece.BED, Piece.LANTERN},
			new Piece[]{Piece.BARREL, Piece.SHULKER, Piece.ENDER_CHEST},
			new Piece[]{Piece.ENCHANTING_TABLE, Piece.BOOKSHELF, Piece.BOOKSHELF, Piece.BOOKSHELF, Piece.CHEST, Piece.BED},
			new Piece[]{Piece.FURNACE, Piece.FURNACE, Piece.BLAST_FURNACE, Piece.CHEST, Piece.BARREL, Piece.HOPPER},
			new Piece[]{Piece.BARREL, Piece.BARREL, Piece.CHEST, Piece.SHULKER, Piece.ENDER_CHEST, Piece.GLOWSTONE},
			new Piece[]{Piece.CRAFTING_TABLE, Piece.FURNACE, Piece.CHEST, Piece.ANVIL, Piece.BED, Piece.TORCH},
			new Piece[]{Piece.BREWING_STAND, Piece.CAULDRON, Piece.CHEST, Piece.BARREL, Piece.LANTERN},
			new Piece[]{Piece.LECTERN, Piece.LOOM, Piece.CARTOGRAPHY_TABLE, Piece.SMITHING_TABLE, Piece.STONECUTTER, Piece.BARREL},
			new Piece[]{Piece.SHULKER, Piece.SHULKER, Piece.ENDER_CHEST, Piece.ANVIL, Piece.CAMPFIRE},
			new Piece[]{Piece.CHEST, Piece.CHEST, Piece.FURNACE, Piece.SMOKER, Piece.CRAFTING_TABLE, Piece.BED, Piece.LANTERN},
			new Piece[]{Piece.JUKEBOX, Piece.NOTE_BLOCK, Piece.CHISELED_BOOKSHELF, Piece.CHEST, Piece.BED, Piece.SEA_LANTERN});

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
		if (rarity > 0 && !pieces.contains(Piece.SHULKER)) {
			pieces.add(Piece.SHULKER);
		}
		// Place wider pieces (beds) first so they can still find an adjacent free cell.
		pieces.sort((a, b) -> Integer.compare(b.width, a.width));
		int cellsNeeded = pieces.stream().mapToInt(p -> p.width).sum();

		BlockPos base = player.blockPosition();
		List<Direction> dirs = new ArrayList<>(List.of(HORIZONTAL));
		Collections.shuffle(dirs);

		// The stash overwrites whatever blocks are here (air, stone, ore...) so it works underground too.
		for (int distance : new int[]{2, 3}) {
			for (Direction dir : dirs) {
				Set<BlockPos> pool = collectCells(base.relative(dir, distance), base);
				if (pool.size() >= cellsNeeded && placeCluster(level, pieces, pool, rarity)) {
					String suffix = rarity > 0 ? " (rarity " + rarity + " loot in the shulkers)" : "";
					player.sendSystemMessage(Component.literal("Spawned a stash next to you" + suffix + ".")
							.withStyle(ChatFormatting.GREEN));
					return Command.SINGLE_SUCCESS;
				}
			}
		}

		source.sendFailure(Component.literal("Couldn't place the stash. Try moving a little and running it again."));
		return 0;
	}

	private static List<Piece> randomLayout() {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		// Mix curated themed stashes with a procedural base generator for lots of variety.
		if (random.nextInt(100) < 40) {
			return new ArrayList<>(List.of(CURATED.get(random.nextInt(CURATED.size()))));
		}
		return proceduralStash(random);
	}

	/** Assembles a believable little base: storage, smelting, crafting stations, maybe an enchant setup, a bed, lights. */
	private static List<Piece> proceduralStash(ThreadLocalRandom random) {
		List<Piece> p = new ArrayList<>();
		int storage = 1 + random.nextInt(3);
		for (int i = 0; i < storage; i++) {
			p.add(pick(random, STORAGE));
		}
		if (random.nextInt(100) < 75) {
			p.add(Piece.CRAFTING_TABLE);
		}
		if (random.nextInt(100) < 70) {
			p.add(pick(random, SMELTERS));
			if (random.nextInt(100) < 35) {
				p.add(pick(random, SMELTERS));
			}
		}
		if (random.nextInt(100) < 40) {
			p.add(Piece.ENCHANTING_TABLE);
			int shelves = 1 + random.nextInt(3);
			for (int i = 0; i < shelves; i++) {
				p.add(random.nextBoolean() ? Piece.BOOKSHELF : Piece.CHISELED_BOOKSHELF);
			}
		}
		if (random.nextInt(100) < 40) {
			p.add(Piece.ANVIL);
		}
		if (random.nextInt(100) < 50) {
			p.add(Piece.BED);
		}
		int extraStations = random.nextInt(3);
		for (int i = 0; i < extraStations; i++) {
			p.add(pick(random, STATIONS));
		}
		if (random.nextInt(100) < 30) {
			p.add(pick(random, DECO));
		}
		int lights = 1 + random.nextInt(2);
		for (int i = 0; i < lights; i++) {
			p.add(pick(random, LIGHTS));
		}
		// Keep it within a placeable footprint.
		int cells = 0;
		List<Piece> trimmed = new ArrayList<>();
		for (Piece piece : p) {
			if (cells + piece.width > 20) {
				break;
			}
			trimmed.add(piece);
			cells += piece.width;
		}
		return trimmed;
	}

	private static Piece pick(ThreadLocalRandom random, Piece[] options) {
		return options[random.nextInt(options.length)];
	}

	/**
	 * Cells in a footprint around {@code anchor} (at the anchor's Y), skipping the block the player is
	 * standing in. No air/support filtering: the stash overwrites whatever is there, so it works underground.
	 */
	private static Set<BlockPos> collectCells(BlockPos anchor, BlockPos playerBlock) {
		Set<BlockPos> cells = new HashSet<>();
		for (int dx = -1; dx <= 3; dx++) {
			for (int dz = -1; dz <= 3; dz++) {
				BlockPos pos = anchor.offset(dx, 0, dz);
				if (!pos.equals(playerBlock) && !pos.equals(playerBlock.above())) {
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
				level.setBlockAndUpdate(cell, piece.block.defaultBlockState());
				if (piece == Piece.SHULKER && rarity > 0) {
					fillShulker(level, cell, rarity);
				}
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

	// --- Loot -------------------------------------------------------------

	private static void fillShulker(ServerLevel level, BlockPos pos, int rarity) {
		BlockEntity be = level.getBlockEntity(pos);
		if (be instanceof ShulkerBoxBlockEntity) {
			StashLoot.fill(be, rarity);
		}
	}
}
