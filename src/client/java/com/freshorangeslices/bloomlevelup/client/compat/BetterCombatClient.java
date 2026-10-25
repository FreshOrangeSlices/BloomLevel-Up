package com.freshorangeslices.bloomlevelup.client.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import com.freshorangeslices.bloomlevelup.compat.BetterCombat;

/**
 * Better Combat on the client: a charged swing (Charge Attack / Heavy Hands, letting go of the key) plays a slam
 * instead of the next swing of the weapon's combo: an overhead one-handed slam, the two-handed heavy slam for
 * two-handed weapons, an uppercut for fists. A Catch! throw plays Bloom's overhand throw and lets go as the arm comes
 * over. Without Better Combat nothing here does anything.
 */
public final class BetterCombatClient {
	/** Set for the length of a charged release's attack, so the swing Better Combat starts in it becomes the slam. */
	private static boolean chargedRelease;
	/** The last slam played (client game tests). */
	private static String lastSlam;
	/** A throw waiting for the arm to come over: what sends it, when (player tick) and from which hotbar slot. */
	private static Runnable pendingThrow;
	private static int throwAt;
	private static int throwSlot;
	/** Throws animated so far (client game tests). */
	private static int throwsPlayed;

	private BetterCombatClient() {
	}

	/** Runs the charged release's attack; with Better Combat the swing it starts is the slam. */
	public static void chargedSwing(Minecraft minecraft, Runnable attack) {
		if (!BetterCombat.installed() || minecraft.player == null) {
			attack.run();
			return;
		}

		ItemStack held = minecraft.player.getMainHandItem();
		chargedRelease = true;

		try {
			attack.run();
		} finally {
			chargedRelease = false;
		}

		// Bare fists (and anything else Better Combat doesn't animate) hit at once like vanilla: show the uppercut too.
		if (!BetterCombatClientBridge.handles(held)) {
			BetterCombatClientBridge.playUppercut(minecraft.player);
			lastSlam = BetterCombatClientBridge.UPPERCUT;
		}
	}

	/** The animation for the swing Better Combat is starting (mixin): the slam during a charged release. */
	public static String swingAnimation(String animation) {
		Minecraft minecraft = Minecraft.getInstance();

		if (!chargedRelease || minecraft.player == null) {
			return animation;
		}

		chargedRelease = false;
		lastSlam = BetterCombatClientBridge.slamFor(minecraft.player.getMainHandItem());
		return lastSlam;
	}

	public static String lastSlamForTests() {
		return lastSlam;
	}

	/**
	 * A Catch! throw with Better Combat: plays the overhand throw and sends the throw as the arm comes over (a quarter
	 * second in), so the weapon leaves the hand at the right moment. Returns false without Better Combat (send it now).
	 * A second press while one is on its way is swallowed.
	 */
	public static boolean animatedThrow(Minecraft minecraft, Runnable send) {
		if (!BetterCombat.installed() || minecraft.player == null) {
			return false;
		}

		if (pendingThrow != null) {
			return true;
		}

		int release = BetterCombatClientBridge.playThrow(minecraft.player);
		pendingThrow = send;
		throwAt = minecraft.player.tickCount + release;
		throwSlot = minecraft.player.getInventory().getSelectedSlot();
		throwsPlayed++;
		return true;
	}

	/** Every client tick: sends a waiting throw when its moment comes; drops it if the weapon was put away. */
	public static void tick(Minecraft minecraft) {
		if (pendingThrow == null) {
			return;
		}

		if (minecraft.player == null || minecraft.player.getInventory().getSelectedSlot() != throwSlot) {
			pendingThrow = null;
			return;
		}

		if (minecraft.player.tickCount >= throwAt) {
			Runnable send = pendingThrow;
			pendingThrow = null;
			send.run();
		}
	}

	public static int throwsPlayedForTests() {
		return throwsPlayed;
	}
}
