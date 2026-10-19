package com.freshorangeslices.bloomlevelup.skill;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;
import com.freshorangeslices.bloomlevelup.data.BloomServices;
import com.freshorangeslices.bloomlevelup.player.BloomAttachments;
import com.freshorangeslices.bloomlevelup.player.BloomProfile;
import com.freshorangeslices.bloomlevelup.progression.ProgressionService;
import com.freshorangeslices.bloomlevelup.registry.BloomTags;
import com.freshorangeslices.bloomlevelup.skill.armor.ArmorIds;
import com.freshorangeslices.bloomlevelup.stats.StatFormulas;

/**
 * Backup durability saves, applied after vanilla Unbreaking has already had its chance (mixin into the
 * point where an item's durability loss is decided). Each remaining point of damage gets one extra roll.
 * <ul>
 *   <li>Worn armor: Armor / Durability (100 points ≈ a second Unbreaking III).</li>
 *   <li>Shields: Armor / Durability on the shield curve.</li>
 *   <li>Utility tools: Utility / Preservation (100 points = 50% backup save).</li>
 * </ul>
 */
public final class DurabilitySaves {
	public static final String PRESERVATION = BloomLevelUp.key("utility/preservation");

	private DurabilitySaves() {
	}

	public static int adjust(ItemStack stack, int damage, ServerPlayer player) {
		if (player == null || damage <= 0 || stack.isEmpty()) {
			return damage;
		}

		ProgressionService service = BloomServices.progression();
		BloomProfile profile = BloomAttachments.profile(player);
		double chance;

		if (isWornArmor(stack)) {
			chance = StatFormulas.armorDurabilitySaveChance(service.statValue(profile, ArmorIds.DURABILITY));
		} else if (stack.has(DataComponents.BLOCKS_ATTACKS)) {
			chance = StatFormulas.shieldDurabilitySaveChance(service.statValue(profile, ArmorIds.DURABILITY));
		} else if (stack.is(BloomTags.UTILITY_TOOLS)) {
			chance = StatFormulas.preservationSaveChance(service.statValue(profile, PRESERVATION));
		} else {
			return damage;
		}

		if (!(chance > 0.0)) {
			return damage;
		}

		RandomSource random = player.getRandom();
		int remaining = damage;

		for (int i = 0; i < damage; i++) {
			if (random.nextDouble() < chance) {
				remaining--;
			}
		}

		return remaining;
	}

	private static boolean isWornArmor(ItemStack stack) {
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		return equippable != null && equippable.slot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
	}
}
