package com.freshorangeslices.bloomlevelup.progression;

import java.util.Map;

/**
 * Summary of a Bloom+ full reset.
 *
 * @param prestigeAwarded    bonus Major Levels awarded per category id by this reset
 * @param majorLevelsRefunded Major Levels returned from refunded Majors
 * @param bloomCount          number of Bloom+ resets the player has now done
 */
public record BloomResetResult(Map<String, Integer> prestigeAwarded, int majorLevelsRefunded, int bloomCount) {
	public BloomResetResult {
		prestigeAwarded = Map.copyOf(prestigeAwarded);
	}

	public int totalPrestigeAwarded() {
		int total = 0;

		for (int v : prestigeAwarded.values()) {
			total += v;
		}

		return total;
	}
}
