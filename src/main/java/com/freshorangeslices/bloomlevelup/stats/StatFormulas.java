package com.freshorangeslices.bloomlevelup.stats;

import java.util.random.RandomGenerator;

/**
 * Reusable, spec-locked stat math. Pure functions only; event hooks call these and apply the results.
 * Values marked TBD in the design documents are deliberately absent until they are decided.
 */
public final class StatFormulas {
	private StatFormulas() {
	}

	// ---------------------------------------------------------------- Utility

	/** Efficiency: +1% relevant action speed per point (100 = 2.0x). */
	public static double efficiencyMultiplier(int points) {
		return 1.0 + Math.max(0, points) / 100.0;
	}

	/**
	 * Yield: extra full copies of the final (post-Fortune) eligible drop.
	 * floor(points / 100) guaranteed, plus a (points mod 100)% chance for one more.
	 */
	public static int yieldExtraCopies(int points, RandomGenerator random) {
		if (points <= 0) {
			return 0;
		}

		int copies = points / 100;
		int remainder = points % 100;

		if (remainder > 0 && random.nextInt(100) < remainder) {
			copies++;
		}

		return copies;
	}

	/** Preservation: backup durability save chance after vanilla Unbreaking fails = p / (p + 100). */
	public static double preservationSaveChance(int points) {
		return points <= 0 ? 0.0 : points / (points + 100.0);
	}

	// ---------------------------------------------------------------- Armor

	/** Vitality: +10 hearts (20 health) per 100 points, linear forever. Returned in health points. */
	public static double vitalityBonusHealth(int points) {
		return Math.max(0, points) * 0.2;
	}

	/** Defense: +10 innate armor points per 100 points, linear forever. */
	public static double defenseArmor(int points) {
		return Math.max(0, points) * 0.1;
	}

	/** Armor overcap: each armor point above 20 adds 0.5% reduction as its own multiplicative layer, capped for safety. */
	public static double armorOvercapReduction(double totalArmor, double cap) {
		return Math.min(cap, Math.max(0.0, totalArmor - 20.0) * 0.005);
	}

	/** Main Dodge chance: 0.3 percentage points per point until 40%, then a tenth of that (uncapped). */
	public static double dodgeChance(int points) {
		if (points <= 0) {
			return 0.0;
		}

		double softCapPoints = 0.40 / 0.003;

		if (points <= softCapPoints) {
			return points * 0.003;
		}

		return 0.40 + (points - softCapPoints) * 0.0003;
	}

	/** Dodge chance with a safety ceiling so no amount of investment reaches literal immunity. */
	public static double dodgeChance(int points, double cap) {
		return Math.min(cap, dodgeChance(points));
	}

	/** Separate defensive layers multiply: 100 dmg with 30% then 40% reduction leaves 42, never additive 70%. */
	public static double applyReductions(double damage, double... reductions) {
		double result = damage;

		for (double reduction : reductions) {
			result *= 1.0 - Math.min(1.0, Math.max(0.0, reduction));
		}

		return result;
	}

	/** Chance at least one of several independent rolls succeeds (e.g. main Dodge then Light's backup roll). */
	public static double anyOf(double... chances) {
		double miss = 1.0;

		for (double chance : chances) {
			miss *= 1.0 - Math.min(1.0, Math.max(0.0, chance));
		}

		return 1.0 - miss;
	}

	// ---------------------------------------------------------------- Armor: Durability

	/** Durability points as a "virtual Unbreaking level": 100 points = Unbreaking III, linear beyond. */
	public static double virtualUnbreakingLevel(int points) {
		return Math.max(0, points) * 3.0 / 100.0;
	}

	/**
	 * Backup save chance for worn armor once vanilla Unbreaking has already failed. Uses vanilla's armor
	 * Unbreaking curve (armor takes damage with chance 0.6 + 0.4 / (level + 1)), so 100 points behaves like a
	 * second Unbreaking III (30% save) and very high investment approaches 40%.
	 */
	public static double armorDurabilitySaveChance(int points) {
		double level = virtualUnbreakingLevel(points);
		return level <= 0 ? 0.0 : 1.0 - (0.6 + 0.4 / (level + 1.0));
	}

	/**
	 * Backup save chance for shields, tuned separately from worn armor: uses vanilla's tool Unbreaking curve
	 * (damage with chance 1 / (level + 1)), so 100 points = 75% backup save.
	 */
	public static double shieldDurabilitySaveChance(int points) {
		double level = virtualUnbreakingLevel(points);
		return level <= 0 ? 0.0 : 1.0 - 1.0 / (level + 1.0);
	}

	// ---------------------------------------------------------------- Armor: Majors

	/** Last Breath: the n-th lethal attempt in a window (0-based) succeeds with 1, 1/2, 1/4, ... */
	public static double lastBreathChance(int attemptsBefore) {
		return Math.pow(0.5, Math.max(0, attemptsBefore));
	}

	/** Protein!: +1 health (half a heart) per 20 qualifying meals, capped at +20 health (10 hearts). */
	public static double proteinBonusHealth(long meals) {
		return Math.min(20.0, Math.max(0L, meals) / 20L);
	}

	/**
	 * Heavy style knockback resistance that scales down when the player already resists knockback:
	 * combined = existing + 0.5 * (1 - existing). Returns only Heavy's share.
	 */
	public static double heavyKnockbackResistance(double existing) {
		double clamped = Math.min(1.0, Math.max(0.0, existing));
		return 0.5 * (1.0 - clamped);
	}

	/** Fitting Armor styles by average weight of the four worn pieces (Light 1, Medium 2, Heavy 3). */
	public enum ArmorStyle {
		NONE,
		LIGHT,
		MEDIUM,
		HEAVY
	}

	/**
	 * @param weights weight of each classified worn piece (1-3)
	 * @param slotsFilled how many of the four armor slots hold something
	 */
	public static ArmorStyle fittingStyle(int[] weights, int slotsFilled) {
		if (slotsFilled < 4 || weights.length == 0) {
			return ArmorStyle.NONE;
		}

		double sum = 0;

		for (int w : weights) {
			sum += w;
		}

		double average = sum / weights.length;

		if (average < 1.5) {
			return ArmorStyle.LIGHT;
		}

		return average < 2.5 ? ArmorStyle.MEDIUM : ArmorStyle.HEAVY;
	}
}
