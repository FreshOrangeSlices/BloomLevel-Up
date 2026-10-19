package com.freshorangeslices.bloomlevelup.progression;

/**
 * Abstraction over the player's vanilla experience, so XP Funding and XP Investment logic can be tested
 * without a Minecraft player. Amounts are raw XP points, never levels (vanilla level costs are nonlinear).
 */
public interface VanillaXpWallet {
	int totalPoints();

	void spend(int points);
}
