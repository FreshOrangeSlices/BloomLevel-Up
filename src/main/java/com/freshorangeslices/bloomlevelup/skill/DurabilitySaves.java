package com.freshorangeslices.bloomlevelup.skill;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ToolActions;

import com.freshorangeslices.bloomlevelup.data.BloomServices;
import com.freshorangeslices.bloomlevelup.player.BloomAttachments;
import com.freshorangeslices.bloomlevelup.player.BloomProfile;
import com.freshorangeslices.bloomlevelup.progression.ProgressionService;
import com.freshorangeslices.bloomlevelup.registry.BloomTags;
import com.freshorangeslices.bloomlevelup.skill.armor.ArmorIds;
import com.freshorangeslices.bloomlevelup.skill.utility.UtilityIds;
import com.freshorangeslices.bloomlevelup.stats.StatFormulas;

/**
 * Backup durability saves on top of vanilla Unbreaking (mixin at the start of {@code ItemStack.hurt}): each point
 * of damage gets one extra roll. Unbreaking rolls each point independently too, so the order doesn't matter.
 * <ul>
 *   <li>Worn armor: Armor / Durability (100 points ≈ a second Unbreaking III).</li>
 *   <li>Shields: Armor / Durability on the shield curve.</li>
 *   <li>Utility tools: Utility / Preservation (100 points = 50% backup save).</li>
 * </ul>
 */
public final class DurabilitySaves {
	public static final String PRESERVATION = UtilityIds.PRESERVATION;

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
		} else if (stack.canPerformAction(ToolActions.SHIELD_BLOCK)) {
			chance = StatFormulas.shieldDurabilitySaveChance(service.statValue(profile, ArmorIds.DURABILITY));
		} else if (stack.is(BloomTags.UTILITY_TOOLS)) {
			int preservation = service.statValue(profile, PRESERVATION);

			// Shovel Mastery: +20% Preservation on shovels.
			if (stack.is(ItemTags.SHOVELS) && service.isMajorActive(profile, UtilityIds.SHOVEL_MASTERY)) {
				preservation = StatFormulas.boosted(preservation, BloomServices.config().shovelMasteryBonus);
			}

			chance = StatFormulas.preservationSaveChance(preservation);
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
		return LivingEntity.getEquipmentSlotForItem(stack).getType() == EquipmentSlot.Type.ARMOR;
	}
}
