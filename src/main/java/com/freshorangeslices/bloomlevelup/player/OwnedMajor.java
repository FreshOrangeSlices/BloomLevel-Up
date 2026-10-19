package com.freshorangeslices.bloomlevelup.player;

/**
 * A Major the player owns.
 *
 * @param enabled whether the owner currently has it switched on (only meaningful for toggleable Majors)
 * @param paid    Major Levels actually paid, so a refund returns exactly that even if a datapack later changes the cost
 */
public record OwnedMajor(boolean enabled, int paid) {
	public OwnedMajor withEnabled(boolean value) {
		return new OwnedMajor(value, paid);
	}
}
