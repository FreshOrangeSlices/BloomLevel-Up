package com.freshorangeslices.bloomlevelup.progression;

/**
 * What a chunk of category XP did, so callers can fire level-up feedback, sounds or toasts.
 *
 * @param levelsGained       natural levels gained by this grant
 * @param majorLevelsGranted one-time Major Level milestones paid out by this grant
 * @param masteredNow        true if this grant reached the cap for the first time ever
 * @param newLevel           level after the grant
 */
public record XpGainResult(int levelsGained, int majorLevelsGranted, boolean masteredNow, int newLevel) {
	public static XpGainResult none(int level) {
		return new XpGainResult(0, 0, false, level);
	}

	public boolean leveledUp() {
		return levelsGained > 0;
	}
}
