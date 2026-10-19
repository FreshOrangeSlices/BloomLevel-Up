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
		return this;
	}
}
