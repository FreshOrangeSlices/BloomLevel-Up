package com.freshorangeslices.bloomlevelup.gametest;

import java.util.List;

import net.bettercombat.mixin.player.LivingEntityAccessor;
import net.bettercombat.network.Packets;
import net.bettercombat.network.ServerNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Drives Better Combat's own server handler, the way its client does after a swing's wind-up. The only game test
 * code that touches Better Combat's classes: only called when it's installed.
 */
final class BetterCombatSwings {
	private BetterCombatSwings() {
	}

	/**
	 * One full-strength Better Combat swing at {@code targets} (the first is the one under the crosshair). Fake
	 * players never tick, so the attack cooldown is filled by hand first.
	 */
	static void swing(ServerPlayer player, List<? extends LivingEntity> targets) {
		((LivingEntityAccessor) player).betterCombat_setTicksSinceLastAttack(200);
		boolean vanillaPacket = Packets.C2S_AttackRequest.UseVanillaPacket;
		// Straight to Player.attack, not through the fake player's network handler.
		Packets.C2S_AttackRequest.UseVanillaPacket = false;

		try {
			Packets.C2S_AttackRequest request = new Packets.C2S_AttackRequest(0, false, player.getInventory().getSelectedSlot(), targets.get(0),
					List.<Entity>copyOf(targets));
			ServerNetwork.handleAttackRequest(request, player.level().getServer(), player, player.connection);
		} finally {
			Packets.C2S_AttackRequest.UseVanillaPacket = vanillaPacket;
		}
	}
}
