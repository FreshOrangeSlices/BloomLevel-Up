package com.freshorangeslices.bloomlevelup.skill.combat;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;

/**
 * The mod's damage types (data: {@code data/bloomlevelup/damage_type/*.json}). Secondary damage carries the
 * player as the causing entity (kill credit, Combat XP) but no direct entity, so Thorns never answers it.
 */
public final class BloomDamage {
	public static final ResourceKey<DamageType> BLEED = key("bleed");
	public static final ResourceKey<DamageType> LIGHTNING_SHOCK = key("lightning_shock");
	public static final ResourceKey<DamageType> OVERKILL = key("overkill");

	private BloomDamage() {
	}

	private static ResourceKey<DamageType> key(String path) {
		return ResourceKey.create(Registries.DAMAGE_TYPE, BloomLevelUp.id(path));
	}

	public static DamageSource bleed(ServerLevel level) {
		return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(BLEED));
	}

	public static DamageSource lightningShock(ServerLevel level, Entity attacker) {
		return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(LIGHTNING_SHOCK), null, attacker);
	}

	public static DamageSource overkill(ServerLevel level, Entity attacker) {
		return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(OVERKILL), null, attacker);
	}

	/** Damage this mod deals as a side effect: never feeds weapon Majors again (no recursion). */
	public static boolean isSecondary(DamageSource source) {
		return source.is(BLEED) || source.is(LIGHTNING_SHOCK) || source.is(OVERKILL);
	}
}
