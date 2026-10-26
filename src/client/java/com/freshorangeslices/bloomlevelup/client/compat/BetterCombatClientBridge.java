package com.freshorangeslices.bloomlevelup.client.compat;

import java.util.ArrayList;
import java.util.List;

import net.bettercombat.api.AttackHand;
import net.bettercombat.api.MinecraftClient_BetterCombat;
import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.client.animation.AnimationRegistry;
import net.bettercombat.client.animation.PlayerAttackAnimatable;
import net.bettercombat.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.bettercombat.logic.AnimatedHand;
import net.bettercombat.logic.PlayerAttackHelper;
import net.bettercombat.logic.WeaponRegistry;
import net.bettercombat.network.Packets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * The only client code that touches Better Combat's classes (its 1.20.1 API); never loaded unless it's installed.
 * Animations are played on the local player and sent to the server, which shows them to everyone watching.
 */
final class BetterCombatClientBridge {
	static final String ONE_HANDED_SLAM = "bettercombat:one_handed_slam";
	static final String TWO_HANDED_SLAM = "bettercombat:two_handed_slam_heavy";
	static final String UPPERCUT = "bettercombat:one_handed_uppercut_right";
	/** Bloom's own overhand throw (assets/bloomlevelup/attack_animations, where Better Combat 1.20.1 looks). */
	static final String THROW = "bloomlevelup:one_handed_throw";
	/** The throw's length in ticks, and how far into it the arm comes over and lets go. */
	static final float THROW_LENGTH = 10.0F;
	static final float THROW_UPSWING = 0.5F;

	private BetterCombatClientBridge() {
	}

	/** Better Combat switches itself on once the server sends its config (so not on a server without it). */
	static boolean enabled() {
		return net.bettercombat.client.BetterCombatClient.ENABLED;
	}

	/** Whether Better Combat runs this item's swings (its own animations and targeting). */
	static boolean handles(ItemStack stack) {
		WeaponAttributes attributes = stack.isEmpty() ? null : WeaponRegistry.getAttributes(stack);
		return attributes != null && attributes.attacks() != null;
	}

	/** Whether a Better Combat swing is winding up (it hits when this runs out). */
	static boolean upswinging(Minecraft minecraft) {
		return ((MinecraftClient_BetterCombat) minecraft).getUpswingTicks() > 0;
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

	/**
	 * The slam over the swing Better Combat just started: the same hand, length and wind-up, so it lands when the
	 * swing hits. Returns the animation played.
	 */
	static String playSlam(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		AttackHand hand = PlayerAttackHelper.getCurrentAttack(player, ((MinecraftClient_BetterCombat) minecraft).getComboCount());
		String slam = slamFor(player.getMainHandItem());

		if (hand == null) {
			return slam;
		}

		float length = PlayerAttackHelper.getAttackCooldownTicksCapped(player);
		float upswing = (float) hand.upswingRate();
		AnimatedHand animated = AnimatedHand.from(hand.isOffHand(), hand.attributes() != null && hand.attributes().isTwoHanded());
		play(player, slam, animated, length, upswing);
		return slam;
	}

	/**
	 * Plays the overhand throw on this player. Returns the ticks until the arm comes over (when the weapon should
	 * leave the hand).
	 */
	static int playThrow(LocalPlayer player) {
		play(player, THROW, AnimatedHand.MAIN_HAND, THROW_LENGTH, THROW_UPSWING);
		return Math.max(1, Math.round(THROW_LENGTH * THROW_UPSWING));
	}

	/** Plays the uppercut on this player. */
	static void playUppercut(LocalPlayer player) {
		play(player, UPPERCUT, AnimatedHand.MAIN_HAND, PlayerAttackHelper.getAttackCooldownTicksCapped(player), 0.3F);
	}

	private static void play(LocalPlayer player, String animation, AnimatedHand hand, float length, float upswing) {
		((PlayerAttackAnimatable) player).playAttackAnimation(animation, hand, length, upswing);
		ClientPlayNetworking.send(Packets.AttackAnimation.ID, new Packets.AttackAnimation(player.getId(), hand, animation, length, upswing).write());
	}

	static List<String> missingAnimations() {
		List<String> missing = new ArrayList<>();

		for (String animation : List.of(THROW, ONE_HANDED_SLAM, TWO_HANDED_SLAM, UPPERCUT)) {
			if (!AnimationRegistry.animations.containsKey(animation)) {
				missing.add(animation);
			}
		}

		return missing;
	}
}
