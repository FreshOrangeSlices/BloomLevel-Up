package com.freshorangeslices.bloomlevelup.definition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * An immutable, validated snapshot of every category, stat and Major currently loaded.
 *
 * <p>Built from raw (possibly datapack-authored) definitions by {@link #build}. Problems are never fatal:
 * bad references are dropped and reported as warnings so one broken datapack file cannot take the mod down.
 * The snapshot is swapped atomically on {@code /reload}.
 */
public final class DefinitionSet {
	public static final DefinitionSet EMPTY = new DefinitionSet(Map.of(), Map.of(), Map.of(), List.of());

	private final Map<String, CategoryDefinition> categories;
	private final Map<String, StatDefinition> stats;
	private final Map<String, MajorDefinition> majors;
	private final List<String> warnings;

	private DefinitionSet(Map<String, CategoryDefinition> categories, Map<String, StatDefinition> stats,
			Map<String, MajorDefinition> majors, List<String> warnings) {
		this.categories = Collections.unmodifiableMap(categories);
		this.stats = Collections.unmodifiableMap(stats);
		this.majors = Collections.unmodifiableMap(majors);
		this.warnings = List.copyOf(warnings);
	}

	public static DefinitionSet build(Collection<CategoryDefinition> rawCategories, Collection<MajorDefinition> rawMajors) {
		List<String> warnings = new ArrayList<>();

		// Categories (sorted) and their stats.
		List<CategoryDefinition> sortedCategories = new ArrayList<>(rawCategories);
		sortedCategories.sort(Comparator.comparingInt(CategoryDefinition::sort).thenComparing(CategoryDefinition::id));
		Map<String, CategoryDefinition> categories = new LinkedHashMap<>();
		Map<String, StatDefinition> stats = new LinkedHashMap<>();

		for (CategoryDefinition category : sortedCategories) {
			if (categories.containsKey(category.id())) {
				warnings.add("Duplicate category '" + category.id() + "' ignored");
				continue;
			}

			List<StatDefinition> catStats = new ArrayList<>();

			for (StatDefinition stat : category.stats()) {
				if (!stat.categoryId().equals(category.id())) {
					warnings.add("Stat '" + stat.id() + "' declares category '" + stat.categoryId() + "' but is listed in '" + category.id() + "'; ignored");
				} else if (stats.containsKey(stat.id())) {
					warnings.add("Duplicate stat '" + stat.id() + "' ignored");
				} else {
					catStats.add(stat);
					stats.put(stat.id(), stat);
				}
			}

			catStats.sort(Comparator.comparingInt(StatDefinition::sort).thenComparing(StatDefinition::id));
			categories.put(category.id(), new CategoryDefinition(category.id(), category.sort(), category.xpInvestment(), category.icon(), catStats));
		}

		// Majors: drop disabled / orphaned / negative cost.
		Map<String, MajorDefinition> majors = new LinkedHashMap<>();
		List<MajorDefinition> sortedMajors = new ArrayList<>(rawMajors);
		sortedMajors.sort(Comparator.comparingInt(MajorDefinition::sort).thenComparing(MajorDefinition::id));

		for (MajorDefinition major : sortedMajors) {
			if (major.hasFlag(MajorDefinition.FLAG_DISABLED)) {
				continue;
			}

			if (majors.containsKey(major.id())) {
				warnings.add("Duplicate major '" + major.id() + "' ignored");
			} else if (!categories.containsKey(major.categoryId())) {
				warnings.add("Major '" + major.id() + "' references unknown category '" + major.categoryId() + "'; ignored");
			} else if (major.cost() < 0) {
				warnings.add("Major '" + major.id() + "' has negative cost; ignored");
			} else {
				majors.put(major.id(), major);
			}
		}

		// Clean references: unknown ids, self references, prerequisite/conflict overlap.
		for (MajorDefinition major : List.copyOf(majors.values())) {
			List<String> prereqs = new ArrayList<>();

			for (String prereq : new LinkedHashSet<>(major.prerequisites())) {
				if (prereq.equals(major.id())) {
					warnings.add("Major '" + major.id() + "' lists itself as a prerequisite; dropped");
				} else if (!majors.containsKey(prereq)) {
					warnings.add("Major '" + major.id() + "' has unknown prerequisite '" + prereq + "'; dropped");
				} else {
					prereqs.add(prereq);
				}
			}

			List<String> conflicts = new ArrayList<>();

			for (String conflict : new LinkedHashSet<>(major.conflicts())) {
				if (conflict.equals(major.id())) {
					warnings.add("Major '" + major.id() + "' conflicts with itself; dropped");
				} else if (!majors.containsKey(conflict)) {
					warnings.add("Major '" + major.id() + "' has unknown conflict '" + conflict + "'; dropped");
				} else if (prereqs.contains(conflict)) {
					warnings.add("Major '" + major.id() + "' both requires and conflicts with '" + conflict + "'; conflict dropped");
				} else {
					conflicts.add(conflict);
				}
			}

			majors.put(major.id(), major.withPrerequisites(prereqs).withConflicts(conflicts));
		}

		// Symmetrise conflicts: if A conflicts with B, B conflicts with A.
		Map<String, Set<String>> symmetric = new HashMap<>();

		for (MajorDefinition major : majors.values()) {
			symmetric.computeIfAbsent(major.id(), k -> new LinkedHashSet<>()).addAll(major.conflicts());

			for (String other : major.conflicts()) {
				symmetric.computeIfAbsent(other, k -> new LinkedHashSet<>()).add(major.id());
			}
		}

		for (MajorDefinition major : List.copyOf(majors.values())) {
			Set<String> all = symmetric.getOrDefault(major.id(), Set.of());

			if (all.size() != major.conflicts().size()) {
				majors.put(major.id(), major.withConflicts(new ArrayList<>(all)));
			}
		}

		// Prerequisite cycles make Majors impossible to buy; drop every Major on a cycle.
		Set<String> cyclic = findPrerequisiteCycles(majors);

		if (!cyclic.isEmpty()) {
			warnings.add("Prerequisite cycle between " + cyclic + "; those majors are ignored");
			cyclic.forEach(majors::remove);

			for (MajorDefinition major : List.copyOf(majors.values())) {
				List<String> prereqs = new ArrayList<>(major.prerequisites());
				List<String> conflicts = new ArrayList<>(major.conflicts());

				if (prereqs.removeAll(cyclic) | conflicts.removeAll(cyclic)) {
					majors.put(major.id(), major.withPrerequisites(prereqs).withConflicts(conflicts));
				}
			}
		}

		return new DefinitionSet(categories, stats, majors, warnings);
	}

	private static Set<String> findPrerequisiteCycles(Map<String, MajorDefinition> majors) {
		Set<String> onCycle = new HashSet<>();
		Map<String, Integer> state = new HashMap<>(); // 1 = visiting, 2 = done

		for (String id : majors.keySet()) {
			visit(id, majors, state, new ArrayList<>(), onCycle);
		}

		return onCycle;
	}

	private static void visit(String id, Map<String, MajorDefinition> majors, Map<String, Integer> state,
			List<String> path, Set<String> onCycle) {
		Integer s = state.get(id);

		if (s != null && s == 2) {
			return;
		}

		if (s != null && s == 1) {
			int start = path.indexOf(id);
			onCycle.addAll(path.subList(start, path.size()));
			return;
		}

		state.put(id, 1);
		path.add(id);
		MajorDefinition major = majors.get(id);

		if (major != null) {
			for (String prereq : major.prerequisites()) {
				visit(prereq, majors, state, path, onCycle);
			}
		}

		path.remove(path.size() - 1);
		state.put(id, 2);
	}

	public Collection<CategoryDefinition> categories() {
		return categories.values();
	}

	public Optional<CategoryDefinition> category(String id) {
		return Optional.ofNullable(categories.get(id));
	}

	public Optional<StatDefinition> stat(String id) {
		return Optional.ofNullable(stats.get(id));
	}

	public Collection<MajorDefinition> majors() {
		return majors.values();
	}

	public Optional<MajorDefinition> major(String id) {
		return Optional.ofNullable(majors.get(id));
	}

	public List<MajorDefinition> majorsIn(String categoryId) {
		List<MajorDefinition> list = new ArrayList<>();

		for (MajorDefinition major : majors.values()) {
			if (major.categoryId().equals(categoryId)) {
				list.add(major);
			}
		}

		return list;
	}

	/** Majors that list {@code majorId} as a prerequisite. */
	public List<MajorDefinition> dependentsOf(String majorId) {
		List<MajorDefinition> list = new ArrayList<>();

		for (MajorDefinition major : majors.values()) {
			if (major.prerequisites().contains(majorId)) {
				list.add(major);
			}
		}

		return list;
	}

	public List<String> warnings() {
		return warnings;
	}
}
