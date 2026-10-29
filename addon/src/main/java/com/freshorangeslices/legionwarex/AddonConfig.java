package com.freshorangeslices.legionwarex;

import net.minecraftforge.common.ForgeConfigSpec;

/** The add-on's settings, per world (world/serverconfig/legionwarex-server.toml). */
public final class AddonConfig {
	/** One Minecraft day. */
	public static final int DAY = 24000;

	public static final ForgeConfigSpec SPEC;
	private static final ForgeConfigSpec.IntValue REVIVE_WAIT;
	private static final ForgeConfigSpec.BooleanValue WILD_DESPAWN;

	static {
		ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
		builder.push("companions");
		REVIVE_WAIT = builder
				.comment("How long a fallen companion's keepsake rests before it can bring them back, in ticks (24000 = one Minecraft day, 20 minutes).")
				.defineInRange("reviveWaitTicks", DAY, 0, DAY * 30);
		WILD_DESPAWN = builder
				.comment("Whether wild companions (not befriended, not name-tagged) despawn the way monsters do: at once with no player within 128 blocks, now and then past 32.")
				.define("wildDespawn", true);
		builder.pop();
		SPEC = builder.build();
	}

	private AddonConfig() {
	}

	public static int reviveWait() {
		try {
			return REVIVE_WAIT.get();
		} catch (IllegalStateException notLoaded) {
			return DAY;
		}
	}

	public static boolean wildDespawn() {
		try {
			return WILD_DESPAWN.get();
		} catch (IllegalStateException notLoaded) {
			return true;
		}
	}
}
