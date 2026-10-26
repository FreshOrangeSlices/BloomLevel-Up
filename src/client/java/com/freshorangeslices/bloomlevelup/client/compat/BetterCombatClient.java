package com.freshorangeslices.bloomlevelup.client.compat;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import com.freshorangeslices.bloomlevelup.compat.BetterCombat;

/**
 * Better Combat on the client: a charged swing (Charge Attack / Heavy Hands, letting go of the key) plays a slam
 * instead of the next swing of the weapon's combo: an overhead one-handed slam, the two-handed heavy slam for
 * two-handed weapons, an uppercut for fists. A Catch! throw plays Bloom's overhand throw and lets go as the arm comes
 * over. Without Better Combat (or on a server without it, where Better Combat switches itself off) nothing here does
 * anything.
 *
 * <p>Forge 1.20.1 build: Better Combat's client swing lives in its own mixin on {@code Minecraft}, so instead of
 * changing the animation it picks, Bloom plays the slam over it at once, with the same timing, when a charged release
 * starts a swing.
 */
public final class BetterCombatClient {
	/** The last slam played (client smoke test). */
	private static String lastSlam;
	/** A throw waiting for the arm to come over: what sends it, when (player tick) and from which hotbar slot. */
	private static Runnable pendingThrow;
	private static int throwAt;
	private static int throwSlot;
	/** Throws animated so far (client smoke test). */
	private static int throwsPlayed;

	private BetterCombatClient() {
	}

	/** Whether Better Combat is installed and running in this world (the server has it too). */
	private static boolean active(Minecraft minecraft) {
		return BetterCombat.installed() && minecraft.player != null && BetterCombatClientBridge.enabled();
	}

	/** Runs the charged release's attack; with Better Combat the swing it starts plays the slam. */
	public static void chargedSwing(Minecraft minecraft, Runnable attack) {
		if (!active(minecraft)) {
			attack.run();
			return;
		}

		ItemStack held = minecraft.player.getMainHandItem();

		// Bare fists (and anything else Better Combat doesn't animate) hit at once like vanilla: show the uppercut.
		if (!BetterCombatClientBridge.handles(held)) {
			attack.run();
			BetterCombatClientBridge.playUppercut(minecraft.player);
			lastSlam = BetterCombatClientBridge.UPPERCUT;
			return;
		}

		boolean swinging = BetterCombatClientBridge.upswinging(minecraft);
		attack.run();

		// A swing started (Better Combat's upswing is counting down): play the slam over its first frame.
		if (!swinging && BetterCombatClientBridge.upswinging(minecraft)) {
			lastSlam = BetterCombatClientBridge.playSlam(minecraft);
		}
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
		if (!active(minecraft)) {
			return false;
		}

		if (pendingThrow != null) {
			return true;
		}

		int release = BetterCombatClientBridge.playThrow(minecraft.player);
		pendingThrow = send;
		throwAt = minecraft.player.tickCount + release;
		throwSlot = minecraft.player.getInventory().selected;
		throwsPlayed++;
		return true;
	}

	/** Every client tick: sends a waiting throw when its moment comes; drops it if the weapon was put away. */
	public static void tick(Minecraft minecraft) {
		if (pendingThrow == null) {
			return;
		}

		if (minecraft.player == null || minecraft.player.getInventory().selected != throwSlot) {
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

	/** Whether Better Combat is running in this world (client smoke test). */
	public static boolean activeForTests() {
		return active(Minecraft.getInstance());
	}

	/** Bloom's throw and the slams it plays that Better Combat didn't load (client smoke test; empty when all are there). */
	public static List<String> missingAnimationsForTests() {
		return BetterCombat.installed() ? BetterCombatClientBridge.missingAnimations() : List.of("Better Combat isn't installed");
	}
}
