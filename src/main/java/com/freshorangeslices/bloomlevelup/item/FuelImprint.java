package com.freshorangeslices.bloomlevelup.item;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.ItemStack;

/**
 * Become My Fuel: the harmful effect a weapon carries (item data, so it stays with the weapon whoever holds it).
 * Every hit with the weapon applies it at this amplifier. Kept in the item's NBT under {@code bloomlevelup:fuel}
 * (1.20.1 has no item data components), which also syncs it to clients for the tooltip.
 */
public record FuelImprint(MobEffect effect, int amplifier) {
	public static final String TAG = "bloomlevelup:fuel";

	/** The weapon's imprint, or null. */
	public static FuelImprint get(ItemStack stack) {
		CompoundTag root = stack == null || stack.isEmpty() ? null : stack.getTag();

		if (root == null || !root.contains(TAG, Tag.TAG_COMPOUND)) {
			return null;
		}

		CompoundTag tag = root.getCompound(TAG);
		ResourceLocation id = ResourceLocation.tryParse(tag.getString("effect"));
		MobEffect effect = id == null ? null : BuiltInRegistries.MOB_EFFECT.get(id);
		return effect == null ? null : new FuelImprint(effect, Math.max(0, Math.min(255, tag.getInt("amplifier"))));
	}

	/** Imprints a weapon (replacing any earlier imprint). */
	public static void set(ItemStack stack, FuelImprint fuel) {
		ResourceLocation id = BuiltInRegistries.MOB_EFFECT.getKey(fuel.effect());

		if (id == null) {
			return;
		}

		CompoundTag tag = new CompoundTag();
		tag.putString("effect", id.toString());
		tag.putInt("amplifier", fuel.amplifier());
		stack.getOrCreateTag().put(TAG, tag);
	}
}
