package com.freshorangeslices.bloomlevelup.client.gui;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;
import com.freshorangeslices.bloomlevelup.client.ClientBloomState;
import com.freshorangeslices.bloomlevelup.config.BloomConfig;
import com.freshorangeslices.bloomlevelup.definition.MajorDefinition;
import com.freshorangeslices.bloomlevelup.player.BloomProfile;
import com.freshorangeslices.bloomlevelup.player.OwnedMajor;
import com.freshorangeslices.bloomlevelup.skill.combat.CombatIds;

/**
 * The text of a Major's details window (right-click an owned Major in the hub): what it does, its numbers, how
 * to use it, its catches, and the combos it's part of (only the ones the player owns every piece of).
 *
 * <p>The words live in the lang file under {@code <major key>.detail.what|numbers|how|catch}. Numbers that come
 * from the config are written as tokens and filled from the server's synced config:
 * {@code {field}} as is, {@code {field:pct}} ×100 as a percent, {@code {field:sec}} ticks as seconds,
 * {@code {field:min}} ticks as minutes, {@code {field:x}} as a multiplier.
 */
final class MajorDetails {

	/** One section of the window: a heading and its paragraphs (a "Label: value" paragraph is a numbers row). */
	record Section(Component heading, List<Component> paragraphs, boolean rows) {
	}

	/** A combo: every group needs at least one owned Major. */
	private record Combo(String key, List<Set<String>> needs) {
		boolean owned(BloomProfile profile) {
			return needs.stream().allMatch(group -> group.stream().anyMatch(profile::owns));
		}

		boolean involves(String majorId) {
			return needs.stream().anyMatch(group -> group.contains(majorId));
		}
	}

	private static final List<Combo> COMBOS = List.of(
			new Combo("charged_throw", List.of(Set.of(CombatIds.MASTER_OF_ARMS), Set.of(CombatIds.CATCH), Set.of(CombatIds.HEAVY_HANDS, CombatIds.CHARGE_ATTACK))),
			new Combo("charged_burst", List.of(Set.of(CombatIds.MASTER_OF_ARMS), Set.of(CombatIds.HEAVY_HANDS), Set.of(CombatIds.WIND_IMPACT))),
			new Combo("storm_call", List.of(Set.of(CombatIds.MASTER_OF_ARMS), Set.of(CombatIds.HEAVY_HANDS), Set.of(CombatIds.LIGHTNING_SUMMON))),
			new Combo("mace_throw", List.of(Set.of(CombatIds.MASTER_OF_ARMS), Set.of(CombatIds.CATCH), Set.of(CombatIds.WIND_IMPACT))),
			new Combo("gale_throw", List.of(Set.of(CombatIds.MASTER_OF_ARMS), Set.of(CombatIds.CATCH), Set.of(CombatIds.WIND_IMPACT),
					Set.of(CombatIds.HEAVY_HANDS))));

	private static final Pattern TOKEN = Pattern.compile("\\{([A-Za-z][A-Za-z0-9]*)(?::(pct|sec|min|x))?\\}");
	private static final Set<String> WARNED = new HashSet<>();

	private MajorDetails() {
	}

	/** A heading or name: the normal font, bold. */
	static MutableComponent heading(Component text) {
		return text.copy().withStyle(style -> style.withBold(true));
	}

	/** How much bigger headings are drawn than body text. */
	static final float BIG = 1.5F;

	/** Text drawn {@link #BIG} times as large, its top-left corner at x, y (about 13 pixels tall). */
	static void big(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int color) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(BIG, BIG);
		g.text(font, text, 0, 0, color);
		g.pose().popMatrix();
	}

	/** The window's sections for a Major, in reading order; sections with no text are left out. */
	static List<Section> sections(MajorDefinition major) {
		String base = major.translationKey();
		List<Section> out = new ArrayList<>();

		String what = text(base + ".detail.what");

		if (what == null) {
			what = text(base + ".desc");
		}

		List<Component> whatParagraphs = new ArrayList<>(paragraphs(what));

		if (major.id().equals(CombatIds.COPYCAT)) {
			OwnedMajor copycat = ClientBloomState.profile().majors().get(major.id());
			String source = copycat == null ? "" : copycat.option();
			whatParagraphs.add(source.isEmpty()
					? Component.translatable("bloom.hub.detail.copying.none").withStyle(ChatFormatting.LIGHT_PURPLE)
					: Component.translatable("bloom.hub.detail.copying", ClientBloomState.definitions().major(source)
							.map(m -> Component.translatableWithFallback(m.translationKey() + ".name", source)).orElse(Component.literal(source)))
							.withStyle(ChatFormatting.LIGHT_PURPLE));
		}

		add(out, "bloom.hub.detail.what", whatParagraphs, false);
		add(out, "bloom.hub.detail.numbers", paragraphs(text(base + ".detail.numbers")), true);
		add(out, "bloom.hub.detail.how", paragraphs(text(base + ".detail.how")), false);
		add(out, "bloom.hub.detail.catch", paragraphs(text(base + ".detail.catch")), false);

		List<Component> combos = new ArrayList<>();
		BloomProfile profile = ClientBloomState.profile();
		boolean master = major.id().equals(CombatIds.MASTER_OF_ARMS);

		for (Combo combo : COMBOS) {
			if ((master || combo.involves(major.id())) && combo.owned(profile)) {
				combos.add(heading(Component.translatable("bloom.combo." + combo.key() + ".name")).withStyle(ChatFormatting.LIGHT_PURPLE));
				combos.addAll(paragraphs(text("bloom.combo." + combo.key() + ".desc")));
			}
		}

		add(out, "bloom.hub.detail.combos", combos, false);
		return out;
	}

	private static void add(List<Section> out, String headingKey, List<Component> paragraphs, boolean rows) {
		if (!paragraphs.isEmpty()) {
			out.add(new Section(heading(Component.translatable(headingKey)), paragraphs, rows));
		}
	}

	/** A lang entry (raw, so a "%" in it is just a percent sign) with its config tokens filled in, or null if it doesn't exist. */
	private static String text(String key) {
		Language language = Language.getInstance();
		return language.has(key) ? fill(language.getOrDefault(key)) : null;
	}

	private static List<Component> paragraphs(String text) {
		List<Component> out = new ArrayList<>();

		if (text == null || text.isBlank()) {
			return out;
		}

		for (String line : text.split("\n")) {
			if (!line.isBlank()) {
				out.add(Component.literal(line.strip()));
			}
		}

		return out;
	}

	/** Replaces {@code {field[:unit]}} tokens with the synced config's values. */
	static String fill(String text) {
		Matcher matcher = TOKEN.matcher(text);
		StringBuilder out = new StringBuilder();

		while (matcher.find()) {
			matcher.appendReplacement(out, Matcher.quoteReplacement(value(matcher.group(1), matcher.group(2))));
		}

		matcher.appendTail(out);
		return out.toString();
	}

	private static String value(String field, String unit) {
		BloomConfig config = ClientBloomState.config();
		double value;

		try {
			Field f = BloomConfig.class.getField(field);
			Object raw = f.get(config);

			if (raw instanceof Number number) {
				value = number.doubleValue();
			} else if (raw instanceof Boolean flag) {
				return flag ? "on" : "off";
			} else {
				return String.valueOf(raw);
			}
		} catch (ReflectiveOperationException e) {
			if (WARNED.add(field)) {
				BloomLevelUp.LOGGER.warn("Bloom details: unknown config field {{}}", field);
			}

			return "?";
		}

		if (unit == null) {
			return number(value);
		}

		return switch (unit) {
			case "pct" -> number(value * 100.0) + "%";
			case "sec" -> number(value / 20.0) + " s";
			case "min" -> number(value / 1200.0) + " min";
			case "x" -> "×" + number(value);
			default -> number(value);
		};
	}

	private static String number(double value) {
		if (Math.abs(value - Math.rint(value)) < 1.0E-6) {
			return Long.toString(Math.round(value));
		}

		String text = String.format(Locale.ROOT, "%.2f", value);
		return text.replaceAll("0+$", "").replaceAll("\\.$", "");
	}

	/** Style for the part of a numbers row before its first colon. */
	static Style labelStyle() {
		return Style.EMPTY.withColor(0xE5B567);
	}
}
