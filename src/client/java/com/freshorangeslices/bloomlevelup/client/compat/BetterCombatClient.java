package com.freshorangeslices.bloomlevelup.client.compat;

import net.minecraft.client.Minecraft;

/**
 * Better Combat on the client, Forge 1.20.1 build: a stand-in until the compat is ported against Better Combat for
 * 1.20.1 (main's version plays a slam for a charged swing and an overhand throw for Catch!). Until then the weapon-skill
 * key behaves as it does without Better Combat: the charged swing is a normal swing, a throw leaves at once.
 */
public final class BetterCombatClient {
	private BetterCombatClient() {
	}

	/** Runs the charged release's attack. */
	public static void chargedSwing(Minecraft minecraft, Runnable attack) {
		attack.run();
	}

	/** Whether a throw waits for an animation (never yet: the caller sends it at once). */
	public static boolean animatedThrow(Minecraft minecraft, Runnable send) {
		return false;
	}

	/** Every client tick (a waiting throw, once the compat is ported). */
	public static void tick(Minecraft minecraft) {
	}
}
