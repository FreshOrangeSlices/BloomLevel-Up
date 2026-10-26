package com.freshorangeslices.bloomlevelup.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;

/** The mod's entity types. */
public final class BloomEntities {
	private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, BloomLevelUp.MOD_ID);

	public static final RegistryObject<EntityType<ThrownWeapon>> THROWN_WEAPON = ENTITIES.register("thrown_weapon",
			() -> EntityType.Builder.<ThrownWeapon>of(ThrownWeapon::new, MobCategory.MISC)
					.sized(0.5F, 0.5F)
					.clientTrackingRange(4)
					.updateInterval(20)
					.build(BloomLevelUp.key("thrown_weapon")));

	private BloomEntities() {
	}

	public static void register(IEventBus modBus) {
		ENTITIES.register(modBus);
	}
}
