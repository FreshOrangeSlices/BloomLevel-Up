package com.freshorangeslices.legionwarex.world;

import com.freshorangeslices.legionwarex.AddonConfig;
import com.freshorangeslices.legionwarex.LegionWareX;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.MiscOverworldFeatures;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.PlayerRespawnLogic;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * A new world's spawn on the mainland ({@link MainlandSpawn}), and the terrain survey for the pack test: off unless
 * the server starts with {@code -Dlegionwarex.terrain=<file>}.
 */
public final class WorldEvents {
	private WorldEvents() {
	}

	// Late, so a mod that places the spawn itself (and cancels) goes first.
	@SubscribeEvent(priority = EventPriority.LOW)
	public static void onCreateSpawn(LevelEvent.CreateSpawnPosition event) {
		if (event.isCanceled() || !AddonConfig.mainlandSpawn() || !(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD
				|| !(level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator generator)
				|| !(generator.stable(NoiseGeneratorSettings.OVERWORLD) || generator.stable(NoiseGeneratorSettings.LARGE_BIOMES)
				|| generator.stable(NoiseGeneratorSettings.AMPLIFIED))) {
			return;
		}

		long started = System.nanoTime();
		Optional<MainlandSpawn.Spot> spot = MainlandSpawn.find(generator, level.getChunkSource().randomState(), level);

		if (spot.isEmpty()) {
			LegionWareX.LOGGER.info("LegionWareX: no mainland within {} blocks of the middle; the spawn is vanilla's", MainlandSpawn.MAX_RADIUS);
			return;
		}

		BlockPos spawn = surface(level, spot.get().pos());
		event.getSettings().setSpawn(spawn, 0.0F);
		bonusChest(level, spawn);
		event.setCanceled(true);
		LegionWareX.LOGGER.info("LegionWareX: world spawn on the mainland at {} ({}, continentalness {}, {} spots read, {} ms)", spawn.toShortString(),
				spot.get().biome().unwrapKey().map(key -> key.location().toString()).orElse("?"), spot.get().continentalness(), spot.get().checked(),
				(System.nanoTime() - started) / 1_000_000);
	}

	/** A block a player can stand on near the spot, as vanilla picks one in a chunk; else the spot itself. */
	private static BlockPos surface(ServerLevel level, BlockPos column) {
		ChunkPos middle = new ChunkPos(column);

		for (int ring = 0; ring <= 2; ring++) {
			for (int dx = -ring; dx <= ring; dx++) {
				for (int dz = -ring; dz <= ring; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
						continue;
					}

					BlockPos found = PlayerRespawnLogic.getSpawnPosInChunk(level, new ChunkPos(middle.x + dx, middle.z + dz));

					if (found != null) {
						return found;
					}
				}
			}
		}

		return column;
	}

	/** Vanilla places the bonus chest after its own spawn search, which a cancelled event skips. */
	private static void bonusChest(ServerLevel level, BlockPos spawn) {
		try {
			if (level.getServer().getWorldData().worldGenOptions().generateBonusChest()) {
				level.registryAccess().registry(Registries.CONFIGURED_FEATURE)
						.flatMap(registry -> registry.getHolder(MiscOverworldFeatures.BONUS_CHEST))
						.ifPresent(feature -> feature.value().place(level, level.getChunkSource().getGenerator(), level.getRandom(), spawn));
			}
		} catch (RuntimeException e) {
			LegionWareX.LOGGER.warn("LegionWareX: couldn't place the bonus chest", e);
		}
	}

	@SubscribeEvent
	public static void onServerStarted(ServerStartedEvent event) {
		String target = System.getProperty("legionwarex.terrain");

		if (target == null || target.isBlank()) {
			return;
		}

		MinecraftServer server = event.getServer();
		Thread survey = new Thread(() -> writeSurvey(server, Path.of(target)), "LegionWareX terrain survey");
		survey.setDaemon(true);
		survey.start();
	}

	private static void writeSurvey(MinecraftServer server, Path target) {
		List<String> lines = new ArrayList<>();

		try {
			for (TerrainSurvey.Setup setup : TerrainSurvey.setups(server)) {
				JsonObject terrain = TerrainSurvey.survey(setup, 48, 128);
				lines.add(terrain.toString());
				LegionWareX.LOGGER.info("LegionWareX terrain {}", TerrainSurvey.describe(terrain));

				if (setup.name().equals("pack")) {
					lines.add(TerrainSurvey.spawn(setup).toString());

					for (long seed = 1; seed <= 4; seed++) {
						lines.add(TerrainSurvey.spawn(TerrainSurvey.Setup.of("pack", server.registryAccess(), seed)).toString());
					}
				}
			}
		} catch (RuntimeException e) {
			LegionWareX.LOGGER.error("LegionWareX: the terrain survey failed", e);
			JsonObject failed = new JsonObject();
			failed.addProperty("kind", "error");
			failed.addProperty("error", e.toString());
			lines.add(failed.toString());
		}

		try {
			Files.write(target, lines, StandardCharsets.UTF_8);
			LegionWareX.LOGGER.info("LegionWareX: terrain survey written to {}", target.toAbsolutePath());
		} catch (IOException e) {
			LegionWareX.LOGGER.error("LegionWareX: couldn't write the terrain survey to {}", target, e);
		}
	}
}
