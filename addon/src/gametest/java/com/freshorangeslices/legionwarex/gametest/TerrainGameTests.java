package com.freshorangeslices.legionwarex.gametest;

import com.freshorangeslices.legionwarex.LegionWareX;
import com.freshorangeslices.legionwarex.world.MainlandSpawn;
import com.freshorangeslices.legionwarex.world.TerrainSurvey;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Optional;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * The terrain survey and the mainland spawn search, on the dev server's own Overworld noise (vanilla's: the add-on
 * changes no terrain itself; the pack's data pack does). The numbers go to the log for the CI summary.
 */
@GameTestHolder(LegionWareX.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TerrainGameTests {
	@GameTest(template = "empty", timeoutTicks = 400)
	public static void theSurveyReadsTheOverworldAsVanillaBuildsIt(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		List<TerrainSurvey.Setup> setups = TerrainSurvey.setups(server);
		JsonObject pack = TerrainSurvey.survey(setups.get(0), 32, 192);
		JsonObject vanilla = TerrainSurvey.survey(setups.get(1), 32, 192);
		LegionWareX.LOGGER.info("legionwarex terrain (dev) {}", TerrainSurvey.describe(pack));
		LegionWareX.LOGGER.info("legionwarex terrain (dev) {} | {}", TerrainSurvey.describe(vanilla), vanilla);

		double land = vanilla.get("land").getAsDouble();
		helper.assertTrue(land > 0.2 && land < 0.9, "Some land, some sea: " + land);
		helper.assertTrue(vanilla.get("mountainBiomes").getAsDouble() > 0.0, "Vanilla has mountains: " + vanilla);
		// Without the pack's data pack, the registries' Overworld is vanilla's own: the same land, point for point.
		helper.assertTrue(pack.get("land").equals(vanilla.get("land")) && pack.get("mountainBiomes").equals(vanilla.get("mountainBiomes"))
				&& pack.get("landMax").equals(vanilla.get("landMax")), "The registries' Overworld matches vanilla's code: " + pack + " / " + vanilla);
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 400)
	public static void theSpawnSearchFindsTheMainland(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();

		for (long seed = 1; seed <= 6; seed++) {
			TerrainSurvey.Setup setup = TerrainSurvey.Setup.of("dev", server.registryAccess(), seed);
			JsonObject spawn = TerrainSurvey.spawn(setup);
			LegionWareX.LOGGER.info("legionwarex spawn (dev) {}", spawn);
			Optional<MainlandSpawn.Spot> spot = MainlandSpawn.find(setup.generator(), setup.state(), setup.heights());
			helper.assertTrue(spot.isPresent(), "Seed " + seed + ": mainland within " + MainlandSpawn.MAX_RADIUS + " blocks");
			MainlandSpawn.Spot found = spot.get();
			helper.assertTrue(found.continentalness() >= MainlandSpawn.MIN_CONTINENTALNESS, "Seed " + seed + ": inland " + found);
			helper.assertTrue(found.pos().getY() > 63 && found.pos().getY() <= MainlandSpawn.MAX_HEIGHT, "Seed " + seed + ": not too high " + found);
			helper.assertFalse(MainlandSpawn.unwanted(found.biome()), "Seed " + seed + ": not sea, beach, river or mountain " + found);
		}

		helper.succeed();
	}
}
