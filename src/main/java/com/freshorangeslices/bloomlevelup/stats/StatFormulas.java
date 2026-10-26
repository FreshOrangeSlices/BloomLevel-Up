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

	/**
	 * Expected-value split of a chance that may exceed 100%: whole rolls plus a fractional chance for one
	 * more (e.g. 2.3 = 2 guaranteed, 30% for a third). Used by Shovel Mastery treasure.
	 */
	public static int rollsFromChance(double chance, RandomGenerator random) {
		if (!(chance > 0.0)) {
			return 0;
		}

		int rolls = (int) Math.floor(chance);
		double remainder = chance - rolls;

		if (remainder > 0.0 && random.nextDouble() < remainder) {
			rolls++;
		}

		return rolls;
	}

	/** A stat value boosted by a percentage bonus (Shovel Mastery: 100 points at +20% = 120). */
	public static int boosted(int points, double bonus) {
		return points <= 0 ? 0 : (int) Math.round(points * (1.0 + Math.max(0.0, bonus)));
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

	// ------------------------------------------------------------------ Combat

	/** Critical: a successful vanilla crit deals 1.5x normally, 4x at 100 points (linear, keeps growing). */
	public static double criticalMultiplier(int points) {
		return 1.5 + 2.5 * Math.max(0, points) / 100.0;
	}

	/** Penetration: share of the target's armor ignored. 50% at 100 points, approaching (never reaching) 100%. */
	public static double penetration(int points) {
		return points <= 0 ? 0.0 : points / (points + 100.0);
	}

	/** Looting: virtual Looting levels (about Looting III at 100 points). Fractions are rolled per drop. */
	public static double virtualLooting(int points) {
		return 3.0 * Math.max(0, points) / 100.0;
	}

	/** Marksman: no bonus up to {@code start} blocks, rising linearly to {@code max} at {@code full} blocks. */
	public static double marksmanBonus(double distance, double start, double full, double max) {
		if (!(distance > start) || full <= start) {
			return 0.0;
		}

		return Math.min(1.0, (distance - start) / (full - start)) * max;
	}

	/** Vanilla mace smash bonus for a fall distance (4/block for 3, then 2/block for 5, then 1/block). */
	public static double maceFallBonus(double fallDistance) {
		if (!(fallDistance > 1.5)) {
			return 0.0;
		}

		if (fallDistance <= 3.0) {
			return 4.0 * fallDistance;
		}

		if (fallDistance <= 8.0) {
			return 12.0 + 2.0 * (fallDistance - 3.0);
		}

		return 22.0 + fallDistance - 8.0;
	}

	/** Predator / Bloodlust style stacking bonus: 1 + perStack x stacks, stacks capped. */
	public static double stackMultiplier(int stacks, double perStack, int maxStacks) {
		return 1.0 + Math.max(0.0, perStack) * Math.max(0, Math.min(stacks, maxStacks));
	}

	/** Combo: +perHit per stack, up to cap. */
	public static double comboBonus(int stacks, double perHit, double cap) {
		return Math.min(Math.max(0, stacks) * perHit, cap);
	}

	/** Combat XP for one hit: real damage (never more than the health the target had) x rate x mob multiplier. */
	public static double combatHitXp(double realDamage, double healthBefore, double perDamage, double multiplier) {
		double counted = Math.max(0.0, Math.min(realDamage, healthBefore));
		return counted * perDamage * multiplier;
	}

	// ------------------------------------------------------------------ Misc

	/** Wisdom: vanilla XP from orbs x(1 + 4% per point), so 3x at 50 and 5x at 100. */
	public static double wisdomMultiplier(int points) {
		return 1.0 + 0.04 * Math.max(0, points);
	}

	/**
	 * Wisdom's spell haste (with the RPG Series' Spell Power): +0.5% per point, so +50% at 100. The value is in Spell
	 * Power's haste attribute points (100 = normal speed), added on top.
	 */
	public static double wisdomSpellHaste(int points) {
		return 0.5 * Math.max(0, points);
	}

	/** An XP amount times a multiplier, with the fraction rolled (1 XP at 1.3x = 1, or 2 with 30%). */
	public static int scaledXp(int amount, double multiplier, RandomGenerator random) {
		if (amount <= 0) {
			return 0;
		}

		return rollsFromChance(amount * Math.max(0.0, multiplier), random);
	}

	/** Endurance: food exhaustion x 1/(1 + 0.5 x points/100), so a full bar lasts 1.5x as long at 100. */
	public static double enduranceExhaustionFactor(int points) {
		return 1.0 / (1.0 + 0.005 * Math.max(0, points));
	}

	/** Vanilla: XP points from level L to L+1. */
	public static int vanillaXpForLevel(int level) {
		int l = Math.max(0, level);

		if (l >= 30) {
			return 112 + (l - 30) * 9;
		}

		return l >= 15 ? 37 + (l - 15) * 5 : 7 + l * 2;
	}

	/** Vanilla: total XP points held at a level plus a bar progress of 0..1. */
	public static long vanillaTotalXp(int level, float progress) {
		long l = Math.max(0, level);
		long base;

		if (l <= 16) {
			base = l * l + 6 * l;
		} else if (l <= 31) {
			base = (long) Math.floor(2.5 * l * l - 40.5 * l + 360.0);
		} else {
			base = (long) Math.floor(4.5 * l * l - 162.5 * l + 2220.0);
		}

		double bar = Math.max(0.0, Math.min(1.0, progress));
		return base + Math.round(bar * vanillaXpForLevel(level));
	}

	/**
	 * XP Insurance: the share of the XP a death destroys that comes back on respawn. Vanilla drops
	 * min(7 x level, 100) as orbs you can pick back up; everything else is lost, and that is what's insured.
	 */
	public static int insuredXp(int level, float progress, double share) {
		long total = vanillaTotalXp(level, progress);
		long dropped = Math.min(7L * Math.max(0, level), 100L);
		long lost = Math.max(0L, total - dropped);
		return (int) Math.min(Integer.MAX_VALUE, (long) Math.floor(lost * Math.max(0.0, Math.min(1.0, share))));
	}

	/**
	 * Overdose: the amplifier a re-applied effect lands at. If the same effect is already active at the
	 * incoming level or higher, it climbs one level, but never past incoming + maxBoost (Speed I can be pushed
	 * to Speed III, and more Speed I then refreshes Speed III). A stronger incoming effect simply replaces a
	 * weaker one, as in vanilla; one already above the cap is left to vanilla too.
	 */
	public static int overdoseAmplifier(int active, int incoming, int maxBoost) {
		int cap = incoming + Math.max(0, maxBoost);

		if (active < incoming || active > cap) {
			return incoming;
		}

		return Math.min(active + 1, cap);
	}
}
