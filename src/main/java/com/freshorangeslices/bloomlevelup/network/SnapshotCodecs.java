package com.freshorangeslices.bloomlevelup.network;

import java.util.List;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import com.freshorangeslices.bloomlevelup.config.BloomConfig;
import com.freshorangeslices.bloomlevelup.definition.CategoryDefinition;
import com.freshorangeslices.bloomlevelup.definition.DefinitionSet;
import com.freshorangeslices.bloomlevelup.definition.MajorDefinition;
import com.freshorangeslices.bloomlevelup.definition.StatDefinition;
import com.freshorangeslices.bloomlevelup.player.BloomProfile;
import com.freshorangeslices.bloomlevelup.player.ProfileCodecs;

/**
 * JSON form of the snapshots the client receives. Definitions travel already validated, so the client
 * rebuilds an identical {@link DefinitionSet} and can show prerequisite / cost states without guessing.
 */
public final class SnapshotCodecs {
	private static final Gson GSON = new Gson();

	private SnapshotCodecs() {
	}

	private static final Codec<StatDefinition> STAT = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("id").forGetter(StatDefinition::id),
			Codec.STRING.fieldOf("category").forGetter(StatDefinition::categoryId),
			Codec.BOOL.fieldOf("toggleable").forGetter(StatDefinition::toggleable),
			Codec.INT.fieldOf("sort").forGetter(StatDefinition::sort),
			Codec.STRING.optionalFieldOf("icon").forGetter(StatDefinition::icon)
	).apply(i, StatDefinition::new));

	private static final Codec<CategoryDefinition> CATEGORY = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("id").forGetter(CategoryDefinition::id),
			Codec.INT.fieldOf("sort").forGetter(CategoryDefinition::sort),
			Codec.BOOL.fieldOf("xp_investment").forGetter(CategoryDefinition::xpInvestment),
			Codec.STRING.optionalFieldOf("icon").forGetter(CategoryDefinition::icon),
			STAT.listOf().fieldOf("stats").forGetter(CategoryDefinition::stats)
	).apply(i, CategoryDefinition::new));

	private static final Codec<Set<String>> STRING_SET = Codec.STRING.listOf()
			.xmap(list -> (Set<String>) Set.copyOf(list), set -> (List<String>) List.copyOf(set));

	private static final Codec<MajorDefinition> MAJOR = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("id").forGetter(MajorDefinition::id),
			Codec.STRING.fieldOf("category").forGetter(MajorDefinition::categoryId),
			Codec.INT.fieldOf("cost").forGetter(MajorDefinition::cost),
			Codec.BOOL.fieldOf("toggleable").forGetter(MajorDefinition::toggleable),
			Codec.STRING.listOf().fieldOf("prerequisites").forGetter(MajorDefinition::prerequisites),
			Codec.STRING.listOf().fieldOf("conflicts").forGetter(MajorDefinition::conflicts),
			Codec.STRING.optionalFieldOf("group").forGetter(MajorDefinition::group),
			Codec.INT.fieldOf("sort").forGetter(MajorDefinition::sort),
			Codec.STRING.optionalFieldOf("icon").forGetter(MajorDefinition::icon),
			Codec.STRING.optionalFieldOf("effect").forGetter(MajorDefinition::effect),
			STRING_SET.fieldOf("flags").forGetter(MajorDefinition::flags)
	).apply(i, MajorDefinition::new));

	/** Definitions + config, as sent to clients. */
	public record DefinitionsSnapshot(DefinitionSet definitions, BloomConfig config) {
	}

	public static String encodeDefinitions(DefinitionSet definitions, BloomConfig config) {
		JsonObject root = new JsonObject();
		root.add("config", GSON.toJsonTree(config));
		root.add("categories", CATEGORY.listOf().encodeStart(JsonOps.INSTANCE, List.copyOf(definitions.categories())).getOrThrow());
		root.add("majors", MAJOR.listOf().encodeStart(JsonOps.INSTANCE, List.copyOf(definitions.majors())).getOrThrow());
		return GSON.toJson(root);
	}

	public static DefinitionsSnapshot decodeDefinitions(String json) {
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		BloomConfig config = GSON.fromJson(root.get("config"), BloomConfig.class).validated();
		List<CategoryDefinition> categories = CATEGORY.listOf().parse(JsonOps.INSTANCE, root.get("categories")).getOrThrow();
		List<MajorDefinition> majors = MAJOR.listOf().parse(JsonOps.INSTANCE, root.get("majors")).getOrThrow();
		return new DefinitionsSnapshot(DefinitionSet.build(categories, majors), config);
	}

	public static String encodeProfile(BloomProfile profile) {
		JsonElement json = ProfileCodecs.PROFILE.encodeStart(JsonOps.INSTANCE, profile).getOrThrow();
		return GSON.toJson(json);
	}

	public static BloomProfile decodeProfile(String json) {
		return ProfileCodecs.PROFILE.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
	}
}
