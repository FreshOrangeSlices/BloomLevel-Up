package com.freshorangeslices.legionwarex.world;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * How the Overworld's land comes out, read from its noise without making a chunk: on a grid around the middle of the
 * world, the share of land, how high it rises, its erosion (low erosion is where mountains are) and how much of it is
 * mountain biomes, plus where the add-on's spawn search lands. For the pack's terrain (its data pack can change the
 * noise) beside vanilla's own (built from the game's code, whatever the data packs say), with the same seed.
 */
public final class TerrainSurvey {
	/** Vanilla's peaks, slopes and windswept hills (the vanilla biome source names nothing else there). */
	private static final Set<String> MOUNTAINS = Set.of("jagged_peaks", "frozen_peaks", "stony_peaks", "snowy_slopes", "grove", "meadow",
			"cherry_grove", "windswept_hills", "windswept_gravelly_hills", "windswept_forest");
	private static final Set<String> PEAKS = Set.of("jagged_peaks", "frozen_peaks", "stony_peaks");

	private TerrainSurvey() {
	}

	/** An Overworld generator (the normal world type's) from a set of registries, with its noise for a seed. */
	public record Setup(String name, NoiseBasedChunkGenerator generator, RandomState state, LevelHeightAccessor heights, long seed) {
		public static Setup of(String name, HolderLookup.Provider registries, long seed) {
			Holder<NoiseGeneratorSettings> settings = registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD);
			MultiNoiseBiomeSource biomes = MultiNoiseBiomeSource.createFromPreset(
					registries.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST).getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
			RandomState state = RandomState.create(settings.value(), registries.lookupOrThrow(Registries.NOISE), seed);
			LevelHeightAccessor heights = LevelHeightAccessor.create(settings.value().noiseSettings().minY(), settings.value().noiseSettings().height());
			return new Setup(name, new NoiseBasedChunkGenerator(biomes, settings), state, heights, seed);
		}
	}

	/** The pack's (the server's registries) and vanilla's, for the world's seed. */
	public static List<Setup> setups(MinecraftServer server) {
		long seed = server.getWorldData().worldGenOptions().seed();
		return List.of(Setup.of("pack", server.registryAccess(), seed), Setup.of("vanilla", VanillaRegistries.createLookup(), seed));
	}

	/** grid x grid points, 'spacing' blocks apart, centred on the middle of the world. */
	public static JsonObject survey(Setup setup, int grid, int spacing) {
		long started = System.nanoTime();
		Climate.Sampler sampler = setup.state().sampler();
		int sea = setup.generator().getSeaLevel();
		List<Integer> land = new ArrayList<>();
		List<Float> erosion = new ArrayList<>();
		int mountains = 0;
		int peaks = 0;
		int points = 0;

		for (int i = 0; i < grid; i++) {
			for (int j = 0; j < grid; j++) {
				// Off the round numbers, so the grid doesn't line up with anything.
				int x = (i - grid / 2) * spacing + 37;
				int z = (j - grid / 2) * spacing + 59;
				points++;
				int floor = setup.generator().getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, setup.heights(), setup.state());

				if (floor <= sea) {
					continue;
				}

				land.add(floor);
				erosion.add(Climate.unquantizeCoord(sampler.sample(QuartPos.fromBlock(x), 0, QuartPos.fromBlock(z)).erosion()));
				String biome = setup.generator().getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(floor), QuartPos.fromBlock(z), sampler)
						.unwrapKey().map(key -> key.location().getPath()).orElse("");

				if (MOUNTAINS.contains(biome)) {
					mountains++;
				}

				if (PEAKS.contains(biome)) {
					peaks++;
				}
			}
		}

		JsonObject out = new JsonObject();
		out.addProperty("kind", "terrain");
		out.addProperty("setup", setup.name());
		out.addProperty("seed", setup.seed());
		out.addProperty("points", points);
		out.addProperty("spacing", spacing);
		out.addProperty("land", share(land.size(), points));
		int[] heights = land.stream().mapToInt(Integer::intValue).sorted().toArray();
		out.addProperty("landMean", heights.length == 0 ? 0 : Math.round(Arrays.stream(heights).average().orElse(0) * 10) / 10.0);
		out.addProperty("landP50", percentile(heights, 0.5));
		out.addProperty("landP90", percentile(heights, 0.9));
		out.addProperty("landP99", percentile(heights, 0.99));
		out.addProperty("landMax", heights.length == 0 ? 0 : heights[heights.length - 1]);

		for (int above : new int[] {100, 130, 160, 200}) {
			out.addProperty("over" + above, share((int) Arrays.stream(heights).filter(h -> h > above).count(), heights.length));
		}

		float[] eroded = new float[erosion.size()];

		for (int i = 0; i < eroded.length; i++) {
			eroded[i] = erosion.get(i);
		}

		Arrays.sort(eroded);
		JsonObject quantiles = new JsonObject();

		for (double q : new double[] {0.05, 0.1, 0.25, 0.5, 0.75, 0.9}) {
			quantiles.addProperty("p" + Math.round(q * 100), eroded.length == 0 ? 0 : Math.round(eroded[Math.min(eroded.length - 1, (int) (q * eroded.length))] * 1000) / 1000.0);
		}

		out.add("erosion", quantiles);
		// Vanilla's erosion bands: peaks below -0.78, peaks and slopes below -0.375, slopes and hills below -0.2225.
		for (float below : new float[] {-0.78F, -0.375F, -0.2225F}) {
			int count = 0;

			for (float e : eroded) {
				count += e < below ? 1 : 0;
			}

			out.addProperty("erosionBelow" + below, share(count, eroded.length));
		}

		out.addProperty("mountainBiomes", share(mountains, land.size()));
		out.addProperty("peakBiomes", share(peaks, land.size()));
		out.addProperty("ms", (System.nanoTime() - started) / 1_000_000);
		return out;
	}

	/** Where the add-on's search puts the spawn for a seed, and where vanilla's own search would. */
	public static JsonObject spawn(Setup setup) {
		long started = System.nanoTime();
		JsonObject out = new JsonObject();
		out.addProperty("kind", "spawn");
		out.addProperty("setup", setup.name());
		out.addProperty("seed", setup.seed());
		Optional<MainlandSpawn.Spot> spot = MainlandSpawn.find(setup.generator(), setup.state(), setup.heights());
		out.addProperty("found", spot.isPresent());
		spot.ifPresent(s -> {
			out.addProperty("x", s.pos().getX());
			out.addProperty("y", s.pos().getY());
			out.addProperty("z", s.pos().getZ());
			out.addProperty("biome", s.biome().unwrapKey().map(key -> key.location().toString()).orElse("?"));
			out.addProperty("continentalness", Math.round(s.continentalness() * 1000) / 1000.0);
			out.addProperty("erosion", Math.round(s.erosion() * 1000) / 1000.0);
			out.addProperty("checked", s.checked());
		});
		out.addProperty("ms", (System.nanoTime() - started) / 1_000_000);
		Climate.Sampler sampler = setup.state().sampler();
		BlockPos vanilla = sampler.findSpawnPosition();
		Climate.TargetPoint point = sampler.sample(QuartPos.fromBlock(vanilla.getX()), 0, QuartPos.fromBlock(vanilla.getZ()));
		int height = setup.generator().getBaseHeight(vanilla.getX(), vanilla.getZ(), Heightmap.Types.OCEAN_FLOOR_WG, setup.heights(), setup.state());
		JsonObject old = new JsonObject();
		old.addProperty("x", vanilla.getX());
		old.addProperty("z", vanilla.getZ());
		old.addProperty("y", height);
		old.addProperty("biome", setup.generator().getBiomeSource()
				.getNoiseBiome(QuartPos.fromBlock(vanilla.getX()), QuartPos.fromBlock(height), QuartPos.fromBlock(vanilla.getZ()), sampler)
				.unwrapKey().map(key -> key.location().toString()).orElse("?"));
		old.addProperty("continentalness", Math.round(Climate.unquantizeCoord(point.continentalness()) * 1000) / 1000.0);
		out.add("vanillaSearch", old);
		return out;
	}

	public static String describe(JsonObject terrain) {
		return terrain.get("setup").getAsString() + ": land " + terrain.get("land") + ", over 130 " + terrain.get("over130") + ", over 160 "
				+ terrain.get("over160") + ", mountains " + terrain.get("mountainBiomes") + ", peaks " + terrain.get("peakBiomes") + ", erosion "
				+ terrain.get("erosion") + ", " + terrain.get("ms") + " ms";
	}

	private static double share(int count, int of) {
		return of == 0 ? 0 : Math.round(count * 10000.0 / of) / 10000.0;
	}

	private static int percentile(int[] sorted, double q) {
		return sorted.length == 0 ? 0 : sorted[Math.min(sorted.length - 1, (int) (q * sorted.length))];
	}
}
