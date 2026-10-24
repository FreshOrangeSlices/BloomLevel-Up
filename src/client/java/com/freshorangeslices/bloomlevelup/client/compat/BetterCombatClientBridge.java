package com.freshorangeslices.bloomlevelup.client.compat;

import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.client.animation.PlayerAttackAnimatable;
import net.bettercombat.logic.AnimatedHand;
import net.bettercombat.logic.PlayerAttackHelper;
import net.bettercombat.logic.WeaponRegistry;
import net.bettercombat.network.Packets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

/** The only client code that touches Better Combat's classes; never loaded unless it's installed. */
final class BetterCombatClientBridge {
	static final String ONE_HANDED_SLAM = "bettercombat:one_handed_slam";
	static final String TWO_HANDED_SLAM = "bettercombat:two_handed_slam_heavy";
	static final String UPPERCUT = "bettercombat:one_handed_uppercut_right";

	private BetterCombatClientBridge() {
	}

	/** Whether Better Combat runs this item's swings (its own animations and targeting). */
	static boolean handles(ItemStack stack) {
		WeaponAttributes attributes = stack.isEmpty() ? null : WeaponRegistry.getAttributes(stack);
		return attributes != null && attributes.attacks() != null;
	}

	static String slamFor(ItemStack stack) {
		WeaponAttributes attributes = stack.isEmpty() ? null : WeaponRegistry.getAttributes(stack);

		if (attributes == null) {
			return UPPERCUT;
		}

		String category = attributes.category();

		if ("fist".equals(category) || "claw".equals(category)) {
			return UPPERCUT;
		}

		return attributes.isTwoHanded() ? TWO_HANDED_SLAM : ONE_HANDED_SLAM;
	}

	/** Plays the uppercut on this player and tells the server, which shows it to everyone watching. */
	static void playUppercut(LocalPlayer player) {
		float length = PlayerAttackHelper.getAttackCooldownTicksCapped(player);
		float upswing = 0.3F;
		((PlayerAttackAnimatable) player).playAttackAnimation(UPPERCUT, AnimatedHand.MAIN_HAND, length, upswing);

		if (ClientPlayNetworking.canSend(Packets.AttackAnimation.PACKET_ID)) {
			ClientPlayNetworking.send(new Packets.AttackAnimation(player.getId(), AnimatedHand.MAIN_HAND, UPPERCUT, length, upswing, 3.0F,
					Math.max(1, Math.round(length * upswing)), Packets.SwingParticles.EMPTY));
		}
	}
}
