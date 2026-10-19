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
	/** Iron Stomach: raw, rotten and poisonous food that becomes safe (and at least as filling as cooked chicken). */
	public static final TagKey<Item> FOOD_UNSAFE = item("food/unsafe");
	/** Selective Cleansing: vegetables that clear negative effects... */
	public static final TagKey<Item> FOOD_VEGETABLES = item("food/vegetables");
	/** ...except anything in here (apples and berries, per the design). */
	public static final TagKey<Item> FOOD_CLEANSE_EXCLUDED = item("food/cleanse_excluded");
	/** Hoarder's Pocket: storage items that can't go inside (items with container/bundle contents are always blocked). */
	public static final TagKey<Item> POCKET_BLOCKED = item("pocket_blocked");
	/** Rainbow: ammunition that bows and crossbows don't use up. */
	public static final TagKey<Item> RAINBOW_AMMO = item("rainbow_ammo");

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
	/** Natural dig blocks that can turn up Shovel Mastery treasure. */
	public static final TagKey<Block> EXCAVATION_TREASURE = block("excavation_treasure");
	/** Crops: only give XP / Yield when fully grown, and are never "player placed" (they had to grow). */
	public static final TagKey<Block> MATURE_ONLY = block("mature_only");
	/** Stacked plants (sugar cane, cactus...): the natural segments above the broken one count too. */
	public static final TagKey<Block> XP_COLUMN = block("xp_column");

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
