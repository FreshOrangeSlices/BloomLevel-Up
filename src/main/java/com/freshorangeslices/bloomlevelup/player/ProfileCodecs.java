package com.freshorangeslices.bloomlevelup.player;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Save format for {@link BloomProfile}. Every field is optional with a default, so older saves load cleanly;
 * {@code data_version} plus {@link #migrate} handles shape changes that defaults cannot.
 */
public final class ProfileCodecs {
	private ProfileCodecs() {
	}

	private static final Codec<Set<String>> STRING_SET = Codec.STRING.listOf()
			.xmap(list -> (Set<String>) new LinkedHashSet<>(list), set -> (List<String>) new ArrayList<>(set));

	private static final Codec<Map<String, Integer>> INT_MAP = Codec.unboundedMap(Codec.STRING, Codec.INT);

	public static final Codec<CategoryProgress> CATEGORY = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("level", 0).forGetter(CategoryProgress::level),
			Codec.DOUBLE.optionalFieldOf("xp", 0.0).forGetter(CategoryProgress::xp),
			INT_MAP.optionalFieldOf("natural", Map.of()).forGetter(CategoryProgress::natural),
			Codec.INT.optionalFieldOf("invested_points", 0).forGetter(CategoryProgress::investedPoints),
			INT_MAP.optionalFieldOf("invested", Map.of()).forGetter(CategoryProgress::invested),
			STRING_SET.optionalFieldOf("disabled_stats", Set.of()).forGetter(CategoryProgress::disabledStats),
			Codec.BOOL.optionalFieldOf("mastered", false).forGetter(CategoryProgress::mastered),
			Codec.INT.optionalFieldOf("major_milestones_claimed", 0).forGetter(CategoryProgress::majorMilestonesClaimed),
			Codec.INT.optionalFieldOf("prestige_claimed", 0).forGetter(CategoryProgress::prestigeClaimed)
	).apply(i, CategoryProgress::new));

	public static final Codec<OwnedMajor> OWNED_MAJOR = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("enabled", true).forGetter(OwnedMajor::enabled),
			Codec.INT.optionalFieldOf("paid", 0).forGetter(OwnedMajor::paid),
			Codec.STRING.optionalFieldOf("option", "").forGetter(OwnedMajor::option)
	).apply(i, OwnedMajor::new));

	public static final Codec<BloomProfile> PROFILE = RecordCodecBuilder.<BloomProfile>create(i -> i.group(
			Codec.INT.optionalFieldOf("data_version", BloomProfile.CURRENT_DATA_VERSION).forGetter(BloomProfile::dataVersion),
			Codec.unboundedMap(Codec.STRING, CATEGORY).optionalFieldOf("categories", Map.of()).forGetter(BloomProfile::categories),
			Codec.unboundedMap(Codec.STRING, OWNED_MAJOR).optionalFieldOf("majors", Map.of()).forGetter(BloomProfile::majors),
			Codec.INT.optionalFieldOf("bloom_count", 0).forGetter(BloomProfile::bloomCount),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("counters", Map.of()).forGetter(BloomProfile::counters)
	).apply(i, BloomProfile::new)).xmap(ProfileCodecs::migrate, profile -> profile);

	/** Upgrades a freshly decoded profile to {@link BloomProfile#CURRENT_DATA_VERSION}. */
	static BloomProfile migrate(BloomProfile profile) {
		// No schema changes yet. Future: if (profile.dataVersion() < 2) { ...rewrite fields... }
		profile.setDataVersion(BloomProfile.CURRENT_DATA_VERSION);
		return profile;
	}
}
