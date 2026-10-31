package com.freshorangeslices.legionwarex.world;

import com.freshorangeslices.legionwarex.AddonConfig;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Mobs the pack never lets into a world (FreshOrangeSlices, 2026-10-30): whatever brings one in (a natural spawn, a
 * structure, a spawner, another mob's summon, a spawn egg, a command), it doesn't join, and ones already saved in a world
 * are gone when their chunk loads. The list is the config's ({@code bannedMobs}); the pack also takes them out of every
 * spawn list and hides their spawn eggs, so nothing tries to spawn them in the first place.
 */
public final class BannedMobs {
	private static volatile List<? extends String> source;
	private static volatile Set<EntityType<?>> banned = Set.of();
	private static Set<EntityType<?>> override;

	private BannedMobs() {
	}

	/** The banned types now (the config's list, resolved once per change of it). */
	public static Set<EntityType<?>> banned() {
		if (override != null) {
			return override;
		}

		List<? extends String> ids = AddonConfig.bannedMobs();

		if (ids != source) {
			Set<EntityType<?>> types = new HashSet<>();

			for (String id : ids) {
				ResourceLocation key = ResourceLocation.tryParse(id);

				if (key != null && ForgeRegistries.ENTITY_TYPES.containsKey(key)) {
					types.add(ForgeRegistries.ENTITY_TYPES.getValue(key));
				}
			}

			banned = Set.copyOf(types);
			source = ids;
		}

		return banned;
	}

	/** For game tests: these instead of the config's list (null to go back). */
	public static void overrideForTest(Set<EntityType<?>> types) {
		override = types;
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onJoin(EntityJoinLevelEvent event) {
		if (!event.getLevel().isClientSide() && banned().contains(event.getEntity().getType())) {
			event.setCanceled(true);
		}
	}
}
