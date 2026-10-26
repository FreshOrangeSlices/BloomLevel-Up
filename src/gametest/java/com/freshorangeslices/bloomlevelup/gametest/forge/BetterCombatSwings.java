package com.freshorangeslices.bloomlevelup.gametest.forge;

import java.lang.reflect.Field;
import java.util.List;

import net.bettercombat.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.bettercombat.network.Packets;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Drives Better Combat's own server handler (1.20.1), the way its client does after a swing's wind-up. The only game
 * test code that touches Better Combat's classes: only called when it's installed.
 */
final class BetterCombatSwings {
	private BetterCombatSwings() {
	}

	/**
	 * One full-strength Better Combat swing at {@code targets} (the first is the one under the crosshair). Fake
	 * players never tick, so the attack cooldown is filled by hand first.
	 */
	static void swing(ServerPlayer player, List<? extends LivingEntity> targets) {
		fullStrength(player);
		boolean vanillaPacket = Packets.C2S_AttackRequest.UseVanillaPacket;
		// Straight to Player.attack, not through the fake player's network handler.
		Packets.C2S_AttackRequest.UseVanillaPacket = false;

		try {
			Packets.C2S_AttackRequest request = new Packets.C2S_AttackRequest(0, false, player.getInventory().selected, List.<Entity>copyOf(targets));
			ServerPlayNetworking.HANDLERS.get(Packets.C2S_AttackRequest.ID).receive(player.getServer(), player, player.connection, request.write(), null);
		} finally {
			Packets.C2S_AttackRequest.UseVanillaPacket = vanillaPacket;
		}
	}

	private static void fullStrength(ServerPlayer player) {
		try {
			Field ticker = LivingEntity.class.getDeclaredField("attackStrengthTicker");
			ticker.setAccessible(true);
			ticker.setInt(player, 200);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Couldn't fill the test player's attack cooldown", e);
		}
	}
}
