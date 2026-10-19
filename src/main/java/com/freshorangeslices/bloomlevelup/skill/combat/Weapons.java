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
 * tag and one item may deliberately belong to several. An empty hand is Unarmed.
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

		Family(boolean melee) {
			this.melee = melee;
			this.tag = TagKey.create(Registries.ITEM, BloomLevelUp.id("weapon/" + name().toLowerCase(java.util.Locale.ROOT)));
		}

		public boolean melee() {
			return melee;
		}

		public TagKey<Item> tag() {
			return tag;
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
			if (family != Family.UNARMED && stack.is(family.tag())) {
				out.add(family);
			}
		}

		return out;
	}

	public static boolean is(ItemStack stack, Family family) {
		if (family == Family.UNARMED) {
			return stack == null || stack.isEmpty();
		}

		return stack != null && !stack.isEmpty() && stack.is(family.tag());
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
