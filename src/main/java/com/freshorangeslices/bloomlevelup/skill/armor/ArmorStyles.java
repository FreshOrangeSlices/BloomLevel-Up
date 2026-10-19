package com.freshorangeslices.bloomlevelup.skill.armor;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import com.freshorangeslices.bloomlevelup.registry.BloomTags;
import com.freshorangeslices.bloomlevelup.stats.StatFormulas;
import com.freshorangeslices.bloomlevelup.stats.StatFormulas.ArmorStyle;

/**
 * Fitting Armor's style detection. Each worn piece is classified by item tag (Light 1, Medium 2, Heavy 3);
 * the style is the average of the classified pieces, and only exists with all four armor slots filled.
 * Unclassified pieces (an elytra, a pumpkin) still fill their slot but don't count toward the average.
 */
public final class ArmorStyles {
	public static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private ArmorStyles() {
	}

	public static ArmorStyle styleOf(ServerPlayer player) {
		int filled = 0;
		List<Integer> weights = new ArrayList<>(4);

		for (EquipmentSlot slot : ARMOR_SLOTS) {
			ItemStack stack = player.getItemBySlot(slot);

			if (stack.isEmpty()) {
				continue;
			}

			filled++;
			int weight = weightOf(stack);

			if (weight > 0) {
				weights.add(weight);
			}
		}

		int[] array = new int[weights.size()];

		for (int i = 0; i < array.length; i++) {
			array[i] = weights.get(i);
		}

		return StatFormulas.fittingStyle(array, filled);
	}

	public static int weightOf(ItemStack stack) {
		if (stack.is(BloomTags.ARMOR_HEAVY)) {
			return 3;
		} else if (stack.is(BloomTags.ARMOR_MEDIUM)) {
			return 2;
		} else if (stack.is(BloomTags.ARMOR_LIGHT)) {
			return 1;
		}

		return 0;
	}

	public static boolean allArmorSlotsEmpty(ServerPlayer player) {
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			if (!player.getItemBySlot(slot).isEmpty()) {
				return false;
			}
		}

		return true;
	}
}
