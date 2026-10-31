package com.freshorangeslices.legionwarex.harvest;

import com.freshorangeslices.legionwarex.AddonConfig;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The Harvest Court all year: Born in Chaos's pumpkin folk. Their spawn rule (one procedure for all five) only says yes
 * from 25 October to 7 November, by the computer's calendar; outside those dates this says yes in its place on the same
 * terms otherwise: the Overworld, not the deep dark or mushroom fields, and a monster's usual rule (dark enough, solid
 * ground, not peaceful). Only natural spawns (and those at chunk generation); how many come is the biome spawn weights
 * (the pack's data pack). Born in Chaos's own season is left alone: in it, its rule decides.
 */
public final class HarvestCourt {
	private HarvestCourt() {
	}

	/** Whether this mob is one of the Harvest Court's (the config's list). */
	public static boolean folk(EntityType<?> type) {
		ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);
		return id != null && AddonConfig.harvestMobs().contains(id.toString());
	}

	/** The mobs on the list that are in the game, for the command. */
	public static List<EntityType<?>> installed() {
		return AddonConfig.harvestMobs().stream().map(ResourceLocation::tryParse)
				.filter(id -> id != null && ForgeRegistries.ENTITY_TYPES.containsKey(id))
				.<EntityType<?>>map(ForgeRegistries.ENTITY_TYPES::getValue).toList();
	}

	/** Born in Chaos's terms without the date: the Overworld (not the deep dark or mushroom fields), a monster's own rule. */
	@SuppressWarnings("unchecked")
	public static boolean mayStand(EntityType<?> type, ServerLevelAccessor level, MobSpawnType spawnType, BlockPos pos, RandomSource random) {
		ServerLevel server = level.getLevel();

		if (server == null || server.dimension() != Level.OVERWORLD) {
			return false;
		}

		Holder<Biome> biome = level.getBiome(pos);

		if (biome.is(Biomes.DEEP_DARK) || biome.is(Biomes.MUSHROOM_FIELDS)) {
			return false;
		}

		return Monster.checkMonsterSpawnRules((EntityType<? extends Monster>) type, level, spawnType, pos, random);
	}

	@SubscribeEvent
	public static void onSpawnPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event) {
		if (event.getDefaultResult() || event.getResult() != Event.Result.DEFAULT || !AddonConfig.harvestAllYear()) {
			return;
		}

		MobSpawnType spawnType = event.getSpawnType();

		if (spawnType != MobSpawnType.NATURAL && spawnType != MobSpawnType.CHUNK_GENERATION) {
			return;
		}

		if (folk(event.getEntityType()) && mayStand(event.getEntityType(), event.getLevel(), spawnType, event.getPos(), event.getRandom())) {
			event.setResult(Event.Result.ALLOW);
		}
	}
}
