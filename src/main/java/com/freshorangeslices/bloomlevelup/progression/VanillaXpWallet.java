package com.freshorangeslices.bloomlevelup.progression;

/**
 * Abstraction over the player's vanilla experience, so XP Funding and XP Investment logic can be tested
 * without a Minecraft player. Amounts are raw XP points, never levels (vanilla level costs are nonlinear).
 * Points are longs: a player with tens of thousands of levels holds more than an int can count.
 */
public interface VanillaXpWallet {
	long totalPoints();

	void spend(long points);
}
