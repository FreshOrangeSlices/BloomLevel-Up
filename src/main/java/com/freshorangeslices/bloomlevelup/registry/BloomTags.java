package com.freshorangeslices.bloomlevelup.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;

/**
 * Every tag the mod reads. Classification is data, not code: modpacks extend these tags (or replace them)
 * to make their own armor, weapons, foods, damage types and effects participate.
 */
public final class BloomTags {
	// Items
	public static final TagKey<Item> ARMOR_LIGHT = item("armor/light");
	public static final TagKey<Item> ARMOR_MEDIUM = item("armor/medium");
	public static final TagKey<Item> ARMOR_HEAVY = item("armor/heavy");
	public static final TagKey<Item> WEAPON_BLADE = item("weapon/blade");
	public static final TagKey<Item> WEAPON_BLUNT = item("weapon/blunt");
	public static final TagKey<Item> MEAT = item("food/meat");
	public static final TagKey<Item> UTILITY_TOOLS = item("utility_tools");

	// Damage types
	public static final TagKey<DamageType> ELEMENTAL = damage("elemental");
	public static final TagKey<DamageType> MELEE = damage("melee");
	public static final TagKey<DamageType> DODGEABLE = damage("dodgeable");
	public static final TagKey<DamageType> BLADE_DAMAGE = damage("blade");
	public static final TagKey<DamageType> BLUNT_DAMAGE = damage("blunt");

	// Mob effects
	public static final TagKey<MobEffect> DAMAGE_OVER_TIME = effect("damage_over_time");
	public static final TagKey<MobEffect> UNYIELDING_IGNORED = effect("unyielding_ignored");

	// Blocks (Utility)
	public static final TagKey<Block> VEIN_MINEABLE = block("vein_mineable");
	public static final TagKey<Block> TIMBER_LOGS = block("timber_logs");
	public static final TagKey<Block> TIMBER_LEAVES = block("timber_leaves");
	public static final TagKey<Block> YIELD_ELIGIBLE = block("yield_eligible");
	public static final TagKey<Block> GROWTH_AURA_CROPS = block("growth_aura_crops");

	private BloomTags() {
	}

	private static TagKey<Item> item(String path) {
		return TagKey.create(Registries.ITEM, BloomLevelUp.id(path));
	}

	private static TagKey<DamageType> damage(String path) {
		return TagKey.create(Registries.DAMAGE_TYPE, BloomLevelUp.id(path));
	}

	private static TagKey<MobEffect> effect(String path) {
		return TagKey.create(Registries.MOB_EFFECT, BloomLevelUp.id(path));
	}

	private static TagKey<Block> block(String path) {
		return TagKey.create(Registries.BLOCK, BloomLevelUp.id(path));
	}
}
