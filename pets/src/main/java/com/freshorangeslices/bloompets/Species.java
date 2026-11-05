package com.freshorangeslices.bloompets;

import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A kind of Bloom pet (data/bloompets/bloompets/species): the animal, its category (combat, pack, speedster, utility),
 * the food that bonds it and how many feedings that takes, how many storage slots it has, and for combat pets the
 * skill tree it uses ({@code skills}, a {@link PetSkills.Kind}, or null) and its XP multiplier (so kinds that hit hard
 * don't level faster than the rest; 0 works it out from the pet's attack damage).
 */
public record Species(ResourceLocation id, EntityType<?> type, String category, Set<Item> foods, List<TagKey<Item>> foodTags,
		int feeds, int storage, String skills, double xpMultiplier) {
	public boolean isFood(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}

		if (foods.contains(stack.getItem())) {
			return true;
		}

		for (TagKey<Item> tag : foodTags) {
			if (stack.is(tag)) {
				return true;
			}
		}

		return false;
	}

	public boolean combat() {
		return "combat".equals(category);
	}

	/** "Polar Bear" from minecraft:polar_bear. */
	public String displayName() {
		return type.getDescription().getString();
	}
}
