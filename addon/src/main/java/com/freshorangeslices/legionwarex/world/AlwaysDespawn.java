package com.freshorangeslices.legionwarex.world;

import com.freshorangeslices.legionwarex.AddonConfig;
import com.freshorangeslices.legionwarex.Despawning;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Monsters their own mod keeps for good that the pack lets despawn as vanilla's do (config {@code alwaysDespawn}): at
 * once with no player within 128 blocks, now and then beyond 32 (FreshOrangeSlices, 2026-11-24: after The Damned, Born
 * in Chaos's Scarlet Persecutor and Infernal Spirit and Undead Unleashed's Flamebreather stayed for days). Checked once a
 * second. One a player kept stays: name-tagged, leashed, riding or ridden, or wearing gear it picked up.
 */
public final class AlwaysDespawn {
	/** Game tests: the list in place of the config's. */
	public static List<String> testMobs;

	private AlwaysDespawn() {
	}

	static List<? extends String> mobs() {
		return testMobs != null ? testMobs : AddonConfig.alwaysDespawn();
	}

	/** Whether this mob is one the pack despawns itself (on the list, and not kept by a player). */
	public static boolean applies(Mob mob) {
		ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
		return id != null && mobs().contains(id.toString()) && !Despawning.keptByPlayer(mob);
	}

	@SubscribeEvent
	public static void onTick(LivingEvent.LivingTickEvent event) {
		if (event.getEntity() instanceof Mob mob && mob.tickCount % 20 == 17 && !mob.level().isClientSide()
				&& mob.isPersistenceRequired() && applies(mob)) {
			Player nearest = mob.level().getNearestPlayer(mob, -1.0);

			// Only worth the picked-up-gear check (it reads the mob's saved form) when it would go.
			if (nearest != null && Despawning.despawns(nearest.distanceToSqr(mob), mob.getNoActionTime(), mob.getRandom())
					&& !Despawning.pickedUpGear(mob)) {
				mob.discard();
			} else if (nearest != null && nearest.distanceToSqr(mob) < Despawning.NEAR * Despawning.NEAR) {
				mob.setNoActionTime(0);
			}
		}
	}
}
