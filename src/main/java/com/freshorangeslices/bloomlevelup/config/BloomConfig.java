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

	/**
	 * Lifetime prestige bonus cap across all categories. The General Concept sheet says +8 (2 x 4 categories);
	 * the older Utility sheet said +6. Defaulting to the newer document.
	 */
	public int prestigeTotalCap = 8;

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
		return this;
	}
}
