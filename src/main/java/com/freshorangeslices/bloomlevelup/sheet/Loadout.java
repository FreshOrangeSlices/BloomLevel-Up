package com.freshorangeslices.bloomlevelup.sheet;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;

import com.freshorangeslices.bloomlevelup.effect.StatEffects;

/**
 * The player's attributes as they would be with {@code item} in the main hand: a copy of each live attribute
 * with the held item's modifiers (its own and its enchantments') swapped for {@code item}'s, and Bloom's
 * hand-dependent modifiers (Brawler, Guaranteed Critical) worked out again. Everything else (effects, armor, other
 * mods) stays as it is. Nothing on the player changes.
 */
final class Loadout {
	private final ServerPlayer player;
	private final ItemStack item;

	Loadout(ServerPlayer player, ItemStack item) {
		this.player = player;
		this.item = item;
	}

	ItemStack item() {
		return item;
	}

	double value(Attribute attribute) {
		return instance(attribute).getValue();
	}

	double base(Attribute attribute) {
		return instance(attribute).getBaseValue();
	}

	/** A detached copy of the attribute with the swap done. */
	AttributeInstance instance(Attribute attribute) {
		AttributeInstance copy = new AttributeInstance(attribute, ignored -> {
		});
		AttributeInstance live = player.getAttribute(attribute);

		if (live == null) {
			copy.setBaseValue(attribute.getDefaultValue());
			return copy;
		}

		copy.replaceFrom(live);
		// Always swap, even for the item in hand: what's on the player may lag a tick behind (or never update, for
		// fake players), and taking off then putting back the same modifiers changes nothing otherwise.
		for (AttributeModifier modifier : player.getMainHandItem().getAttributeModifiers(EquipmentSlot.MAINHAND).get(attribute)) {
			copy.removeModifier(modifier.getId());
		}

		for (AttributeModifier modifier : item.getAttributeModifiers(EquipmentSlot.MAINHAND).get(attribute)) {
			put(copy, modifier);
		}

		// Bloom's own modifiers, worked out again from the profile as it is now (the sheet may be showing the other
		// build, or a point not spent yet) and for this item.
		List<StatEffects.HeldModifier> bloom = new ArrayList<>(StatEffects.profileModifiers(player));
		bloom.addAll(StatEffects.heldModifiers(player, item));

		for (StatEffects.HeldModifier modifier : bloom) {
			if (modifier.attribute() == attribute) {
				copy.removeModifier(modifier.id().uuid());

				if (modifier.amount() != 0.0) {
					copy.addTransientModifier(new AttributeModifier(modifier.id().uuid(), modifier.id().name(), modifier.amount(), modifier.operation()));
				}
			}
		}

		return copy;
	}

	/** The amount of one of Bloom's modifiers on the swapped attribute, or 0 if it isn't there. */
	double modifier(Attribute attribute, StatEffects.ModifierId id) {
		AttributeModifier modifier = instance(attribute).getModifier(id.uuid());
		return modifier == null ? 0.0 : modifier.getAmount();
	}

	/** {@code base} with only {@code item}'s main-hand modifiers for {@code attribute} on it (vanilla's order of operations). */
	static double withItem(ItemStack item, Attribute attribute, double base) {
		AttributeInstance instance = new AttributeInstance(attribute, ignored -> {
		});
		instance.setBaseValue(base);

		for (AttributeModifier modifier : item.getAttributeModifiers(EquipmentSlot.MAINHAND).get(attribute)) {
			put(instance, modifier);
		}

		return instance.getValue();
	}

	/** Adds a modifier, replacing one with the same UUID (1.20.1 refuses a second one with the same UUID). */
	private static void put(AttributeInstance instance, AttributeModifier modifier) {
		UUID id = modifier.getId();

		if (instance.getModifier(id) != null) {
			instance.removeModifier(id);
		}

		instance.addTransientModifier(modifier);
	}
}
