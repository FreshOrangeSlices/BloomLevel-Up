package com.freshorangeslices.bloomlevelup.skill.combat;

import java.util.EnumSet;
import java.util.Set;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;

/**
 * Weapon families by item tag ({@code bloomlevelup:weapon/<family>}), so modded weapons join a family by
 * tag and one item may deliberately belong to several. An item in {@code bloomlevelup:weapon/not_<family>} is never
 * in that family, even when the family's tag takes it in through a wider tag (the RPG Series puts its maces, hammers,
 * spears, glaives and double axes in {@code #minecraft:swords}). An empty hand is Unarmed.
 */
public final class Weapons {
	public enum Family {
		SWORD(true),
		AXE(true),
		BOW(false),
		CROSSBOW(false),
		TRIDENT(true),
		MACE(true),
		SPEAR(true),
		UNARMED(true);

		private final boolean melee;
		private final TagKey<Item> tag;
		private final TagKey<Item> excluded;

		Family(boolean melee) {
			String name = name().toLowerCase(java.util.Locale.ROOT);
			this.melee = melee;
			this.tag = TagKey.create(Registries.ITEM, BloomLevelUp.id("weapon/" + name));
			this.excluded = TagKey.create(Registries.ITEM, BloomLevelUp.id("weapon/not_" + name));
		}

		public boolean melee() {
			return melee;
		}

		public TagKey<Item> tag() {
			return tag;
		}

		/** In the family's tag and not in its {@code not_} tag. */
		boolean holds(ItemStack stack) {
			return stack.is(tag) && !stack.is(excluded);
		}
	}

	private Weapons() {
	}

	public static Set<Family> families(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return EnumSet.of(Family.UNARMED);
		}

		EnumSet<Family> out = EnumSet.noneOf(Family.class);

		for (Family family : Family.values()) {
			if (family != Family.UNARMED && family.holds(stack)) {
				out.add(family);
			}
		}

		return out;
	}

	public static boolean is(ItemStack stack, Family family) {
		if (family == Family.UNARMED) {
			return stack == null || stack.isEmpty();
		}

		return stack != null && !stack.isEmpty() && family.holds(stack);
	}

	/** Anything a player can sensibly swing: a tagged melee weapon or an empty hand. */
	public static boolean isMeleeWeapon(ItemStack stack) {
		for (Family family : families(stack)) {
			if (family.melee()) {
				return true;
			}
		}

		return false;
	}
}
