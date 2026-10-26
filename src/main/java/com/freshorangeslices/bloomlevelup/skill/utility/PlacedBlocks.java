package com.freshorangeslices.bloomlevelup.skill.utility;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.IEventBus;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;

/**
 * mcMMO-style anti-exploit memory: which XP/Yield-relevant blocks were placed by a player. Stored per chunk
 * (a chunk capability, saved with the chunk), only for blocks that matter, so ordinary building costs nothing.
 * Placed blocks give no Utility XP, no Yield and no treasure, and Timber won't fell placed logs.
 */
public final class PlacedBlocks {
	public static final Capability<Positions> PLACED = CapabilityManager.get(new CapabilityToken<>() {
	});

	private PlacedBlocks() {
	}

	/** One chunk's placed positions ({@link BlockPos#asLong}). */
	public static final class Positions {
		private final LongOpenHashSet set = new LongOpenHashSet();
	}

	public static void init(IEventBus modBus) {
		modBus.addListener((RegisterCapabilitiesEvent event) -> event.register(Positions.class));
		MinecraftForge.EVENT_BUS.addGenericListener(LevelChunk.class, (AttachCapabilitiesEvent<LevelChunk> event) -> {
			if (!event.getObject().getLevel().isClientSide()) {
				Provider provider = new Provider();
				event.addCapability(BloomLevelUp.id("placed_blocks"), provider);
				event.addListener(provider.optional::invalidate);
			}
		});
	}

	private static LongOpenHashSet set(LevelChunk chunk) {
		return chunk.getCapability(PLACED).map(positions -> positions.set).orElse(null);
	}

	public static boolean isPlaced(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return false;
		}

		LongOpenHashSet set = set(level.getChunkAt(pos));
		return set != null && set.contains(pos.asLong());
	}

	public static void mark(ServerLevel level, BlockPos pos) {
		LevelChunk chunk = level.getChunkAt(pos);
		LongOpenHashSet set = set(chunk);

		if (set != null && set.add(pos.asLong())) {
			chunk.setUnsaved(true);
		}
	}

	/** Forgets a position; returns whether it was marked. */
	public static boolean clear(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return false;
		}

		LevelChunk chunk = level.getChunkAt(pos);
		LongOpenHashSet set = set(chunk);

		if (set == null || !set.remove(pos.asLong())) {
			return false;
		}

		chunk.setUnsaved(true);
		return true;
	}

	private static final class Provider implements ICapabilitySerializable<LongArrayTag> {
		private final Positions positions = new Positions();
		private final LazyOptional<Positions> optional = LazyOptional.of(() -> positions);

		@Override
		public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
			return PLACED.orEmpty(capability, optional);
		}

		@Override
		public LongArrayTag serializeNBT() {
			return new LongArrayTag(positions.set.toLongArray());
		}

		@Override
		public void deserializeNBT(LongArrayTag tag) {
			positions.set.clear();

			for (long pos : tag.getAsLongArray()) {
				positions.set.add(pos);
			}
		}
	}
}
