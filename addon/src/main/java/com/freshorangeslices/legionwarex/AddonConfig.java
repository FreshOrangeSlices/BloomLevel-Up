package com.freshorangeslices.legionwarex;

import java.util.List;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
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
	private static final ForgeConfigSpec.IntValue PIRATE_BAND_STAY;
	private static final ForgeConfigSpec.IntValue BAND_MAX_SIZE;
	private static final ForgeConfigSpec.IntValue SUMMON_LIFETIME;
	private static final ForgeConfigSpec.IntValue SUMMONS_AT_ONCE;
	private static final ForgeConfigSpec.DoubleValue SABER_DAMAGE;
	private static final ForgeConfigSpec.IntValue SABER_COOLDOWN;
	private static final ForgeConfigSpec.BooleanValue MAINLAND_SPAWN;
	private static final ForgeConfigSpec.DoubleValue CHART_DROP_CHANCE;
	private static final ForgeConfigSpec.IntValue CHART_REACH;
	private static final ForgeConfigSpec.IntValue CHART_ARRIVE;
	private static final ForgeConfigSpec.BooleanValue SENTINELS;
	private static final ForgeConfigSpec.IntValue SENTINEL_EVERY;
	private static final ForgeConfigSpec.DoubleValue SENTINEL_CHANCE;
	private static final ForgeConfigSpec.DoubleValue SENTINEL_VANILLA_SHARE;
	private static final ForgeConfigSpec.DoubleValue SCOUT_SHARE;
	private static final ForgeConfigSpec.IntValue SENTINEL_FROM_DAY;
	private static final ForgeConfigSpec.BooleanValue ILLAGER_CROWDING;
	private static final ForgeConfigSpec.DoubleValue ILLAGER_HALF_LIFE;
	private static final ForgeConfigSpec.DoubleValue ILLAGER_STRENGTH;
	private static final ForgeConfigSpec.IntValue ILLAGER_RADIUS;
	private static final ForgeConfigSpec.BooleanValue ILLAGER_NATURAL;
	private static final ForgeConfigSpec.BooleanValue HARVEST_ALL_YEAR;
	private static final ForgeConfigSpec.BooleanValue MOB_KILL_DROPS;
	private static final ForgeConfigSpec.BooleanValue ZOMBIE_ENDLESS_BUFFS;
	private static final ForgeConfigSpec.BooleanValue MILK_CURES;
	private static final ForgeConfigSpec.ConfigValue<List<? extends String>> DEAD_ENCHANTMENTS;
	private static final ForgeConfigSpec.DoubleValue RARE_GEAR_CHANCE;
	private static final ForgeConfigSpec.BooleanValue THEMED_NIGHTS;
	private static final ForgeConfigSpec.IntValue THEMED_NIGHT_EVERY;
	private static final ForgeConfigSpec.DoubleValue THEMED_NIGHT_SHARE;
	private static final ForgeConfigSpec.DoubleValue THEMED_NIGHT_OFF_CLIMATE;
	/** Enchantments that do nothing in the pack (FreshOrangeSlices, 2026-11-22: "switch off the dead enchantments"). */
	public static final List<String> DEAD_DEFAULT = List.of("spell_engine:spell_infinity", "skarrier_mobs:bluntness", "skarrier_mobs:mass",
			"skarrier_mobs:mobility");
	private static final ForgeConfigSpec.BooleanValue KRAMPUS_ANIMALS;
	private static final ForgeConfigSpec.IntValue KRAMPUS_VILLAGER_KILLS;
	private static final ForgeConfigSpec.ConfigValue<List<? extends String>> BANNED_MOBS;
	/** Never in a world (FreshOrangeSlices, 2026-10-30/31): Born in Chaos's flies, maggots, termites, firelights and baby
	 * skeletons; Dungeon Denizens' orcs, ghouls, alligator gar, boulders and floating eyes. Always applies; the config's
	 * {@code bannedMobs} adds more (so a world made before a mob joined this list still keeps it out). */
	public static final List<String> BANNED_DEFAULT = List.of(
			"born_in_chaos_v1:corpse_fly", "born_in_chaos_v1:maggot", "born_in_chaos_v1:bloody_gadfly", "born_in_chaos_v1:swarmer",
			"born_in_chaos_v1:diamond_termite", "born_in_chaos_v1:firelight", "born_in_chaos_v1:firelight_not_despawn",
			"born_in_chaos_v1:baby_skeleton", "born_in_chaos_v1:baby_skeleton_minion", "born_in_chaos_v1:controlled_baby_skeleton",
			"ddenizens:orc", "ddenizens:orc_shaman", "ddenizens:ghoul", "ddenizens:sewer_ghoul", "ddenizens:alligator_gar",
			"ddenizens:boulder", "ddenizens:beholder", "ddenizens:death_tyrant", "ddenizens:gazer", "ddenizens:spectator");
	private static final ForgeConfigSpec.ConfigValue<List<? extends String>> HARVEST_MOBS;
	/** Born in Chaos's pumpkin folk that spawn in the wild (its Lord Pumpkinhead and the rest come from them or from items). */
	public static final List<String> HARVEST_DEFAULT = List.of("born_in_chaos_v1:mr_pumpkin", "born_in_chaos_v1:mrs_pumpkin",
			"born_in_chaos_v1:senor_pumpkin", "born_in_chaos_v1:pumpkin_bruiser", "born_in_chaos_v1:sir_pumpkinhead");

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
				"(more on harder difficulties and as the area's local difficulty grows), up to bandMaxSize. Every so often one player is",
				"picked and a band may come 24 to 48 blocks from them. /legionwarex band war|pirate [size] calls one for testing.").push("bands");
		BAND_MAX_SIZE = builder
				.comment("The most mobs in a band, war or pirate (FreshOrangeSlices, 2026-11-22: 5). A patrol's own size can reach 8 on Hard.")
				.defineInRange("bandMaxSize", 5, 1, 20);
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
						"on the Kraken's ship), landing near a player on a warm beach: one in the biome tag legionwarex:pirate_beaches",
						"(vanilla's Beach, Biomes O' Plenty's Dune Beach) with a sea in legionwarex:warm_oceans (warm and lukewarm) within",
						"48 blocks. Never close to a Kraken's ship, nor within 128 blocks of another pirate band. They walk 40% faster than",
						"the ship's crew, and rooks wait 1.25 s between swings instead of 2 s. Pirates never despawn by themselves, so the",
						"add-on despawns these ones the way monsters despawn, and they sail off after pirateBandStayTicks.")
				.define("pirateBands", true);
		PIRATE_BAND_EVERY = builder
				.comment("How often a pirate band is tried, in ticks (plus up to a minute); one try in pirateBandChance brings one, so",
						"about one every 20 minutes with the defaults, near a warm beach. (Named pirateBandEveryTicks before 0.4.0, at",
						"4800: renamed so worlds made before take the new pace.)")
				.defineInRange("pirateBandTryEveryTicks", 12000, 200, DAY * 10);
		PIRATE_BAND_CHANCE = builder
				.comment("The chance a try brings a pirate band.")
				.defineInRange("pirateBandChance", 0.5, 0.0, 1.0);
		PIRATE_BAND_FROM_DAY = builder
				.comment("The first day pirate bands come.")
				.defineInRange("pirateBandFromDay", 3, 0, 1000);
		PIRATE_BAND_STAY = builder
				.comment("How long a pirate band stays after landing, in ticks (6000 = 5 minutes): then each pirate sails off once no",
						"player is within 32 blocks of it, also when its chunk loads again. A name-tagged, leashed or riding one stays.",
						"Bands from before 0.4.0 have no landing time and sail off at once.")
				.defineInRange("pirateBandStayTicks", 6000, 200, DAY * 10);
		builder.pop();

		builder.comment("Summons: monsters a player owns (Bosses'Rise's Pirate Saber pirates and Undying Tentacle ghosts, other mods'",
				"summoned monsters). Nothing a player owns ever targets or hits its owner or the rest of that owner's side",
				"(companions, pets, summons), and a summon never targets or hits any player. /legionwarex summons lists the Pirate",
				"Saber's.").push("summons");
		SUMMON_LIFETIME = builder
				.comment("How long the Pirate Saber's two pirates stay, in ticks (1200 = 60 seconds). Ones already in a world get the time",
						"from when they next load.")
				.defineInRange("pirateSaberSummonTicks", 1200, 20, DAY);
		SUMMONS_AT_ONCE = builder
				.comment("How many of the Pirate Saber's pirates a player may have at once; a new charge sends the oldest away.")
				.defineInRange("pirateSaberSummonsAtOnce", 2, 1, 20);
		SABER_DAMAGE = builder
				.comment("The Pirate Saber's attack damage as its tooltip shows it (Bosses'Rise's own: 12).")
				.defineInRange("pirateSaberDamage", 9.0, 1.0, 40.0);
		SABER_COOLDOWN = builder
				.comment("The Pirate Saber's wait after a summon, in ticks (Bosses'Rise's own: 80).")
				.defineInRange("pirateSaberCooldownTicks", 200, 0, 6000);
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

		builder.comment("Sentinels: one harder mob, alone and out by day, that holds its spot and watches, takes on a player who comes within",
				"16 blocks and despawns once players are far away; undead ones wear a helmet the sun can't wear out (it never drops).",
				"Who stands where is the add-on's sentinel table (data/<mod>/legionwarex/sentinels). Sometimes a pillager scout",
				"wanders instead. /legionwarex sentinel [<entity>|all|check] for testing.").push("sentinels");
		SENTINELS = builder
				.comment("Whether sentinels come at all.")
				.define("sentinels", true);
		SENTINEL_EVERY = builder
				.comment("How often each player may get one, in ticks (plus up to a minute); only by day, and never a second while one is",
						"within 96 blocks of them.")
				.defineInRange("sentinelEveryTicks", 6000, 200, DAY * 10);
		SENTINEL_CHANCE = builder
				.comment("The chance a try brings one. With the defaults, about one a day near each player.")
				.defineInRange("sentinelChance", 0.5, 0.0, 1.0);
		SENTINEL_VANILLA_SHARE = builder
				.comment("In vanilla's own biomes a try goes ahead only this share of the time (vanilla's biomes get the smallest share).")
				.defineInRange("sentinelVanillaBiomeShare", 0.6, 0.0, 1.0);
		SCOUT_SHARE = builder
				.comment("The share of tries that bring a pillager scout (where the table has one for that biome) instead of a sentinel.")
				.defineInRange("scoutShare", 0.25, 0.0, 1.0);
		SENTINEL_FROM_DAY = builder
				.comment("The first day sentinels come (day 0 is the world's first).")
				.defineInRange("sentinelFromDay", 1, 0, 1000);
		builder.pop();

		builder.comment("Illagers grow rarer where they keep turning up (FreshOrangeSlices, 2026-11-16: pillagers came too often). Each",
				"vanilla patrol, war band or pillager scout that comes warms the chunk it came to; the next one goes ahead with a chance",
				"of 1 / (1 + strength x warmth), warmth being what's in the chunks within the radius, and warmth halves every few days.",
				"Raids, outposts and other structures' own illagers, spawners, spawn eggs and commands are left alone.",
				"/legionwarex illagers [cool|warm <n>] shows or sets the warmth where it runs.").push("illagers");
		ILLAGER_CROWDING = builder
				.comment("Whether illagers grow rarer where they keep coming.")
				.define("illagerCrowding", true);
		ILLAGER_HALF_LIFE = builder
				.comment("How long warmth takes to halve, in Minecraft days (20 minutes each).")
				.defineInRange("illagerCoolingDays", 3.0, 0.25, 60.0);
		ILLAGER_STRENGTH = builder
				.comment("How much each arrival counts: with 1, one arrival halves the chance of the next, two make it a third, three a quarter.")
				.defineInRange("illagerCrowdingStrength", 1.0, 0.0, 20.0);
		ILLAGER_RADIUS = builder
				.comment("How far an arrival's warmth reaches, in chunks (4 is 64 blocks: a base and its fields).")
				.defineInRange("illagerCrowdingRadius", 4, 0, 16);
		ILLAGER_NATURAL = builder
				.comment("Whether illagers that spawn in the wild like other monsters (Illager Invasion's at night, The Graveyard's in the",
						"dark) are thinned by the same warmth. They don't add to it: there are too many of them, coming and going out of sight.")
				.define("illagerCrowdingNatural", true);
		builder.pop();

		builder.comment("The Harvest Court: Born in Chaos's pumpkin folk. Its spawn rule only lets them out from 25 October to 7 November;",
				"the add-on lets them out all year on the same terms otherwise (the Overworld at night or in the dark, not the deep",
				"dark or mushroom fields, not on peaceful). How many come is the pack's spawn weights (its data pack).",
				"/legionwarex harvest for testing.").push("harvest");
		HARVEST_ALL_YEAR = builder
				.comment("Whether the pumpkin folk spawn all year.")
				.define("harvestCourtAllYear", true);
		HARVEST_MOBS = builder
				.comment("Which mobs spawn all year (entity ids).")
				.defineListAllowEmpty("harvestCourtMobs", HARVEST_DEFAULT, o -> o instanceof String s && ResourceLocation.isValidResourceLocation(s));
		builder.pop();

		builder.comment("Themed nights (FreshOrangeSlices, 2026-11-22/23): now and then a night of one kind of monster (bugs, bones, frost,",
				"heat, demons, the deep, vanilla's own), from a shuffled deck so each comes once before any comes again. Most other",
				"monsters that would spawn naturally that night come as one of the night's instead, with more health; the night's",
				"climate (cold, hot) has them at full strength, elsewhere far fewer. Myths & Legends' Blood Moon keeps its own cycle and",
				"wins a night they'd share: the themed night waits for the next. They despawn like any monster, and what's left of",
				"them after the night goes once no player is within 32 blocks. The nights are data files",
				"(data/<namespace>/legionwarex/nights/<name>.json). /legionwarex night [themes|start <night>|stop|in <days>|show|check].").push("nights");
		THEMED_NIGHTS = builder
				.comment("Whether themed nights come.")
				.define("themedNights", true);
		THEMED_NIGHT_EVERY = builder
				.comment("Days from one themed night to the next (Minecraft days, 20 minutes each).")
				.defineInRange("themedNightEveryDays", 30, 1, 365);
		THEMED_NIGHT_SHARE = builder
				.comment("Of the other monsters that would spawn naturally on a themed night, the share that come as one of the night's",
						"instead (in the night's own climate; elsewhere this times themedNightOffClimateShare).")
				.defineInRange("themedNightShare", 0.8, 0.0, 1.0);
		THEMED_NIGHT_OFF_CLIMATE = builder
				.comment("How strong a cold or hot night (or one of its mobs) is outside its climate: this in temperate land, its square",
						"in the opposite climate (0.2: a fifth, and a twenty-fifth in the heat for frost mobs).")
				.defineInRange("themedNightOffClimateShare", 0.2, 0.0, 1.0);
		builder.pop();

		builder.push("world");
		MOB_KILL_DROPS = builder
				.comment("Whether a mob killed by another mob (an iron golem, a rival, a creeper) drops its loot. Off: it drops nothing unless",
						"a player, their pet or companion hit it at some point in the fight. Deaths with no killer (falls, lava) drop as usual.")
				.define("dropsWhenKilledByMobs", false);
		ZOMBIE_ENDLESS_BUFFS = builder
				.comment("Whether zombies may carry good effects over an hour long (endless strength, regeneration, invisibility, resistance).",
						"Off: refused whoever gives them, and taken off zombies already in the world when they load; the first refusal from",
						"each place in the code is logged as \"LegionWareX buffs\".")
				.define("zombieEndlessBuffs", false);
		MILK_CURES = builder
				.comment("Whether drinking milk always takes Bad Omen off, as in vanilla (something in the pack stopped it), even past a",
						"mod that refuses the removal. The server's start logs what it found as \"LegionWareX milk\".")
				.define("milkCuresBadOmen", true);
		DEAD_ENCHANTMENTS = builder
				.comment("Enchantments switched off: never offered by the enchanting table (books too), villagers or the wandering trader,",
						"nor found on loot (taken off; a book left with none is a plain book). Spell Infinity has no spell in the pack to",
						"use it on (no spell costs an item); Skarrier Mobs' three are for its Morgenstern, which the pack takes out.",
						"/legionwarex enchantments checks them.")
				.defineListAllowEmpty("deadEnchantments", DEAD_DEFAULT, o -> o instanceof String s && ResourceLocation.isValidResourceLocation(s));
		RARE_GEAR_CHANCE = builder
				.comment("The chance a monster drops a piece of #legionwarex:rare_mob_gear it was wearing (Epic Knights' rust series),",
						"flat: Looting doesn't raise it (vanilla drops worn gear 8.5% of the time, plus 1% a Looting level). Only when a",
						"player had a hand in the kill, as in vanilla. A piece the monster picked up still always drops. 0.005 is 1 in 200.")
				.defineInRange("rareMobGearDropChance", 0.005, 0.0, 1.0);
		KRAMPUS_ANIMALS = builder
				.comment("Whether killing animals adds to Born in Chaos's naughtiness (its Krampus meter: warnings, then Krampus comes for",
						"the player). Off: only villagers and wandering traders count. Applied when the server starts.")
				.define("krampusAnimalNaughtiness", false);
		KRAMPUS_VILLAGER_KILLS = builder
				.comment("How many villager or wandering trader kills bring Krampus to the player (a baby villager counts one and a half).",
						"On the way: a far warning at two fifths, two henchmen at three fifths, a close warning at four fifths; after he",
						"comes the count starts again. 0: Born in Chaos's own meter (about the 11th). Applied when the server starts.")
				.defineInRange("krampusVillagerKills", 5, 0, 50);
		BANNED_MOBS = builder
				.comment("More mobs that never join a world, on top of the pack's own list (Born in Chaos's flies, maggots, termites,",
						"firelights and baby skeletons; Dungeon Denizens' orcs, ghouls, alligator gar, boulders and floating eyes), however they'd",
						"come (natural spawns, structures, spawners, other mobs' summons, spawn eggs, commands); ones already in a world",
						"vanish when their chunk loads. Entity ids.")
				.defineListAllowEmpty("bannedMobs", List.of(), o -> o instanceof String s && ResourceLocation.isValidResourceLocation(s));
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
		return get(PIRATE_BAND_EVERY, 12000);
	}

	public static int pirateBandStay() {
		return get(PIRATE_BAND_STAY, 6000);
	}

	public static int bandMaxSize() {
		return get(BAND_MAX_SIZE, 5);
	}

	public static int summonLifetime() {
		return get(SUMMON_LIFETIME, 1200);
	}

	public static int summonsAtOnce() {
		return get(SUMMONS_AT_ONCE, 2);
	}

	public static double pirateSaberDamage() {
		return get(SABER_DAMAGE, 9.0);
	}

	public static int pirateSaberCooldown() {
		return get(SABER_COOLDOWN, 200);
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

	public static boolean sentinels() {
		return get(SENTINELS, true);
	}

	public static int sentinelEvery() {
		return get(SENTINEL_EVERY, 6000);
	}

	public static double sentinelChance() {
		return get(SENTINEL_CHANCE, 0.5);
	}

	public static double sentinelVanillaShare() {
		return get(SENTINEL_VANILLA_SHARE, 0.6);
	}

	public static double scoutShare() {
		return get(SCOUT_SHARE, 0.25);
	}

	public static int sentinelFromDay() {
		return get(SENTINEL_FROM_DAY, 1);
	}

	public static boolean illagerCrowding() {
		return get(ILLAGER_CROWDING, true);
	}

	public static double illagerCoolingDays() {
		return get(ILLAGER_HALF_LIFE, 3.0);
	}

	public static double illagerCrowdingStrength() {
		return get(ILLAGER_STRENGTH, 1.0);
	}

	public static int illagerCrowdingRadius() {
		return get(ILLAGER_RADIUS, 4);
	}

	public static boolean illagerCrowdingNatural() {
		return get(ILLAGER_NATURAL, true);
	}

	public static boolean dropsWhenKilledByMobs() {
		return get(MOB_KILL_DROPS, false);
	}

	public static boolean zombieEndlessBuffs() {
		return get(ZOMBIE_ENDLESS_BUFFS, false);
	}

	public static List<? extends String> deadEnchantments() {
		return get(DEAD_ENCHANTMENTS, DEAD_DEFAULT);
	}

	public static double rareMobGearDropChance() {
		return get(RARE_GEAR_CHANCE, 0.005);
	}

	public static boolean themedNights() {
		return get(THEMED_NIGHTS, true);
	}

	public static int themedNightEveryDays() {
		return get(THEMED_NIGHT_EVERY, 30);
	}

	public static double themedNightShare() {
		return get(THEMED_NIGHT_SHARE, 0.8);
	}

	public static double themedNightOffClimateShare() {
		return get(THEMED_NIGHT_OFF_CLIMATE, 0.2);
	}

	public static boolean milkCuresBadOmen() {
		return get(MILK_CURES, true);
	}

	/** The config's extra bans (the pack's own list is {@link #BANNED_DEFAULT}, always in force). */
	public static List<? extends String> bannedMobs() {
		return get(BANNED_MOBS, List.of());
	}

	public static boolean krampusAnimalNaughtiness() {
		return get(KRAMPUS_ANIMALS, false);
	}

	public static int krampusVillagerKills() {
		return get(KRAMPUS_VILLAGER_KILLS, 5);
	}

	public static boolean harvestAllYear() {
		return get(HARVEST_ALL_YEAR, true);
	}

	public static List<? extends String> harvestMobs() {
		return get(HARVEST_MOBS, HARVEST_DEFAULT);
	}
}
