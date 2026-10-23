package com.freshorangeslices.bloomlevelup.skill.armor;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;

/** Ids of the Armor category's stats, Majors and persistent counters. */
public final class ArmorIds {
	public static final String VITALITY = BloomLevelUp.key("armor/vitality");
	public static final String DEFENSE = BloomLevelUp.key("armor/defense");
	public static final String DODGE = BloomLevelUp.key("armor/dodge");
	public static final String DURABILITY = BloomLevelUp.key("armor/durability");

	public static final String FITTING_ARMOR = BloomLevelUp.key("armor/fitting_armor");
	public static final String PROTEIN = BloomLevelUp.key("armor/protein");
	public static final String IRON_SKIN = BloomLevelUp.key("armor/iron_skin");
	public static final String BRACE = BloomLevelUp.key("armor/brace");
	public static final String PARKOUR = BloomLevelUp.key("armor/parkour");
	public static final String ELEMENTAL_SKIN = BloomLevelUp.key("armor/elemental_skin");
	public static final String THICK_BLOOD = BloomLevelUp.key("armor/thick_blood");
	public static final String VIBRANIUM = BloomLevelUp.key("armor/vibranium");
	public static final String ADAMANTIUM = BloomLevelUp.key("armor/adamantium");
	public static final String REFLECT = BloomLevelUp.key("armor/reflect");
	public static final String REBOUND = BloomLevelUp.key("armor/rebound");
	public static final String UNYIELDING = BloomLevelUp.key("armor/unyielding");
	public static final String LAST_BREATH = BloomLevelUp.key("armor/last_breath");
	public static final String CASTLE_OF_STONE = BloomLevelUp.key("armor/castle_of_stone");
	public static final String ABSORB = BloomLevelUp.key("armor/absorb");
	public static final String TITAN_HIDE = BloomLevelUp.key("armor/titan_hide");

	// Persistent counters (BloomProfile.counters)
	public static final String PROTEIN_MEALS = PROTEIN + "#meals";
	public static final String LAST_BREATH_WINDOW_START = LAST_BREATH + "#window_start";
	public static final String LAST_BREATH_ATTEMPTS = LAST_BREATH + "#attempts";
	public static final String CASTLE_SPENT = CASTLE_OF_STONE + "#spent";
	public static final String CASTLE_UNTIL = CASTLE_OF_STONE + "#until";
	/** 1 while braced (synced, so the HUD can show it). */
	public static final String BRACED = BRACE + "#braced";
	/** Game time from which Brace! can take hold again (it cools down after it ends). */
	public static final String BRACE_READY_AT = BRACE + "#ready_at";

	private ArmorIds() {
	}
}
