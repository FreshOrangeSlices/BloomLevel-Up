package com.freshorangeslices.bloomlevelup.definition;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One of the core skill categories (Utility, Combat, Armor, Misc, or a datapack addition).
 *
 * <p>Categories deliberately differ: stat count, XP source and whether a post-100 XP Investment
 * tab exists are all per-category. XP sources are code (see {@code skill/}), everything else is data.
 *
 * @param id           category id, e.g. {@code bloomlevelup:utility}
 * @param sort         order of the category icon in the hub
 * @param xpInvestment whether reaching the level cap unlocks the permanent XP Investment tab
 * @param icon         optional texture id; presentation only
 * @param stats        stat tracks in display order
 */
public record CategoryDefinition(String id, int sort, boolean xpInvestment, Optional<String> icon, List<StatDefinition> stats) {
	public CategoryDefinition {
		Objects.requireNonNull(id, "id");
		icon = icon == null ? Optional.empty() : icon;
		stats = List.copyOf(stats);
	}

	public Optional<StatDefinition> stat(String statId) {
		for (StatDefinition stat : stats) {
			if (stat.id().equals(statId)) {
				return Optional.of(stat);
			}
		}

		return Optional.empty();
	}

	public String translationKey() {
		return "bloom.category." + id.replace(':', '.').replace('/', '.');
	}
}
