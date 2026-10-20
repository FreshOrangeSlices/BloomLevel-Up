package com.freshorangeslices.bloomlevelup.skill.combat;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;

/** Ids of the Combat category's stats, Majors and persistent counters. */
public final class CombatIds {
	public static final String POWER = BloomLevelUp.key("combat/power");
	public static final String TEMPO = BloomLevelUp.key("combat/tempo");
	public static final String CRITICAL = BloomLevelUp.key("combat/critical");
	public static final String PENETRATION = BloomLevelUp.key("combat/penetration");
	public static final String LOOTING = BloomLevelUp.key("combat/looting");

	// Sword
	public static final String FULL_SWEEP = BloomLevelUp.key("combat/full_sweep");
	public static final String EXECUTE = BloomLevelUp.key("combat/execute");
	// Axe
	public static final String CHARGE_ATTACK = BloomLevelUp.key("combat/charge_attack");
	public static final String ARMOR_BREAK = BloomLevelUp.key("combat/armor_break");
	public static final String GUARANTEED_CRITICAL = BloomLevelUp.key("combat/guaranteed_critical");
	// Bow
	public static final String MARKSMAN = BloomLevelUp.key("combat/marksman");
	public static final String PIERCING_SHOT = BloomLevelUp.key("combat/piercing_shot");
	public static final String STRAIGHT_SHOT = BloomLevelUp.key("combat/straight_shot");
	// Crossbow
	public static final String REINFORCED_SHOT = BloomLevelUp.key("combat/reinforced_shot");
	public static final String ASSAULT_RIFLE = BloomLevelUp.key("combat/assault_rifle");
	// Trident
	public static final String UNRESTRICTED_RIPTIDE = BloomLevelUp.key("combat/unrestricted_riptide");
	public static final String LIGHTNING_SHOCK = BloomLevelUp.key("combat/lightning_shock");
	public static final String LIGHTNING_SUMMON = BloomLevelUp.key("combat/lightning_summon");
	// Mace
	public static final String WIND_IMPACT = BloomLevelUp.key("combat/wind_impact");
	public static final String STORED_IMPACT = BloomLevelUp.key("combat/stored_impact");
	// Unarmed
	public static final String BRAWLER = BloomLevelUp.key("combat/brawler");
	public static final String COMBO = BloomLevelUp.key("combat/combo");
	public static final String DISARM = BloomLevelUp.key("combat/disarm");
	// Spear
	public static final String LONG_REACH = BloomLevelUp.key("combat/long_reach");
	public static final String IMPALE = BloomLevelUp.key("combat/impale");
	// Universal
	public static final String BATTER_UP = BloomLevelUp.key("combat/batter_up");
	public static final String AURA_SWORD = BloomLevelUp.key("combat/aura_sword");
	public static final String HEAVY_HANDS = BloomLevelUp.key("combat/heavy_hands");
	public static final String CATCH = BloomLevelUp.key("combat/catch");
	public static final String DEATH_FROM_ABOVE = BloomLevelUp.key("combat/death_from_above");
	public static final String PREDATOR = BloomLevelUp.key("combat/predator");
	public static final String BLOODLUST = BloomLevelUp.key("combat/bloodlust");
	public static final String OVERKILL = BloomLevelUp.key("combat/overkill");
	public static final String FIRST_STRIKE = BloomLevelUp.key("combat/first_strike");
	public static final String CROSSBOW_INFINITY = BloomLevelUp.key("combat/crossbow_infinity");
	public static final String COPYCAT = BloomLevelUp.key("combat/copycat");

	// Persistent counters
	/** Stored Impact: the stored bonus, in hundredths of a damage point. */
	public static final String STORED_IMPACT_HUNDREDTHS = STORED_IMPACT + "#stored";
	/** Stored Impact: game time when it can store again (the 5-minute cooldown). */
	public static final String STORED_IMPACT_READY_AT = STORED_IMPACT + "#ready_at";
	/** Game time the current Stored Impact window closes (0 = no window open). */
	public static final String STORED_IMPACT_EXPIRES_AT = STORED_IMPACT + "#expires_at";

	private CombatIds() {
	}
}
