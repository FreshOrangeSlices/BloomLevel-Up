package com.freshorangeslices.bloomlevelup.player;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Per-player, per-category progression. Mutable; only {@code ProgressionService} should change it.
 *
 * <p>Two separate stat pools exist:
 * <ul>
 *   <li><b>natural</b> points: one per natural level, re-earned after a Bloom+ reset;</li>
 *   <li><b>invested</b> points: bought with vanilla XP after the category's first cap, permanent.</li>
 * </ul>
 */
public final class CategoryProgress {
	private int level;
	private double xp;
	private final Map<String, Integer> natural;
	private int investedPoints;
	private final Map<String, Integer> invested;
	private final Set<String> disabledStats;
	private final Set<String> disabledInvested;
	private boolean mastered;
	private int majorMilestonesClaimed;
	private int prestigeClaimed;

	public CategoryProgress() {
		this(0, 0.0, Map.of(), 0, Map.of(), Set.of(), Set.of(), false, 0, 0);
	}

	public CategoryProgress(int level, double xp, Map<String, Integer> natural, int investedPoints, Map<String, Integer> invested,
			Set<String> disabledStats, Set<String> disabledInvested, boolean mastered, int majorMilestonesClaimed, int prestigeClaimed) {
		this.level = Math.max(0, level);
		this.xp = Math.max(0.0, xp);
		this.natural = new LinkedHashMap<>(natural);
		this.investedPoints = Math.max(0, investedPoints);
		this.invested = new LinkedHashMap<>(invested);
		this.disabledStats = new LinkedHashSet<>(disabledStats);
		this.disabledInvested = new LinkedHashSet<>(disabledInvested);
		this.mastered = mastered;
		this.majorMilestonesClaimed = Math.max(0, majorMilestonesClaimed);
		this.prestigeClaimed = Math.max(0, prestigeClaimed);
	}

	public int level() {
		return level;
	}

	public void setLevel(int level) {
		this.level = level;
	}

	/** XP progress toward the next level (not lifetime XP). */
	public double xp() {
		return xp;
	}

	public void setXp(double xp) {
		this.xp = xp;
	}

	public Map<String, Integer> natural() {
		return natural;
	}

	public int naturalAllocated() {
		return sum(natural);
	}

	/** Natural points earned equal the current natural level. */
	public int naturalUnspent() {
		return Math.max(0, level - naturalAllocated());
	}

	public int investedPoints() {
		return investedPoints;
	}

	public void setInvestedPoints(int investedPoints) {
		this.investedPoints = investedPoints;
	}

	public Map<String, Integer> invested() {
		return invested;
	}

	public int investedUnspent() {
		return Math.max(0, investedPoints - sum(invested));
	}

	/** Stats switched off entirely (both pools). */
	public Set<String> disabledStats() {
		return disabledStats;
	}

	/** Stats whose XP-invested (post-100) points are switched off on their own; the natural points still count. */
	public Set<String> disabledInvested() {
		return disabledInvested;
	}

	/** True once the category has ever reached the level cap; survives every reset. */
	public boolean mastered() {
		return mastered;
	}

	public void setMastered(boolean mastered) {
		this.mastered = mastered;
	}

	/** How many one-time natural Major Level milestones this category has paid out (never reset). */
	public int majorMilestonesClaimed() {
		return majorMilestonesClaimed;
	}

	public void setMajorMilestonesClaimed(int claimed) {
		this.majorMilestonesClaimed = claimed;
	}

	/** Lifetime Bloom+ bonus Major Levels this category has contributed (never reset). */
	public int prestigeClaimed() {
		return prestigeClaimed;
	}

	public void setPrestigeClaimed(int claimed) {
		this.prestigeClaimed = claimed;
	}

	/** Raw points in a stat from both pools, ignoring the on/off toggle. */
	public int rawStat(String statId) {
		return natural.getOrDefault(statId, 0) + invested.getOrDefault(statId, 0);
	}

	public CategoryProgress copy() {
		return new CategoryProgress(level, xp, natural, investedPoints, invested, disabledStats, disabledInvested, mastered,
				majorMilestonesClaimed, prestigeClaimed);
	}

	private static int sum(Map<String, Integer> map) {
		int total = 0;

		for (int v : map.values()) {
			total += v;
		}

		return total;
	}
}
