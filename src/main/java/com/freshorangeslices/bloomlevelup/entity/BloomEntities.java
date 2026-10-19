package com.freshorangeslices.bloomlevelup.entity;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;

/** The mod's entity types. */
public final class BloomEntities {
	public static final ResourceKey<EntityType<?>> THROWN_WEAPON_KEY = ResourceKey.create(Registries.ENTITY_TYPE, BloomLevelUp.id("thrown_weapon"));
	public static final EntityType<ThrownWeapon> THROWN_WEAPON = Registry.register(BuiltInRegistries.ENTITY_TYPE, THROWN_WEAPON_KEY,
			EntityType.Builder.<ThrownWeapon>of(ThrownWeapon::new, MobCategory.MISC)
					.noLootTable()
					.sized(0.5F, 0.5F)
					.clientTrackingRange(4)
					.updateInterval(20)
					.build(THROWN_WEAPON_KEY));

	private BloomEntities() {
	}

	/** Loads the class so the types register during mod init. */
	public static void init() {
	}
}
