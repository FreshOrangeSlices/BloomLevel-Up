package com.freshorangeslices.bloomlevelup.player;

import java.util.Objects;

/**
 * A Major the player owns.
 *
 * @param enabled whether the owner currently has it switched on (only meaningful for toggleable Majors)
 * @param paid    Major Levels actually paid, so a refund returns exactly that even if a datapack later changes the cost
 * @param option  a per-Major choice, empty when unused (Copycat: the id of the weapon Major it copies)
 */
public record OwnedMajor(boolean enabled, int paid, String option) {
	public OwnedMajor {
		option = Objects.requireNonNullElse(option, "");
	}

	public OwnedMajor(boolean enabled, int paid) {
		this(enabled, paid, "");
	}

	public OwnedMajor withEnabled(boolean value) {
		return new OwnedMajor(value, paid, option);
	}

	public OwnedMajor withOption(String value) {
		return new OwnedMajor(enabled, paid, value);
	}
}
