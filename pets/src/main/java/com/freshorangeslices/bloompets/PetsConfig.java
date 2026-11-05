package com.freshorangeslices.bloompets;

import java.util.List;
import net.minecraftforge.common.ForgeConfigSpec;

/** The server's Bloom Pets settings (world/serverconfig/bloompets-server.toml). */
public final class PetsConfig {
	public static final ForgeConfigSpec SPEC;
	private static final ForgeConfigSpec.IntValue PARTY_COOLDOWN;
	private static final ForgeConfigSpec.DoubleValue FOLLOW_DISTANCE;
	private static final ForgeConfigSpec.DoubleValue TELEPORT_DISTANCE;
	private static final ForgeConfigSpec.DoubleValue HOME_RANGE;
	private static final ForgeConfigSpec.ConfigValue<List<? extends String>> MODDED;

	static {
		ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
		builder.push("party");
		PARTY_COOLDOWN = builder.comment("Seconds between changes to a player's party (joining fills a pet's health, so swapping isn't a free heal).")
				.defineInRange("partyCooldownSeconds", 300, 0, 3600);
		FOLLOW_DISTANCE = builder.comment("The party pet walks to its owner once it's this many blocks away.")
				.defineInRange("followDistance", 6.0, 2.0, 32.0);
		TELEPORT_DISTANCE = builder.comment("Past this many blocks the party pet pops back to its owner's side.")
				.defineInRange("teleportDistance", 24.0, 8.0, 128.0);
		HOME_RANGE = builder.comment("A Bloom pet outside the party stays within this many blocks of where it was left.")
				.defineInRange("homeRange", 16.0, 4.0, 128.0);
		builder.pop();
		builder.push("companions");
		MODDED = builder.comment("Mods whose tamed companions are listed with their owner's pets (always with them, no party place needed). Dragons are left out on purpose.")
				.defineListAllowEmpty("moddedNamespaces", List.of("migamigos", "zonis_companions"), o -> o instanceof String);
		builder.pop();
		SPEC = builder.build();
	}

	private PetsConfig() {
	}

	public static int partyCooldownTicks() {
		return (SPEC.isLoaded() ? PARTY_COOLDOWN.get() : 300) * 20;
	}

	public static double followDistance() {
		return SPEC.isLoaded() ? FOLLOW_DISTANCE.get() : 6.0;
	}

	public static double teleportDistance() {
		return SPEC.isLoaded() ? TELEPORT_DISTANCE.get() : 24.0;
	}

	public static double homeRange() {
		return SPEC.isLoaded() ? HOME_RANGE.get() : 16.0;
	}

	public static List<? extends String> moddedNamespaces() {
		return SPEC.isLoaded() ? MODDED.get() : List.of("migamigos", "zonis_companions");
	}
}
