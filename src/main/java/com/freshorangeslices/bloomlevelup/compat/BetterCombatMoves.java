package com.freshorangeslices.bloomlevelup.compat;

import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.api.component.BetterCombatDataComponents;
import net.bettercombat.logic.WeaponRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.freshorangeslices.bloomlevelup.skill.combat.CombatIds;
import com.freshorangeslices.bloomlevelup.skill.combat.CombatSkill;
import com.freshorangeslices.bloomlevelup.skill.combat.Weapons;
import com.freshorangeslices.bloomlevelup.skill.combat.Weapons.Family;

/**
 * Batter Up! with Better Combat: a mace or a spear swings like a sword. It takes the sword's combo (the claymore's
 * when it's held in two hands, like spears, glaives and hammers) and keeps everything else of its own: its speed (the
 * item's attack speed, so the wind-up stretches with it), reach, idle pose and grip. A mace still slams with its own
 * swing while falling, so the mace smash keeps working. Up to netherite only: one-off and end-game weapons keep their
 * own swings ({@code Weapons.batterUpMoves}). Both sides pick the swing the same way (the client for the
 * animation and the targets, the server for the damage and the sound). Only loaded when Better Combat is installed.
 */
public final class BetterCombatMoves {
	static final String SWORD = "bettercombat:sword";
	static final String CLAYMORE = "bettercombat:claymore";

	private BetterCombatMoves() {
	}

	/** The swings this player gets with this weapon: the sword's or claymore's with Batter Up!, else its own. */
	public static WeaponAttributes forPlayer(Player player, ItemStack stack, WeaponAttributes attributes) {
		if (player == null || stack == null || stack.isEmpty() || attributes == null || attributes.attacks() == null
				|| Weapons.is(stack, Family.SWORD)) {
			return attributes;
		}

		boolean mace = Weapons.is(stack, Family.MACE);

		if ((!mace && !Weapons.is(stack, Family.SPEAR)) || !Weapons.batterUpMoves(stack) || !CombatSkill.activeAnySide(player, CombatIds.BATTER_UP)) {
			return attributes;
		}

		if (mace && !player.onGround() && player.fallDistance > 1.5) {
			return attributes; // the mace smash
		}

		WeaponAttributes moves = preset(attributes.isTwoHanded() ? CLAYMORE : SWORD);

		if (moves == null || moves.attacks() == null || moves.attacks().length == 0) {
			return attributes;
		}

		return new WeaponAttributes(attributes.attackRange(), attributes.rangeBonus(), attributes.pose(), attributes.offHandPose(),
				attributes.two_handed(), attributes.category(), moves.attacks(),
				moves.trailAppearance() != null ? moves.trailAppearance() : attributes.trailAppearance());
	}

	/** One of Better Combat's presets by id (synced to clients with the rest of its weapon data), or null. */
	static WeaponAttributes preset(String id) {
		ItemStack probe = new ItemStack(Items.STICK);
		probe.set(BetterCombatDataComponents.WEAPON_PRESET_ID, Identifier.parse(id));
		return WeaponRegistry.getAttributes(probe);
	}
}
