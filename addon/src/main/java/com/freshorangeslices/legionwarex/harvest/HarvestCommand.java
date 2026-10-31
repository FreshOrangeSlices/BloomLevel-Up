package com.freshorangeslices.legionwarex.harvest;

import com.freshorangeslices.legionwarex.AddonConfig;
import com.freshorangeslices.legionwarex.LegionWareX;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * {@code /legionwarex harvest} (operators): for each of the Harvest Court's mobs that is in the game, how many of 40 tries
 * its spawn rule (with the add-on's all-year one) lets it stand on the ground where the command runs, as a natural spawn
 * at this hour, and how many the all-year rule alone does (in Born in Chaos's season its own rule already says yes, so
 * the second count is what shows the add-on's). Logged as "LegionWareX harvest: ..." for the pack test, which asks at
 * midnight and at noon.
 */
public final class HarvestCommand {
	static final int TRIES = 40;

	private HarvestCommand() {
	}

	public static LiteralArgumentBuilder<CommandSourceStack> node() {
		return Commands.literal("harvest").executes(HarvestCommand::check);
	}

	private static int check(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		ServerLevel level = source.getLevel();
		BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(source.getPosition()));
		List<EntityType<?>> folk = HarvestCourt.installed();
		List<String> rows = new ArrayList<>();

		for (EntityType<?> type : folk) {
			int yes = 0;
			int own = 0;

			for (int i = 0; i < TRIES; i++) {
				if (SpawnPlacements.checkSpawnRules(type, level, MobSpawnType.NATURAL, at, level.getRandom())) {
					yes++;
				}

				if (HarvestCourt.mayStand(type, level, MobSpawnType.NATURAL, at, level.getRandom())) {
					own++;
				}
			}

			rows.add(ForgeRegistries.ENTITY_TYPES.getKey(type) + " " + yes + "/" + TRIES + " (all-year rule " + own + "/" + TRIES + ")");
		}

		String text = folk.isEmpty() ? "no Harvest Court mobs in the game"
				: (AddonConfig.harvestAllYear() ? "all year" : "in season only") + ", time " + level.getDayTime() % 24000 + ": "
						+ String.join(", ", rows);
		LegionWareX.LOGGER.info("LegionWareX harvest: {}", text);
		source.sendSuccess(() -> Component.literal("LegionWareX harvest: " + text), true);
		return folk.size();
	}
}
