package com.freshorangeslices.bloomlevelup.sheet;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/**
 * Stand-in mobs the stat sheet asks the game's own rules about ("how much does Smite add against this?", "what
 * does a zombie's sword hit count as?"). They're created but never added to the world, one of each per level,
 * and dropped when the server stops.
 */
final class Dummies {
	private static final Map<ServerLevel, Map<EntityType<?>, LivingEntity>> CACHE = new IdentityHashMap<>();

	private Dummies() {
	}

	@SuppressWarnings("unchecked")
	static <T extends LivingEntity> T of(ServerLevel level, EntityType<T> type) {
		Map<EntityType<?>, LivingEntity> perLevel = CACHE.computeIfAbsent(level, key -> new HashMap<>());
		LivingEntity dummy = perLevel.get(type);

		if (dummy == null) {
			dummy = type.create(level, EntitySpawnReason.COMMAND);

			if (dummy == null) {
				throw new IllegalStateException("Bloom stat sheet: can't create a " + type);
			}

			perLevel.put(type, dummy);
		}

		return (T) dummy;
	}

	static void clear() {
		CACHE.clear();
	}
}
