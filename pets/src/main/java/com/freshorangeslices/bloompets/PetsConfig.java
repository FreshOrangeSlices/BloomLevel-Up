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
	private static final ForgeConfigSpec.DoubleValue CURVE_BASE;
	private static final ForgeConfigSpec.DoubleValue CURVE_LINEAR;
	private static final ForgeConfigSpec.DoubleValue PASSIVE_CURVE;
	private static final ForgeConfigSpec.DoubleValue COMBAT_XP;
	private static final ForgeConfigSpec.DoubleValue KILL_BONUS;
	private static final ForgeConfigSpec.DoubleValue TIME_XP;
	private static final ForgeConfigSpec.IntValue WALK_BLOCKS;
	private static final ForgeConfigSpec.DoubleValue FEED_XP;
	private static final ForgeConfigSpec.IntValue FEED_COOLDOWN;
	private static final ForgeConfigSpec.IntValue REST_SECONDS;
	private static final ForgeConfigSpec.IntValue POST_LEVEL;
	private static final ForgeConfigSpec.IntValue MAX_POSTS;
	private static final ForgeConfigSpec.DoubleValue REST_HEALTH;

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
		builder.push("levels");
		CURVE_BASE = builder.comment("XP a combat pet needs from level L to L+1 = curveBase + curveLinear x L (Bloom's own curve, halved).")
				.defineInRange("curveBase", 25.0, 1.0, 10000.0);
		CURVE_LINEAR = builder.defineInRange("curveLinear", 5.0, 0.0, 10000.0);
		PASSIVE_CURVE = builder.comment("Non-combat pets need this share of a combat pet's XP per level (a quarter of Bloom's curve).")
				.defineInRange("passiveCurveFactor", 0.5, 0.01, 10.0);
		COMBAT_XP = builder.comment("A combat pet's XP per point of real damage it deals, before its kind's multiplier (Bloom's Combat tab gives a player 0.5; a pet a quarter of that, so one takes about 16 hours of fighting to reach 100).")
				.defineInRange("combatXpPerDamage", 0.125, 0.0, 100.0);
		KILL_BONUS = builder.comment("The killing blow adds this share of the XP the pet earned on that kill.")
				.defineInRange("killBonus", 0.5, 0.0, 10.0);
		TIME_XP = builder.comment("A non-combat party pet's XP per minute near its owner (within 32 blocks; about 8 hours to level 100 with walking and feeding).")
				.defineInRange("timeXpPerMinute", 22.0, 0.0, 10000.0);
		WALK_BLOCKS = builder.comment("A non-combat party pet earns 1 XP each time its owner walks this many blocks with it near.")
				.defineInRange("walkBlocksPerXp", 10, 1, 10000);
		FEED_XP = builder.comment("XP for feeding a non-combat party pet its favourite food (sneak + right-click).")
				.defineInRange("feedXp", 10.0, 0.0, 10000.0);
		FEED_COOLDOWN = builder.comment("Seconds between feedings that give XP.")
				.defineInRange("feedCooldownSeconds", 30, 0, 3600);
		builder.pop();
		builder.push("posts");
		POST_LEVEL = builder.comment("The level a pet can be left at a post from (the plugin's level 8).")
				.defineInRange("postLevel", 8, 1, 100);
		MAX_POSTS = builder.comment("How many of a player's pets can keep a post at once.")
				.defineInRange("maxPosts", 3, 0, 100);
		builder.pop();
		builder.push("fainting");
		REST_SECONDS = builder.comment("A party pet that would die faints and rests this long instead (it can't be hurt meanwhile; 5 minutes, FreshOrangeSlices 2026-11-05).")
				.defineInRange("restSeconds", 300, 1, 3600);
		REST_HEALTH = builder.comment("Share of its health it gets back when it's on its feet again.")
				.defineInRange("comesBackWithHealth", 0.5, 0.05, 1.0);
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

	public static double curveBase() {
		return SPEC.isLoaded() ? CURVE_BASE.get() : 25.0;
	}

	public static double curveLinear() {
		return SPEC.isLoaded() ? CURVE_LINEAR.get() : 5.0;
	}

	public static double passiveCurveFactor() {
		return SPEC.isLoaded() ? PASSIVE_CURVE.get() : 0.5;
	}

	public static double combatXpPerDamage() {
		return SPEC.isLoaded() ? COMBAT_XP.get() : 0.125;
	}

	public static double killBonus() {
		return SPEC.isLoaded() ? KILL_BONUS.get() : 0.5;
	}

	public static double timeXpPerMinute() {
		return SPEC.isLoaded() ? TIME_XP.get() : 22.0;
	}

	public static int walkBlocksPerXp() {
		return SPEC.isLoaded() ? WALK_BLOCKS.get() : 10;
	}

	public static double feedXp() {
		return SPEC.isLoaded() ? FEED_XP.get() : 10.0;
	}

	public static int feedCooldownTicks() {
		return (SPEC.isLoaded() ? FEED_COOLDOWN.get() : 30) * 20;
	}

	public static int postLevel() {
		return SPEC.isLoaded() ? POST_LEVEL.get() : 8;
	}

	public static int maxPosts() {
		return SPEC.isLoaded() ? MAX_POSTS.get() : 3;
	}

	public static int restTicks() {
		return (SPEC.isLoaded() ? REST_SECONDS.get() : 300) * 20;
	}

	public static double restHealth() {
		return SPEC.isLoaded() ? REST_HEALTH.get() : 0.5;
	}

	public static List<? extends String> moddedNamespaces() {
		return SPEC.isLoaded() ? MODDED.get() : List.of("migamigos", "zonis_companions");
	}
}
