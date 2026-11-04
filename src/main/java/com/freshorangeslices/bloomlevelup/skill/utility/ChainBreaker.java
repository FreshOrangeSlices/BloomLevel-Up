package com.freshorangeslices.bloomlevelup.skill.utility;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import com.freshorangeslices.bloomlevelup.registry.BloomTags;

/** Finds the blocks Vein Mining and Timber should break: breadth-first, nearest first, capped. */
public final class ChainBreaker {
	private ChainBreaker() {
	}

	/** Connected blocks of the same type (including diagonals), not counting the origin itself. */
	public static List<BlockPos> vein(ServerLevel level, BlockPos origin, Block block, int max) {
		return search(level, origin, max, Integer.MIN_VALUE, state -> state.is(block), pos -> true);
	}

	/** How far down a trunk is followed looking for the ground (the tallest trees are well under this). */
	private static final int MAX_TRUNK = 64;

	/**
	 * A natural tree above the cut: connected natural logs at or above the origin's height, and only if the
	 * logs touch natural leaves. Player-placed logs (and anything without natural leaves) is never felled, and
	 * neither is anything a structure built: the cut log has to stand on a trunk (natural logs straight down to the
	 * ground, which a beam or a post on a floor never does), and logs inside a structure's pieces (a house's frame,
	 * even one touching a real tree) are left alone (FreshOrangeSlices, 2026-11-02: one beam of a spruce house took the
	 * whole frame down).
	 */
	public static List<BlockPos> tree(ServerLevel level, BlockPos origin, int max) {
		Structures structures = new Structures(level);

		if (!onTrunk(level, origin) || structures.contains(origin)) {
			return List.of();
		}

		List<BlockPos> logs = search(level, origin, max, origin.getY(), state -> state.is(BloomTags.TIMBER_LOGS),
				pos -> !PlacedBlocks.isPlaced(level, pos) && !structures.contains(pos));

		if (logs.isEmpty()) {
			return logs;
		}

		for (BlockPos log : logs) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						BlockPos near = log.offset(dx, dy, dz);

						if (level.isLoaded(near) && isNaturalLeaves(level.getBlockState(near))) {
							return logs;
						}
					}
				}
			}
		}

		return List.of();
	}

	/**
	 * Whether the cut log stands on a trunk: below it, natural logs (none at all if it was the bottom one) and then the
	 * ground ({@link BloomTags#TIMBER_GROUND}). The cut log itself may already be gone (Timber runs after the break).
	 */
	public static boolean onTrunk(ServerLevel level, BlockPos origin) {
		BlockPos.MutableBlockPos pos = origin.mutable();

		for (int i = 0; i < MAX_TRUNK; i++) {
			pos.move(Direction.DOWN);

			if (!level.isLoaded(pos)) {
				return false;
			}

			BlockState state = level.getBlockState(pos);

			if (state.is(BloomTags.TIMBER_GROUND)) {
				return true;
			}

			if (!state.is(BloomTags.TIMBER_LOGS) || PlacedBlocks.isPlaced(level, pos)) {
				return false;
			}
		}

		return false;
	}

	/** Whether a position is inside one of a structure's pieces (not just its outer box), looked up once a chunk. */
	private static final class Structures {
		private final ServerLevel level;
		private final Long2ObjectOpenHashMap<List<StructureStart>> byChunk = new Long2ObjectOpenHashMap<>();

		Structures(ServerLevel level) {
			this.level = level;
		}

		boolean contains(BlockPos pos) {
			StructureManager manager = level.structureManager();
			ChunkPos chunk = new ChunkPos(pos);
			List<StructureStart> starts = byChunk.get(chunk.toLong());

			if (starts == null) {
				starts = manager.startsForStructure(chunk, structure -> true);
				byChunk.put(chunk.toLong(), starts);
			}

			for (StructureStart start : starts) {
				if (manager.structureHasPieceAt(pos, start)) {
					return true;
				}
			}

			return false;
		}
	}

	public static boolean isNaturalLeaves(BlockState state) {
		if (!state.is(BloomTags.TIMBER_LEAVES)) {
			return false;
		}

		// Leaves the player placed are persistent; wart blocks and the like have no such flag.
		return !state.hasProperty(BlockStateProperties.PERSISTENT) || !state.getValue(BlockStateProperties.PERSISTENT);
	}

	private interface StateTest {
		boolean test(BlockState state);
	}

	private interface PosTest {
		boolean test(BlockPos pos);
	}

	private static List<BlockPos> search(ServerLevel level, BlockPos origin, int max, int minY, StateTest matches, PosTest allowed) {
		List<BlockPos> found = new ArrayList<>();
		LongOpenHashSet visited = new LongOpenHashSet();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		visited.add(origin.asLong());
		queue.add(origin);

		while (!queue.isEmpty() && found.size() < max) {
			BlockPos current = queue.poll();

			for (int dx = -1; dx <= 1 && found.size() < max; dx++) {
				for (int dy = -1; dy <= 1 && found.size() < max; dy++) {
					for (int dz = -1; dz <= 1 && found.size() < max; dz++) {
						if (dx == 0 && dy == 0 && dz == 0) {
							continue;
						}

						BlockPos next = current.offset(dx, dy, dz);

						if (next.getY() < minY || !visited.add(next.asLong()) || !level.isLoaded(next)) {
							continue;
						}

						if (matches.test(level.getBlockState(next)) && allowed.test(next)) {
							found.add(next);
							queue.add(next);
						}
					}
				}
			}
		}

		return found;
	}
}
