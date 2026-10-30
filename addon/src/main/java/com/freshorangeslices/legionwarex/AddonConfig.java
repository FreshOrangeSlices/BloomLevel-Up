package com.freshorangeslices.legionwarex;

import java.util.function.Supplier;
import net.minecraftforge.common.ForgeConfigSpec;

/** The add-on's settings, per world (world/serverconfig/legionwarex-server.toml). */
public final class AddonConfig {
	/** One Minecraft day. */
	public static final int DAY = 24000;

	public static final ForgeConfigSpec SPEC;
	private static final ForgeConfigSpec.IntValue REVIVE_WAIT;
	private static final ForgeConfigSpec.BooleanValue WILD_DESPAWN;
	private static final ForgeConfigSpec.BooleanValue WAR_BANDS;
	private static final ForgeConfigSpec.IntValue WAR_BAND_EVERY;
	private static final ForgeConfigSpec.DoubleValue WAR_BAND_CHANCE;
	private static final ForgeConfigSpec.IntValue WAR_BAND_FROM_DAY;
	private static final ForgeConfigSpec.BooleanValue PIRATE_BANDS;
	private static final ForgeConfigSpec.IntValue PIRATE_BAND_EVERY;
	private static final ForgeConfigSpec.DoubleValue PIRATE_BAND_CHANCE;
	private static final ForgeConfigSpec.IntValue PIRATE_BAND_FROM_DAY;
	private static final ForgeConfigSpec.BooleanValue MAINLAND_SPAWN;
	private static final ForgeConfigSpec.DoubleValue CHART_DROP_CHANCE;
	private static final ForgeConfigSpec.IntValue CHART_REACH;
	private static final ForgeConfigSpec.IntValue CHART_ARRIVE;

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

		builder.comment("Bands that roam by day, the way vanilla's pillager patrols do: a leader and a few followers, as many as a patrol",
				"(more on harder difficulties and as the area's local difficulty grows). Every so often one player is picked and a band",
				"may come 24 to 48 blocks from them. /legionwarex band war|pirate [size] calls one for testing.").push("bands");
		WAR_BANDS = builder
				.comment("Illager war bands by day, in any land biome that has patrols (not near villages): pillagers, vindicators and",
						"Illager Invasion's marauders, provokers, alchemists and the odd basher, led by one carrying the ominous banner",
						"(killing the leader gives Bad Omen, as with a patrol). Needs the doPatrolSpawning game rule.")
				.define("warBands", true);
		WAR_BAND_EVERY = builder
				.comment("How often a war band is tried, in ticks (plus up to a minute). Vanilla's patrols: 12000 at a 1 in 5 chance.")
				.defineInRange("warBandEveryTicks", 4800, 200, DAY * 10);
		WAR_BAND_CHANCE = builder
				.comment("The chance a try brings a band (it also needs daylight and a spot to stand). With the defaults, about one a day.")
				.defineInRange("warBandChance", 0.5, 0.0, 1.0);
		WAR_BAND_FROM_DAY = builder
				.comment("The first day bands come (day 0 is the world's first; vanilla's patrols start on day 5).")
				.defineInRange("warBandFromDay", 5, 0, 1000);
		PIRATE_BANDS = builder
				.comment("Bosses'Rise's pirates on the coasts, day or night: rooks and crossbow pirates led by a rook (the captain stays",
						"on the Kraken's ship), landing on a beach or the shore by the sea near a player (never close to a Kraken's ship).",
						"They walk 40% faster than the ship's crew, and rooks wait 1.25 s between swings instead of 2 s. Pirates never",
						"despawn by themselves, so the add-on despawns these ones the way monsters despawn.")
				.define("pirateBands", true);
		PIRATE_BAND_EVERY = builder
				.comment("How often a pirate band is tried, in ticks (plus up to a minute); it only comes when a player is near a coast.")
				.defineInRange("pirateBandEveryTicks", 4800, 200, DAY * 10);
		PIRATE_BAND_CHANCE = builder
				.comment("The chance a try brings a pirate band.")
				.defineInRange("pirateBandChance", 0.5, 0.0, 1.0);
		PIRATE_BAND_FROM_DAY = builder
				.comment("The first day pirate bands come.")
				.defineInRange("pirateBandFromDay", 3, 0, 1000);
		builder.pop();

		builder.comment("Pirate charts: band pirates drop them, and they turn up in shipwreck and buried-treasure chests (the add-on's loot",
				"modifiers). Reading one leads to the nearest boss lair no one on the world has beaten (the add-on's lair list,",
				"data/<mod>/legionwarex/lairs), as a treasure map; it crumbles once you get there. /legionwarex chart find|lairs|beaten|forget",
				"for testing.").push("charts");
		CHART_DROP_CHANCE = builder
				.comment("The chance a band pirate killed by a player drops a chart (1 in 15 by default).")
				.defineInRange("chartDropChance", 1.0 / 15.0, 0.0, 1.0);
		CHART_REACH = builder
				.comment("How far a chart looks for a lair, in blocks from where it's read. Further takes longer to read (a structure a",
						"tick, each looking further) and leads further.")
				.defineInRange("chartReachBlocks", 3000, 256, 20000);
		CHART_ARRIVE = builder
				.comment("How close to the chart's X counts as there (the chart crumbles), in blocks.")
				.defineInRange("chartArriveBlocks", 32, 4, 256);
		builder.pop();

		builder.push("world");
		MAINLAND_SPAWN = builder
				.comment("A new world's spawn on the mainland: the land nearest the middle of the world that is well inland (no sea",
						"within about 250 blocks), not a beach, river or mountain, and not too high. Off: vanilla's spawn (which can be",
						"a small island). Only matters when a world is made.")
				.define("mainlandSpawn", true);
		builder.pop();
		SPEC = builder.build();
	}

	private AddonConfig() {
	}

	/** A value, or its default before the world's config has loaded (and in game tests that run before it). */
	private static <T> T get(Supplier<T> value, T fallback) {
		try {
			return value.get();
		} catch (IllegalStateException notLoaded) {
			return fallback;
		}
	}

	public static int reviveWait() {
		return get(REVIVE_WAIT, DAY);
	}

	public static boolean wildDespawn() {
		return get(WILD_DESPAWN, true);
	}

	public static boolean warBands() {
		return get(WAR_BANDS, true);
	}

	public static int warBandEvery() {
		return get(WAR_BAND_EVERY, 4800);
	}

	public static double warBandChance() {
		return get(WAR_BAND_CHANCE, 0.5);
	}

	public static int warBandFromDay() {
		return get(WAR_BAND_FROM_DAY, 5);
	}

	public static boolean pirateBands() {
		return get(PIRATE_BANDS, true);
	}

	public static int pirateBandEvery() {
		return get(PIRATE_BAND_EVERY, 4800);
	}

	public static double pirateBandChance() {
		return get(PIRATE_BAND_CHANCE, 0.5);
	}

	public static int pirateBandFromDay() {
		return get(PIRATE_BAND_FROM_DAY, 3);
	}

	public static boolean mainlandSpawn() {
		return get(MAINLAND_SPAWN, true);
	}

	public static double chartDropChance() {
		return get(CHART_DROP_CHANCE, 1.0 / 15.0);
	}

	public static int chartReach() {
		return get(CHART_REACH, 3000);
	}

	public static int chartArriveBlocks() {
		return get(CHART_ARRIVE, 32);
	}
}
