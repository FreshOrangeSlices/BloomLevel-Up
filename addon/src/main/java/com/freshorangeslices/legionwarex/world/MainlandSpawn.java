package com.freshorangeslices.legionwarex.world;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraftforge.common.Tags;

/**
 * Where a new world's spawn goes: the nearest land to the middle of the world (searched outward in rings, 64 blocks
 * apart, up to 4096 out) that is well inland, not too rugged or high, and not a beach, river, mountain or mushroom
 * island. Vanilla's own search accepts the coast (continentalness from -0.11), so a world can start on a small island
 * out at sea (FreshOrangeSlices' playtest). Read from the world's own noise (climate and terrain height), before any
 * chunk exists.
 */
public final class MainlandSpawn {
	/** Continentalness at the spot: mid-inland or further (vanilla's bands: coast to -0.11, near inland to 0.03). */
	public static final float MIN_CONTINENTALNESS = 0.1F;
	/** Land all round: no sea or coast this far out in any of eight directions. */
	public static final int INLAND_RADIUS = 256;
	public static final float LAND = -0.11F;
	/** Erosion from vanilla's middle band up (lower is where peaks and slopes rise). */
	public static final float MIN_EROSION = -0.2225F;
	public static final int MAX_HEIGHT = 110;
	public static final int STEP = 64;
	public static final int MAX_RADIUS = 4096;

	private MainlandSpawn() {
	}

	/** The spot chosen, with what it was read as. */
	public record Spot(BlockPos pos, Holder<Biome> biome, float continentalness, float erosion, int checked) {
	}

	public static Optional<Spot> find(ChunkGenerator generator, RandomState state, LevelHeightAccessor heights) {
		Climate.Sampler sampler = state.sampler();
		int checked = 0;

		for (int ring = 0; ring * STEP <= MAX_RADIUS; ring++) {
			for (int i = 0; i < Math.max(1, 8 * ring); i++) {
				BlockPos column = onRing(ring, i);
				int x = column.getX();
				int z = column.getZ();
				checked++;
				Climate.TargetPoint point = sampler.sample(QuartPos.fromBlock(x), 0, QuartPos.fromBlock(z));
				float continentalness = Climate.unquantizeCoord(point.continentalness());
				float erosion = Climate.unquantizeCoord(point.erosion());

				if (continentalness < MIN_CONTINENTALNESS || erosion < MIN_EROSION || !inland(sampler, x, z)) {
					continue;
				}

				int height = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, heights, state);

				if (height <= generator.getSeaLevel() || height > MAX_HEIGHT) {
					continue;
				}

				Holder<Biome> biome = generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(height), QuartPos.fromBlock(z), sampler);

				if (!unwanted(biome)) {
					return Optional.of(new Spot(new BlockPos(x, height, z), biome, continentalness, erosion, checked));
				}
			}
		}

		return Optional.empty();
	}

	/** The i-th of a square ring's 8 * ring points, STEP apart (ring 0 is the middle). */
	static BlockPos onRing(int ring, int i) {
		if (ring == 0) {
			return BlockPos.ZERO;
		}

		int side = 2 * ring;
		int edge = i / side;
		int along = i % side;
		int a = -ring + along;

		return switch (edge) {
			case 0 -> new BlockPos(a * STEP, 0, -ring * STEP);
			case 1 -> new BlockPos(ring * STEP, 0, a * STEP);
			case 2 -> new BlockPos(-a * STEP, 0, ring * STEP);
			default -> new BlockPos(-ring * STEP, 0, -a * STEP);
		};
	}

	private static boolean inland(Climate.Sampler sampler, int x, int z) {
		for (int i = 0; i < 8; i++) {
			double angle = Math.PI * 2 * i / 8;
			int px = x + (int) Math.round(Math.cos(angle) * INLAND_RADIUS);
			int pz = z + (int) Math.round(Math.sin(angle) * INLAND_RADIUS);

			if (Climate.unquantizeCoord(sampler.sample(QuartPos.fromBlock(px), 0, QuartPos.fromBlock(pz)).continentalness()) < LAND) {
				return false;
			}
		}

		return true;
	}

	/** Not to start in: water, beaches, mountains and windswept hills, mushroom islands. */
	public static boolean unwanted(Holder<Biome> biome) {
		return biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN) || biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_BEACH)
				|| biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(BiomeTags.IS_HILL) || biome.is(Tags.Biomes.IS_MUSHROOM) || biome.is(Tags.Biomes.IS_PEAK);
	}
}
