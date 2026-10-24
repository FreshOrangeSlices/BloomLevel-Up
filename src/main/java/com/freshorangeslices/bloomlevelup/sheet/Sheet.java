package com.freshorangeslices.bloomlevelup.sheet;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

import com.freshorangeslices.bloomlevelup.network.SheetPayloads;
import com.freshorangeslices.bloomlevelup.network.SheetPayloads.Row;

/**
 * A stat sheet being filled in: its summary, its full breakdown, the switches it offers and the picture it
 * compares to. Labels are lang keys under {@code bloom.sheet.}; values are formatted here.
 */
final class Sheet {
	static final int GOOD = 0x8CE07A;
	static final int BAD = 0xFF7A6B;
	static final int GOLD = 0xFFD86B;
	static final int PLAIN = 0xFFFFFF;

	private final Set<String> requested;
	final List<Row> summary = new ArrayList<>();
	final List<Row> details = new ArrayList<>();
	final List<SheetPayloads.Toggle> toggles = new ArrayList<>();
	List<Component> targets = List.of();
	int target;
	ItemStack gauge = ItemStack.EMPTY;
	Component gaugeCaption = Component.empty();
	Component gaugeBadge = Component.empty();

	Sheet(Set<String> requested, int target) {
		this.requested = requested;
		this.target = target;
	}

	/** Offers a switch (named {@code bloom.sheet.toggle.<id>}) and returns whether the player has it on. */
	boolean toggle(String id, Object... args) {
		boolean on = requested.contains(id);
		toggles.add(new SheetPayloads.Toggle(id, Component.translatable("bloom.sheet.toggle." + id, args),
				Component.translatable("bloom.sheet.toggle." + id + ".tip", args), on));
		return on;
	}

	/** The picker's choices; returns the chosen index (the first when none was chosen yet, clamped). */
	int targets(List<Component> choices) {
		return targets(choices, 0);
	}

	/** The picker's choices, with the one shown before the player picks (the client asks with -1 at first). */
	int targets(List<Component> choices, int fallback) {
		targets = choices;
		target = Math.max(0, Math.min(target < 0 ? fallback : target, choices.size() - 1));
		return target;
	}

	void summary(Component value, String key, Object... args) {
		summary.add(new Row(Row.ROW, label(key, args), value));
	}

	void heading(String key, Object... args) {
		details.add(new Row(Row.HEADING, label(key, args), Component.empty()));
	}

	void row(Component value, String key, Object... args) {
		details.add(new Row(Row.ROW, label(key, args), value));
	}

	void total(Component value, String key, Object... args) {
		details.add(new Row(Row.TOTAL, label(key, args), value));
	}

	void note(String key, Object... args) {
		details.add(new Row(Row.NOTE, label(key, args), Component.empty()));
	}

	private static MutableComponent label(String key, Object... args) {
		return Component.translatable("bloom.sheet." + key, args);
	}

	// ------------------------------------------------------------------ values

	/** A number with up to two decimals, trailing zeros dropped. */
	static String num(double value) {
		if (!Double.isFinite(value)) {
			return "-";
		}

		if (Math.abs(value - Math.rint(value)) < 0.005) {
			return Long.toString(Math.round(value));
		}

		String text = String.format(Locale.ROOT, "%.2f", value);
		return text.replaceAll("0+$", "").replaceAll("\\.$", "");
	}

	static Component plain(double value) {
		return colored(num(value), PLAIN);
	}

	static Component plain(String text) {
		return colored(text, PLAIN);
	}

	/** "+3" in green, "-2" in red. */
	static Component plus(double value) {
		return value < 0.0 ? colored("-" + num(-value), BAD) : colored("+" + num(value), GOOD);
	}

	/** "×1.5" in gold (red below 1). */
	static Component times(double value) {
		return colored("×" + num(value), value < 1.0 ? BAD : GOLD);
	}

	/** A share as a percent: 0.35 → "35%". */
	static Component pct(double share) {
		return colored(num(share * 100.0) + "%", PLAIN);
	}

	/** A bonus share as a percent: 0.35 → "+35%" (green), -0.2 → "-20%" (red). */
	static Component plusPct(double share) {
		return share < 0.0 ? colored("-" + num(-share * 100.0) + "%", BAD) : colored("+" + num(share * 100.0) + "%", GOOD);
	}

	/** Ticks as seconds: "1.5 s". */
	static Component seconds(double ticks) {
		return colored(num(ticks / 20.0) + " s", PLAIN);
	}

	static Component colored(String text, int color) {
		return Component.literal(text).withStyle(style -> style.withColor(color));
	}

	/** A translated value (for words rather than numbers), plain white. */
	static Component word(String key, Object... args) {
		return Component.translatable("bloom.sheet." + key, args).withStyle(style -> style.withColor(PLAIN));
	}
}
