package com.freshorangeslices.bloomlevelup.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.bettercombat.api.AttackHand;
import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.config.ServerConfig;
import net.bettercombat.logic.PlayerAttackHelper;
import net.bettercombat.logic.WeaponRegistry;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The only server-side code that touches Better Combat's classes (its 1.20.1 API). Never loaded unless Better Combat
 * is installed (every call goes through {@link BetterCombat}, which checks first).
 */
final class BetterCombatBridge {
	private BetterCombatBridge() {
	}

	static BetterCombat.CrowdRule crowdRule() {
		ServerConfig config = net.bettercombat.BetterCombat.config;

		if (config == null) {
			return new BetterCombat.CrowdRule(true, 0.5, 4, 0.5, false);
		}

		return new BetterCombat.CrowdRule(config.allow_reworked_sweeping, config.reworked_sweeping_maximum_damage_penalty,
				config.reworked_sweeping_extra_target_count, config.reworked_sweeping_enchant_restores, config.allow_vanilla_sweeping);
	}

	static List<BetterCombat.Swing> swings(ItemStack weapon) {
		return swings(null, weapon);
	}

	static List<BetterCombat.Swing> swings(Player player, ItemStack weapon) {
		WeaponAttributes attributes = weapon.isEmpty() ? null : BetterCombatMoves.forPlayer(player, weapon, WeaponRegistry.getAttributes(weapon));

		if (attributes == null || attributes.attacks() == null) {
			return List.of();
		}

		List<BetterCombat.Swing> out = new ArrayList<>();

		for (WeaponAttributes.Attack attack : attributes.attacks()) {
			if (attack == null) {
				continue;
			}

			String shape = attack.hitbox() == null ? "forward_box" : attack.hitbox().name().toLowerCase(Locale.ROOT);
			out.add(new BetterCombat.Swing(shape, attack.angle(), attack.damageMultiplier()));
		}

		return out;
	}

	/** Better Combat's own words for a weapon: its category ("dagger", "sword", "mace"...), whether it's two-handed, its reach. */
	static String describe(ItemStack weapon) {
		WeaponAttributes attributes = weapon.isEmpty() ? null : WeaponRegistry.getAttributes(weapon);
		return attributes == null ? null : attributes.category() + "|" + attributes.isTwoHanded() + "|" + attributes.attackRange();
	}

	static String attackAnimation(Player player, int combo) {
		AttackHand hand = PlayerAttackHelper.getCurrentAttack(player, combo);
		return hand == null || hand.attack() == null ? null : hand.attack().animation();
	}
}
