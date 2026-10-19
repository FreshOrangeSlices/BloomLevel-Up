package com.freshorangeslices.bloomlevelup.skill.utility;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import com.freshorangeslices.bloomlevelup.registry.BloomTags;

/** Finds the blocks Vein Mining and Timber should break: breadth-first, nearest first, capped. */
public final class ChainBreaker {
	private ChainBreaker() {
	}

	/** Connected blocks of the same type (including diagonals), not counting the origin itself. */
	public static List<BlockPos> vein(ServerLevel level, BlockPos origin, Block block, int max) {
		return search(level, origin, max, Integer.MIN_VALUE, state -> state.is(block), pos -> true);
	}

	/**
	 * A natural tree above the cut: connected natural logs at or above the origin's height, and only if the
	 * logs touch natural leaves. Player-placed logs (and anything without natural leaves) is never felled.
	 */
	public static List<BlockPos> tree(ServerLevel level, BlockPos origin, int max) {
		List<BlockPos> logs = search(level, origin, max, origin.getY(), state -> state.is(BloomTags.TIMBER_LOGS),
				pos -> !PlacedBlocks.isPlaced(level, pos));

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
