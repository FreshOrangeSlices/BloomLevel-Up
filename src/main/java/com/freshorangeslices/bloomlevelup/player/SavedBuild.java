package com.freshorangeslices.bloomlevelup.player;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The build that isn't in use (the second preset): which Majors it owns (with their on/off switches and
 * options), and per category its natural stat points and which stats are switched off. XP-invested points and
 * everything else in the profile are shared by both builds.
 */
public final class SavedBuild {
	private final Map<String, OwnedMajor> majors;
	private final Map<String, Map<String, Integer>> natural;
	private final Map<String, Set<String>> disabledStats;

	public SavedBuild(Map<String, OwnedMajor> majors, Map<String, Map<String, Integer>> natural, Map<String, Set<String>> disabledStats) {
		this.majors = new LinkedHashMap<>(majors);
		this.natural = new LinkedHashMap<>();
		natural.forEach((category, points) -> this.natural.put(category, new LinkedHashMap<>(points)));
		this.disabledStats = new LinkedHashMap<>();
		disabledStats.forEach((category, stats) -> this.disabledStats.put(category, new LinkedHashSet<>(stats)));
	}

	/** The build a profile is using right now. */
	public static SavedBuild capture(BloomProfile profile) {
		Map<String, Map<String, Integer>> natural = new LinkedHashMap<>();
		Map<String, Set<String>> disabled = new LinkedHashMap<>();

		profile.categories().forEach((category, progress) -> {
			if (!progress.natural().isEmpty()) {
				natural.put(category, progress.natural());
			}

			if (!progress.disabledStats().isEmpty()) {
				disabled.put(category, progress.disabledStats());
			}
		});

		return new SavedBuild(profile.majors(), natural, disabled);
	}

	/** Makes this the build the profile is using (its Majors, natural points and stat switches). */
	public void applyTo(BloomProfile profile) {
		profile.majors().clear();
		profile.majors().putAll(majors);

		for (String category : natural.keySet()) {
			profile.category(category);
		}

		for (String category : disabledStats.keySet()) {
			profile.category(category);
		}

		profile.categories().forEach((category, progress) -> {
			progress.natural().clear();
			progress.natural().putAll(natural.getOrDefault(category, Map.of()));
			progress.disabledStats().clear();
			progress.disabledStats().addAll(disabledStats.getOrDefault(category, Set.of()));
		});
	}

	/** Drops one category's part of this build: its Majors (and any listed with them) and its natural points. */
	public void clearCategory(String categoryId, Collection<String> majorIds) {
		majorIds.forEach(majors::remove);
		natural.remove(categoryId);
	}

	public Map<String, OwnedMajor> majors() {
		return majors;
	}

	public Map<String, Map<String, Integer>> natural() {
		return natural;
	}

	public Map<String, Set<String>> disabledStats() {
		return disabledStats;
	}

	public boolean owns(String majorId) {
		return majors.containsKey(majorId);
	}

	public SavedBuild copy() {
		return new SavedBuild(majors, natural, disabledStats);
	}
}
