package com.freshorangeslices.bloomlevelup.definition;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Metadata for a purchasable Major. Behaviour lives in Java handlers keyed by {@link #id()};
 * everything a designer may want to tune (cost, prerequisites, conflicts, icons) lives here.
 *
 * <p>Weapon trees (Sword, Axe, ...) are a shared {@link #group()}; every Major in a tree is an independent
 * purchase with no rank order between them.
 *
 * @param id            full id, e.g. {@code bloomlevelup:utility/vein_mining}
 * @param categoryId    category page the Major appears on (it can be bought with Major Levels from anywhere)
 * @param cost          Major Levels required
 * @param toggleable    whether the owner may switch it off without refunding
 * @param prerequisites Majors that must ALL be owned to purchase this one (ownership only; never revealed in UI).
 *                      An entry starting with {@code #} names a tree instead: owning ANY one Major in that
 *                      group satisfies it, e.g. {@code #bloomlevelup:combat/sword}
 * @param conflicts     Majors that cannot be owned at the same time (made symmetric on load)
 * @param group         optional tree id used to group Majors on the page, e.g. {@code bloomlevelup:combat/sword}
 * @param sort          display order on the category page
 * @param icon          optional texture id; presentation only
 * @param effect        optional visual/sound effect id; presentation only
 * @param flags         free-form behaviour flags read by handlers, e.g. {@code copycat_ineligible}, {@code primary_hit_only}
 */
public record MajorDefinition(
		String id,
		String categoryId,
		int cost,
		boolean toggleable,
		List<String> prerequisites,
		List<String> conflicts,
		Optional<String> group,
		int sort,
		Optional<String> icon,
		Optional<String> effect,
		Set<String> flags
) {
	public static final String FLAG_COPYCAT_INELIGIBLE = "copycat_ineligible";
	public static final String FLAG_PRIMARY_HIT_ONLY = "primary_hit_only";
	public static final String FLAG_DISABLED = "disabled";
	/** Prefix marking a prerequisite as "any one Major in this group". */
	public static final String GROUP_PREFIX = "#";

	public MajorDefinition {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(categoryId, "categoryId");
		prerequisites = List.copyOf(prerequisites);
		conflicts = List.copyOf(conflicts);
		group = group == null ? Optional.empty() : group;
		icon = icon == null ? Optional.empty() : icon;
		effect = effect == null ? Optional.empty() : effect;
		flags = Set.copyOf(flags);
	}

	public static boolean isGroupRef(String prerequisite) {
		return prerequisite.startsWith(GROUP_PREFIX);
	}

	/** The group id named by a {@code #group} prerequisite. */
	public static String groupOf(String prerequisite) {
		return prerequisite.substring(GROUP_PREFIX.length());
	}

	public boolean hasFlag(String flag) {
		return flags.contains(flag);
	}

	/** Returns a copy with a different conflict list (used when conflicts are symmetrised). */
	public MajorDefinition withConflicts(List<String> newConflicts) {
		return new MajorDefinition(id, categoryId, cost, toggleable, prerequisites, newConflicts, group, sort, icon, effect, flags);
	}

	/** Returns a copy with a different prerequisite list (used when invalid references are dropped). */
	public MajorDefinition withPrerequisites(List<String> newPrerequisites) {
		return new MajorDefinition(id, categoryId, cost, toggleable, newPrerequisites, conflicts, group, sort, icon, effect, flags);
	}

	public String translationKey() {
		return "bloom.major." + id.replace(':', '.').replace('/', '.');
	}
}
