package com.freshorangeslices.bloomlevelup.definition;

import java.util.Objects;
import java.util.Optional;

/**
 * A levelable stat track inside a category (e.g. Utility / Efficiency).
 *
 * <p>Pure data: no Minecraft types, so the progression core can be unit-tested
 * without a game instance. Ids are {@code namespace:path} strings; a stat's id is
 * {@code <category namespace>:<category path>/<stat name>}.
 *
 * @param id         full stat id, e.g. {@code bloomlevelup:utility/efficiency}
 * @param categoryId owning category id
 * @param toggleable whether the player may switch the stat's effect off without losing points
 * @param sort       display order inside the category page
 * @param icon       optional texture id; presentation only
 */
public record StatDefinition(String id, String categoryId, boolean toggleable, int sort, Optional<String> icon) {
	public StatDefinition {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(categoryId, "categoryId");
		icon = icon == null ? Optional.empty() : icon;
	}

	/** Translation key root, e.g. {@code bloom.stat.bloomlevelup.utility.efficiency}. */
	public String translationKey() {
		return "bloom.stat." + id.replace(':', '.').replace('/', '.');
	}
}
