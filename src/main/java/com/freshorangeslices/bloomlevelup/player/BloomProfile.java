package com.freshorangeslices.bloomlevelup.player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The single, versioned source of truth for one player's build. Lives on the server as a persistent,
 * copy-on-death player attachment; the client only ever sees synced snapshots of it.
 *
 * <p>Major Levels are not stored as a balance. They are derived:
 * {@code earned = sum(milestones claimed) + sum(prestige claimed)}, {@code spent = sum(paid for owned Majors)},
 * so no code path can accidentally mint or lose currency.
 */
public final class BloomProfile {
	/** Bump when the saved shape changes and add a migration in {@code ProfileMigrations}. */
	public static final int CURRENT_DATA_VERSION = 1;

	private int dataVersion;
	private final Map<String, CategoryProgress> categories;
	private final Map<String, OwnedMajor> majors;
	private int bloomCount;
	private final Map<String, Long> counters;
	/** Which of the two builds is in use (0 or 1); the other one waits in {@link #spareBuild}. */
	private int activeBuild;
	/** The build not in use, once there has been a switch (until then the second build starts as a copy). */
	private SavedBuild spareBuild;

	public BloomProfile() {
		this(CURRENT_DATA_VERSION, Map.of(), Map.of(), 0, Map.of());
	}

	public BloomProfile(int dataVersion, Map<String, CategoryProgress> categories, Map<String, OwnedMajor> majors,
			int bloomCount, Map<String, Long> counters) {
		this(dataVersion, categories, majors, bloomCount, counters, 0, Optional.empty());
	}

	public BloomProfile(int dataVersion, Map<String, CategoryProgress> categories, Map<String, OwnedMajor> majors,
			int bloomCount, Map<String, Long> counters, int activeBuild, Optional<SavedBuild> spareBuild) {
		this.dataVersion = dataVersion;
		this.categories = new LinkedHashMap<>();
		categories.forEach((id, progress) -> this.categories.put(id, progress.copy()));
		this.majors = new LinkedHashMap<>(majors);
		this.bloomCount = Math.max(0, bloomCount);
		this.counters = new LinkedHashMap<>(counters);
		this.activeBuild = activeBuild == 1 ? 1 : 0;
		this.spareBuild = spareBuild.map(SavedBuild::copy).orElse(null);
	}

	public int activeBuild() {
		return activeBuild;
	}

	public void setActiveBuild(int activeBuild) {
		this.activeBuild = activeBuild == 1 ? 1 : 0;
	}

	public Optional<SavedBuild> spareBuild() {
		return Optional.ofNullable(spareBuild);
	}

	public void setSpareBuild(SavedBuild spareBuild) {
		this.spareBuild = spareBuild;
	}

	/** Owned in either build (the one in use or the spare one). */
	public boolean ownsInAnyBuild(String majorId) {
		return owns(majorId) || (spareBuild != null && spareBuild.owns(majorId));
	}

	public int dataVersion() {
		return dataVersion;
	}

	public void setDataVersion(int dataVersion) {
		this.dataVersion = dataVersion;
	}

	/** All category progress records, including ones whose category is not currently loaded. */
	public Map<String, CategoryProgress> categories() {
		return categories;
	}

	/** Progress for a category, created on first access. */
	public CategoryProgress category(String categoryId) {
		return categories.computeIfAbsent(categoryId, id -> new CategoryProgress());
	}

	public Map<String, OwnedMajor> majors() {
		return majors;
	}

	public boolean owns(String majorId) {
		return majors.containsKey(majorId);
	}

	public int bloomCount() {
		return bloomCount;
	}

	public void setBloomCount(int bloomCount) {
		this.bloomCount = bloomCount;
	}

	/**
	 * Free-form persistent numbers for Major-specific state (Protein! meals eaten, Last Breath tier,
	 * Castle of Stone charge, ...). Keys should be namespaced by the owning Major id.
	 */
	public Map<String, Long> counters() {
		return counters;
	}

	public long counter(String key) {
		return counters.getOrDefault(key, 0L);
	}

	public void setCounter(String key, long value) {
		if (value == 0L) {
			counters.remove(key);
		} else {
			counters.put(key, value);
		}
	}

	/** Counter: Major Levels given outright by an admin ({@code /bloom admin majorlevels}), on top of earned ones. */
	public static final String GIVEN_MAJOR_LEVELS = "bloomlevelup:admin#major_levels";

	public int earnedMajorLevels() {
		int total = (int) Math.max(0L, Math.min(Integer.MAX_VALUE / 2, counter(GIVEN_MAJOR_LEVELS)));

		for (CategoryProgress progress : categories.values()) {
			total += progress.majorMilestonesClaimed() + progress.prestigeClaimed();
		}

		return total;
	}

	public int prestigeMajorLevels() {
		int total = 0;

		for (CategoryProgress progress : categories.values()) {
			total += progress.prestigeClaimed();
		}

		return total;
	}

	/** Major Levels currently tied up in owned Majors. This is the N shown in the {@code [lvl N]} nameplate. */
	public int spentMajorLevels() {
		int total = 0;

		for (OwnedMajor owned : majors.values()) {
			total += owned.paid();
		}

		return total;
	}

	public int availableMajorLevels() {
		return earnedMajorLevels() - spentMajorLevels();
	}

	/** True if any category has ever been mastered; drives the permanent green nameplate tag. */
	public boolean anyMastered() {
		for (CategoryProgress progress : categories.values()) {
			if (progress.mastered()) {
				return true;
			}
		}

		return false;
	}

	public BloomProfile copy() {
		return new BloomProfile(dataVersion, categories, majors, bloomCount, counters, activeBuild, spareBuild());
	}
}
