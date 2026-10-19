package com.freshorangeslices.bloomlevelup.progression;

import java.util.List;

/**
 * Outcome of a player-requested change (buy, refund, toggle, allocate...). Every request the client
 * can send goes through the server and returns one of these; the client never decides success itself.
 *
 * @param outcome  what happened
 * @param involved related ids, e.g. the conflicting Majors for {@link Outcome#CONFLICT}
 */
public record ActionResult(Outcome outcome, List<String> involved) {
	public enum Outcome {
		SUCCESS,
		UNKNOWN_CATEGORY,
		UNKNOWN_STAT,
		UNKNOWN_MAJOR,
		ALREADY_OWNED,
		NOT_OWNED,
		NOT_TOGGLEABLE,
		/** A hidden prerequisite is missing. The id list is intentionally empty so UI cannot leak it. */
		PREREQUISITE_REQUIRED,
		/** Owning this would clash with owned Majors; {@code involved} lists them for the swap prompt. */
		CONFLICT,
		NOT_ENOUGH_MAJOR_LEVELS,
		NOT_ENOUGH_POINTS,
		NOT_ENOUGH_XP,
		/** Owned Majors require this one; {@code involved} lists them. */
		REQUIRED_BY_OWNED,
		REFUND_DISABLED,
		AT_LEVEL_CAP,
		INVESTMENT_LOCKED,
		INVALID_AMOUNT
	}

	public ActionResult {
		involved = List.copyOf(involved);
	}

	public static ActionResult success() {
		return new ActionResult(Outcome.SUCCESS, List.of());
	}

	public static ActionResult of(Outcome outcome) {
		return new ActionResult(outcome, List.of());
	}

	public static ActionResult of(Outcome outcome, List<String> involved) {
		return new ActionResult(outcome, involved);
	}

	public boolean ok() {
		return outcome == Outcome.SUCCESS;
	}

	public String translationKey() {
		return "bloom.result." + outcome.name().toLowerCase(java.util.Locale.ROOT);
	}
}
