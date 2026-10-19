package com.freshorangeslices.bloomlevelup.skill.utility;

import java.util.Arrays;

import com.mojang.serialization.Codec;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;

/**
 * mcMMO-style anti-exploit memory: which XP/Yield-relevant blocks were placed by a player. Stored per chunk
 * (saved with the chunk), only for blocks that matter, so ordinary building costs nothing.
 * Placed blocks give no Utility XP, no Yield and no treasure, and Timber won't fell placed logs.
 */
public final class PlacedBlocks {
	private static final Codec<LongOpenHashSet> CODEC = Codec.LONG_STREAM.xmap(
			stream -> new LongOpenHashSet(stream.toArray()),
			set -> Arrays.stream(set.toLongArray()));

	public static final AttachmentType<LongOpenHashSet> PLACED = AttachmentRegistry.create(BloomLevelUp.id("placed_blocks"), builder -> builder
			.persistent(CODEC)
			.initializer(LongOpenHashSet::new));

	private PlacedBlocks() {
	}

	public static void init() {
	}

	public static boolean isPlaced(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return false;
		}

		LongOpenHashSet set = level.getChunkAt(pos).getAttached(PLACED);
		return set != null && set.contains(pos.asLong());
	}

	public static void mark(ServerLevel level, BlockPos pos) {
		LevelChunk chunk = level.getChunkAt(pos);

		if (chunk.getAttachedOrCreate(PLACED).add(pos.asLong())) {
			chunk.markUnsaved();
		}
	}

	/** Forgets a position; returns whether it was marked. */
	public static boolean clear(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return false;
		}

		LevelChunk chunk = level.getChunkAt(pos);
		LongOpenHashSet set = chunk.getAttached(PLACED);

		if (set == null || !set.remove(pos.asLong())) {
			return false;
		}

		if (set.isEmpty()) {
			chunk.removeAttached(PLACED);
		}

		chunk.markUnsaved();
		return true;
	}
}
