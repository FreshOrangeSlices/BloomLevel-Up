package com.freshorangeslices.bloomlevelup.config;

/**
 * Server-side tuning values. Loaded from {@code config/bloomlevelup.json}; every field has a default so
 * an empty or partial file still works. Plain public fields keep Gson (de)serialisation trivial.
 *
 * <p>Values marked TBD in the design docs are given working defaults here so they can be tuned in play
 * without code changes.
 */
public final class BloomConfig {
	/** Natural level cap per category. */
	public int levelCap = 100;

	/** A global Major Level is granted every this many natural levels in a category (one time only). */
	public int majorLevelEvery = 10;

	/** Bloom+ prestige: one bonus Major Level per this many levels reached in a single category before a reset. */
	public int prestigeEvery = 50;

	/** Lifetime prestige bonus cap per category. */
	public int prestigePerCategoryCap = 2;

	/** Lifetime prestige bonus cap across all categories (confirmed +6, even though 4 categories x 2 could reach 8). */
	public int prestigeTotalCap = 6;

	/** XP needed to go from level L to L+1 = base + linear * L + quadratic * L^2 (TBD curve). */
	public double xpCurveBase = 50.0;
	public double xpCurveLinear = 10.0;
	public double xpCurveQuadratic = 0.0;

	/** Skill XP granted per raw vanilla XP point spent on funding a category below the cap (TBD). */
	public double vanillaXpToSkillXp = 1.0;

	/** Raw vanilla XP cost of the n-th post-cap Investment point = base + growth * n (TBD curve). */
	public int investmentBaseCost = 100;
	public int investmentCostGrowth = 10;

	/** Whether naturally earned stat points may be pulled back out and re-allocated freely. */
	public boolean allowStatRefund = true;

	/** Safety ceiling for the armor-overcap damage-reduction layer so it can never reach immunity. */
	public double armorOvercapReductionCap = 0.90;

	// ---------------------------------------------------------------- XP sources

	/** Multiplies every XP award per category, for pacing. 1.0 = as computed. */
	public double utilityXpMultiplier = 1.0;
	public double combatXpMultiplier = 1.0;
	public double armorXpMultiplier = 1.0;
	public double miscXpMultiplier = 1.0;

	/** Armor XP per point of incoming damage (one point = half a heart), before the event multiplier. */
	public double armorXpPerDamage = 4.0;
	/** Locked by the Armor doc: blocked hits 1.5x, dodges 2.0x. */
	public double armorBlockXpMultiplier = 1.5;
	public double armorDodgeXpMultiplier = 2.0;
	/** Utility XP per successful fishing catch and per successful shear (block XP lives in data: bloom/xp/blocks). */
	public double utilityFishingXp = 8.0;
	public double utilityShearXp = 2.5;

	// ---------------------------------------------------------------- Armor tuning

	/** Safety ceiling for the main Dodge roll so no investment reaches literal immunity. */
	public double dodgeChanceCap = 0.90;
	/** Last Breath: length of the diminishing window in ticks (20 minutes) and health restored (5 hearts). */
	public int lastBreathWindowTicks = 24_000;
	public float lastBreathHealth = 10.0F;
	/** Castle of Stone: invincibility length and bed rest needed to recharge (4 seconds, confirmed). */
	public int castleOfStoneTicks = 200;
	public int castleOfStoneRestTicks = 80;
	/** Brace!: how long the player must crouch still before bracing. */
	public int braceDelayTicks = 20;

	// ---------------------------------------------------------------- Utility tuning

	/** Vein Mining / Timber safety limits (blocks processed per activation). */
	public int veinMiningMaxBlocks = 64;
	public int timberMaxLogs = 256;
	/** Holding sneak skips Vein Mining and Timber for that block, without opening the hub. */
	public boolean chainBreakSneakBypass = true;
	/** Shovel Mastery: Utility stats count this much more while using a shovel (0.20 = +20%). */
	public double shovelMasteryBonus = 0.20;
	/** Shovel Mastery treasure: chance per natural dug block for every 100 (boosted) Yield. */
	public double excavationTreasureChancePer100Yield = 0.04;
	/** Growth Aura: radius (horizontal / vertical) and the chance per crop per pulse for an extra growth tick. */
	public int growthAuraRadius = 4;
	public int growthAuraHeight = 2;
	public int growthAuraIntervalTicks = 20;
	public double growthAuraChance = 0.15;
	/** Master Shearing: chance the target stays shearable; extra durability per shear. */
	public double masterShearingChance = 0.5;
	public int masterShearingExtraDurability = 1;
	/** Triple Catch: total loot rolls per catch and extra rod durability per catch. */
	public int tripleCatchRolls = 3;
	public int tripleCatchExtraDurability = 2;

	// ---------------------------------------------------------------- Combat

	/** Combat XP per point of real health damage dealt (capped by the target's remaining health). */
	public double combatXpPerDamage = 0.5;
	/** Kill bonus: this share of the damage XP the killer earned on that target. */
	public double combatKillBonus = 0.5;
	/** Combat stats and Majors work against players; and whether hitting players gives Combat XP. */
	public boolean combatPvp = true;
	public boolean combatPvpXp = true;
	/** Combat XP multipliers per entity type id (bosses, dangerous modded mobs). */
	public java.util.Map<String, Double> combatEntityXpMultipliers = new java.util.LinkedHashMap<>(java.util.Map.of(
			"minecraft:wither", 10.0,
			"minecraft:ender_dragon", 12.0,
			"minecraft:warden", 5.0,
			"minecraft:elder_guardian", 5.0));

	public double executeThreshold = 0.30;
	public double executeBossThreshold = 0.10;
	public double armorBreakRatio = 0.5;
	public double guaranteedCriticalPenalty = 2.0;
	public double marksmanStartDistance = 10.0;
	public double marksmanFullDistance = 40.0;
	public double marksmanMaxBonus = 0.5;
	public int piercingShotLevel = 4;
	public int straightShotMaxTicks = 100;
	public double reinforcedShotDamage = 3.0;
	public double lightningShockRatio = 0.5;
	public double lightningShockRadius = 6.0;
	public int lightningShockCooldownTicks = 40;
	public double storedImpactRatio = 0.5;
	public double storedImpactCap = 20.0;
	public int storedImpactCooldownTicks = 6000;
	public double windImpactRadius = 3.5;
	public double windImpactMinFall = 1.5;
	public double windImpactStrength = 1.2;
	public double brawlerDamage = 6.0;
	public double comboPerHit = 0.25;
	public double comboCap = 5.0;
	public int comboResetTicks = 60;
	public double disarmChance = 0.30;
	public double longReachBlocks = 2.0;
	public double impaleChance = 0.40;
	public int bleedTicks = 60;
	public int auraSwordHits = 5;
	public double auraSwordRadius = 3.5;
	public int auraSwordPulseGapTicks = 10;
	public double predatorPerStack = 0.10;
	public int predatorMaxStacks = 5;
	public int predatorResetTicks = 100;
	public double bloodlustPerStack = 0.08;
	public int bloodlustMaxStacks = 5;
	public int bloodlustTicks = 200;
	public double overkillRatio = 0.6;
	public double overkillRadius = 4.0;
	public int overkillMaxTargets = 3;
	public double firstStrikeBonus = 0.5;
	public double unrestrictedRiptideStrength = 3.0;

	public BloomConfig validated() {
		levelCap = Math.max(1, levelCap);
		majorLevelEvery = Math.max(1, majorLevelEvery);
		prestigeEvery = Math.max(1, prestigeEvery);
		prestigePerCategoryCap = Math.max(0, prestigePerCategoryCap);
		prestigeTotalCap = Math.max(0, prestigeTotalCap);
		xpCurveBase = Math.max(1.0, xpCurveBase);
		xpCurveLinear = Math.max(0.0, xpCurveLinear);
		xpCurveQuadratic = Math.max(0.0, xpCurveQuadratic);
		vanillaXpToSkillXp = Math.max(0.0, vanillaXpToSkillXp);
		investmentBaseCost = Math.max(1, investmentBaseCost);
		investmentCostGrowth = Math.max(0, investmentCostGrowth);
		armorOvercapReductionCap = Math.min(0.99, Math.max(0.0, armorOvercapReductionCap));
		utilityXpMultiplier = Math.max(0.0, utilityXpMultiplier);
		combatXpMultiplier = Math.max(0.0, combatXpMultiplier);
		armorXpMultiplier = Math.max(0.0, armorXpMultiplier);
		miscXpMultiplier = Math.max(0.0, miscXpMultiplier);
		armorXpPerDamage = Math.max(0.0, armorXpPerDamage);
		dodgeChanceCap = Math.min(0.99, Math.max(0.0, dodgeChanceCap));
		lastBreathWindowTicks = Math.max(1, lastBreathWindowTicks);
		lastBreathHealth = Math.max(1.0F, lastBreathHealth);
		castleOfStoneTicks = Math.max(0, castleOfStoneTicks);
		castleOfStoneRestTicks = Math.max(1, castleOfStoneRestTicks);
		braceDelayTicks = Math.max(1, braceDelayTicks);
		utilityFishingXp = Math.max(0.0, utilityFishingXp);
		utilityShearXp = Math.max(0.0, utilityShearXp);
		veinMiningMaxBlocks = Math.max(1, Math.min(4096, veinMiningMaxBlocks));
		timberMaxLogs = Math.max(1, Math.min(4096, timberMaxLogs));
		shovelMasteryBonus = Math.max(0.0, shovelMasteryBonus);
		excavationTreasureChancePer100Yield = Math.max(0.0, excavationTreasureChancePer100Yield);
		growthAuraRadius = Math.max(0, Math.min(16, growthAuraRadius));
		growthAuraHeight = Math.max(0, Math.min(8, growthAuraHeight));
		growthAuraIntervalTicks = Math.max(1, growthAuraIntervalTicks);
		growthAuraChance = Math.min(1.0, Math.max(0.0, growthAuraChance));
		masterShearingChance = Math.min(1.0, Math.max(0.0, masterShearingChance));
		masterShearingExtraDurability = Math.max(0, masterShearingExtraDurability);
		tripleCatchRolls = Math.max(1, Math.min(10, tripleCatchRolls));
		tripleCatchExtraDurability = Math.max(0, tripleCatchExtraDurability);
		combatXpPerDamage = Math.max(0.0, combatXpPerDamage);
		combatKillBonus = Math.max(0.0, combatKillBonus);

		if (combatEntityXpMultipliers == null) {
			combatEntityXpMultipliers = new java.util.LinkedHashMap<>();
		}

		executeThreshold = clamp01(executeThreshold);
		executeBossThreshold = clamp01(executeBossThreshold);
		armorBreakRatio = Math.max(0.0, armorBreakRatio);
		guaranteedCriticalPenalty = Math.max(0.0, guaranteedCriticalPenalty);
		marksmanStartDistance = Math.max(0.0, marksmanStartDistance);
		marksmanFullDistance = Math.max(marksmanStartDistance + 1.0, marksmanFullDistance);
		marksmanMaxBonus = Math.max(0.0, marksmanMaxBonus);
		piercingShotLevel = Math.max(0, Math.min(127, piercingShotLevel));
		straightShotMaxTicks = Math.max(1, straightShotMaxTicks);
		reinforcedShotDamage = Math.max(0.0, reinforcedShotDamage);
		lightningShockRatio = Math.max(0.0, lightningShockRatio);
		lightningShockRadius = Math.max(1.0, Math.min(32.0, lightningShockRadius));
		lightningShockCooldownTicks = Math.max(0, lightningShockCooldownTicks);
		storedImpactRatio = Math.max(0.0, storedImpactRatio);
		storedImpactCap = Math.max(0.0, storedImpactCap);
		storedImpactCooldownTicks = Math.max(0, storedImpactCooldownTicks);
		windImpactRadius = Math.max(0.5, Math.min(16.0, windImpactRadius));
		windImpactMinFall = Math.max(0.0, windImpactMinFall);
		windImpactStrength = Math.max(0.0, windImpactStrength);
		brawlerDamage = Math.max(0.0, brawlerDamage);
		comboPerHit = Math.max(0.0, comboPerHit);
		comboCap = Math.max(0.0, comboCap);
		comboResetTicks = Math.max(1, comboResetTicks);
		disarmChance = clamp01(disarmChance);
		longReachBlocks = Math.max(0.0, longReachBlocks);
		impaleChance = clamp01(impaleChance);
		bleedTicks = Math.max(20, bleedTicks);
		auraSwordHits = Math.max(1, auraSwordHits);
		auraSwordRadius = Math.max(1.0, Math.min(16.0, auraSwordRadius));
		auraSwordPulseGapTicks = Math.max(1, auraSwordPulseGapTicks);
		predatorPerStack = Math.max(0.0, predatorPerStack);
		predatorMaxStacks = Math.max(0, predatorMaxStacks);
		predatorResetTicks = Math.max(1, predatorResetTicks);
		bloodlustPerStack = Math.max(0.0, bloodlustPerStack);
		bloodlustMaxStacks = Math.max(0, bloodlustMaxStacks);
		bloodlustTicks = Math.max(1, bloodlustTicks);
		overkillRatio = Math.max(0.0, overkillRatio);
		overkillRadius = Math.max(1.0, Math.min(16.0, overkillRadius));
		overkillMaxTargets = Math.max(1, overkillMaxTargets);
		firstStrikeBonus = Math.max(0.0, firstStrikeBonus);
		unrestrictedRiptideStrength = Math.max(0.0, unrestrictedRiptideStrength);
		return this;
	}

	private static double clamp01(double value) {
		return Math.min(1.0, Math.max(0.0, value));
	}
}
