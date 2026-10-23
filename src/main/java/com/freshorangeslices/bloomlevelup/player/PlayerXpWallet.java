package com.freshorangeslices.bloomlevelup.player;

import net.minecraft.server.level.ServerPlayer;

import com.freshorangeslices.bloomlevelup.progression.VanillaXpWallet;

/**
 * Adapts a player's vanilla experience to {@link VanillaXpWallet}. The raw total is recomputed from the
 * visible level + bar (vanilla's {@code totalExperience} field drifts after {@code /xp levels} changes), in
 * longs so huge levels can't overflow.
 */
public final class PlayerXpWallet implements VanillaXpWallet {
	/** Highest level the level/points conversions search up to. */
	private static final int MAX_LEVEL = 10_000_000;

	private final ServerPlayer player;

	public PlayerXpWallet(ServerPlayer player) {
		this.player = player;
	}

	@Override
	public long totalPoints() {
		return totalPoints(player.experienceLevel, player.experienceProgress);
	}

	/** A level and bar as raw points (used on both sides). */
	public static long totalPoints(int level, float progress) {
		return pointsForLevel(level) + Math.round((double) Math.max(0.0F, progress) * xpNeededAt(level));
	}

	/**
	 * Takes {@code points} away and sets the level and bar to exactly what is left (vanilla's own
	 * {@code giveExperiencePoints} drifts on big amounts because the bar is a float).
	 */
	@Override
	public void spend(long points) {
		long remaining = Math.max(0L, totalPoints() - Math.max(0L, points));
		int level = levelHolding(remaining);
		long into = remaining - pointsForLevel(level);
		player.experienceProgress = (float) Math.min(0.999999, into / (double) xpNeededAt(level));
		player.totalExperience = (int) Math.min(Integer.MAX_VALUE, remaining);
		player.setExperienceLevels(level); // also tells the client
	}

	/**
	 * The vanilla level whose total XP (from zero) first covers {@code points}: a player at that level or
	 * higher can always pay it. Rounds up, so 2120 points reads as level 36 (level 35 holds only 2045).
	 */
	public static int levelForPoints(long points) {
		int low = 0;
		int high = MAX_LEVEL;

		while (low < high) {
			int mid = (low + high) >>> 1;

			if (pointsForLevel(mid) < points) {
				low = mid + 1;
			} else {
				high = mid;
			}
		}

		return low;
	}

	/** The highest level fully paid for by {@code points} (the bar holds the rest). */
	public static int levelHolding(long points) {
		int level = levelForPoints(points);
		return pointsForLevel(level) > points ? level - 1 : level;
	}

	/** Vanilla's total XP needed to reach {@code level} from zero. */
	public static long pointsForLevel(int level) {
		long l = Math.max(0, level);

		if (l <= 16) {
			return l * l + 6 * l;
		}

		if (l <= 31) {
			return (long) (2.5 * l * l - 40.5 * l + 360);
		}

		return (long) (4.5 * l * l - 162.5 * l + 2220);
	}

	/** Vanilla's XP from {@code level} to the next ({@code Player.getXpNeededForNextLevel}). */
	public static long xpNeededAt(int level) {
		long l = Math.max(0, level);

		if (l >= 30) {
			return 112 + (l - 30) * 9;
		}

		return l >= 15 ? 37 + (l - 15) * 5 : 7 + l * 2;
	}
}
