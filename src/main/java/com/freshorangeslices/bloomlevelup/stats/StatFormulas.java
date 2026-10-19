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

	/** Main Dodge chance: 0.3 percentage points per point until 40%, then a tenth of that. */
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
}
