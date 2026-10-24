package com.freshorangeslices.bloomlevelup.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.bettercombat.BetterCombatMod;
import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.config.ServerConfig;
import net.bettercombat.logic.PlayerAttackProperties;
import net.bettercombat.logic.WeaponRegistry;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The only server-side code that touches Better Combat's classes. Never loaded unless Better Combat is installed
 * (every call goes through {@link BetterCombat}, which checks first).
 */
final class BetterCombatBridge {
	private BetterCombatBridge() {
	}

	/** Better Combat's combo counter on a player: 0 or more while its swing is being handled, -1 otherwise. */
	static int comboCount(Player player) {
		return player instanceof PlayerAttackProperties properties ? properties.getComboCount() : -1;
	}

	static BetterCombat.CrowdRule crowdRule() {
		ServerConfig config = BetterCombatMod.config;

		if (config == null) {
			return new BetterCombat.CrowdRule(true, 0.5, 4, false);
		}

		return new BetterCombat.CrowdRule(config.allow_reworked_sweeping, config.reworked_sweeping_maximum_damage_penalty,
				config.reworked_sweeping_extra_target_count, config.allow_vanilla_sweeping);
	}

	static List<BetterCombat.Swing> swings(ItemStack weapon) {
		WeaponAttributes attributes = weapon.isEmpty() ? null : WeaponRegistry.getAttributes(weapon);

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
}
