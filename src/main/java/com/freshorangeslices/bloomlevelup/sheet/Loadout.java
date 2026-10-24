package com.freshorangeslices.bloomlevelup.sheet;

import net.minecraft.core.Holder;
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
 * hand-dependent modifiers (Brawler, Guaranteed Critical, Long Reach, Full Sweep, Efficiency) worked out again.
 * Everything else (effects, armor, other mods) stays as it is. Nothing on the player changes.
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

	double value(Holder<Attribute> attribute) {
		return instance(attribute).getValue();
	}

	double base(Holder<Attribute> attribute) {
		return instance(attribute).getBaseValue();
	}

	/** A detached copy of the attribute with the swap done. */
	AttributeInstance instance(Holder<Attribute> attribute) {
		AttributeInstance copy = new AttributeInstance(attribute, ignored -> {
		});
		AttributeInstance live = player.getAttribute(attribute);

		if (live == null) {
			copy.setBaseValue(attribute.value().getDefaultValue());
			return copy;
		}

		copy.replaceFrom(live);
		// Always swap, even for the item in hand: what's on the player may lag a tick behind (or never update, for
		// fake players), and taking off then putting back the same modifiers changes nothing otherwise.
		player.getMainHandItem().forEachModifier(EquipmentSlot.MAINHAND, (which, modifier) -> {
			if (which.value() == attribute.value()) {
				copy.removeModifier(modifier.id());
			}
		});
		item.forEachModifier(EquipmentSlot.MAINHAND, (which, modifier) -> {
			if (which.value() == attribute.value()) {
				copy.addOrUpdateTransientModifier(modifier);
			}
		});

		for (StatEffects.HeldModifier modifier : StatEffects.heldModifiers(player, item)) {
			if (modifier.attribute().value() == attribute.value()) {
				copy.removeModifier(modifier.id());

				if (modifier.amount() != 0.0) {
					copy.addOrUpdateTransientModifier(new AttributeModifier(modifier.id(), modifier.amount(), modifier.operation()));
				}
			}
		}

		return copy;
	}

	/** The sum of one modifier's amount on the swapped attribute, or 0 if it isn't there. */
	double modifier(Holder<Attribute> attribute, net.minecraft.resources.Identifier id) {
		AttributeModifier modifier = instance(attribute).getModifier(id);
		return modifier == null ? 0.0 : modifier.amount();
	}
}
