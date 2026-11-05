package com.freshorangeslices.bloompets;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The roster, from data/&lt;namespace&gt;/bloompets/species/*.json: {"species": [{"entity", "category", "foods", "feeds",
 * "storage", "skills", "xp_multiplier"}]}. Files are read in id order; a later entry for the same animal replaces an earlier one. An entity or
 * food that isn't in the game is skipped with a warning.
 */
public final class SpeciesData extends SimpleJsonResourceReloadListener {
	private static volatile Map<EntityType<?>, Species> byType = Map.of();

	public SpeciesData() {
		super(new Gson(), "bloompets/species");
	}

	public static Species of(EntityType<?> type) {
		return byType.get(type);
	}

	public static Collection<Species> all() {
		return byType.values();
	}

	public static Species byName(String name) {
		for (Species species : byType.values()) {
			if (species.id().toString().equals(name) || species.id().getPath().equals(name)) {
				return species;
			}
		}

		return null;
	}

	private static int slots(String storage) {
		return switch (storage) {
			case "none" -> 0;
			case "small" -> 9;
			case "pack" -> 27;
			default -> 18;
		};
	}

	@Override
	protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
		Map<EntityType<?>, Species> out = new LinkedHashMap<>();

		for (Map.Entry<ResourceLocation, JsonElement> file : new TreeMap<>(files).entrySet()) {
			try {
				for (JsonElement element : GsonHelper.getAsJsonArray(file.getValue().getAsJsonObject(), "species")) {
					Species species = parse(element.getAsJsonObject(), file.getKey());

					if (species != null) {
						out.put(species.type(), species);
					}
				}
			} catch (RuntimeException e) {
				BloomPets.LOGGER.warn("Bloom Pets: couldn't read species file {}: {}", file.getKey(), e.getMessage());
			}
		}

		byType = Collections.unmodifiableMap(out);
		BloomPets.LOGGER.info("Bloom Pets: {} species", out.size());
	}

	public static Species parse(JsonObject json, ResourceLocation file) {
		ResourceLocation id = new ResourceLocation(GsonHelper.getAsString(json, "entity"));

		if (!ForgeRegistries.ENTITY_TYPES.containsKey(id)) {
			BloomPets.LOGGER.warn("Bloom Pets: {} names {}, which isn't in the game", file, id);
			return null;
		}

		Set<Item> foods = new LinkedHashSet<>();
		List<TagKey<Item>> tags = new ArrayList<>();

		for (JsonElement food : GsonHelper.getAsJsonArray(json, "foods")) {
			String name = food.getAsString();

			if (name.startsWith("#")) {
				tags.add(TagKey.create(Registries.ITEM, new ResourceLocation(name.substring(1))));
			} else if (ForgeRegistries.ITEMS.containsKey(new ResourceLocation(name))) {
				foods.add(ForgeRegistries.ITEMS.getValue(new ResourceLocation(name)));
			} else {
				BloomPets.LOGGER.warn("Bloom Pets: {} names the food {}, which isn't in the game", file, name);
			}
		}

		return new Species(id, ForgeRegistries.ENTITY_TYPES.getValue(id), GsonHelper.getAsString(json, "category", "utility"),
				Set.copyOf(foods), List.copyOf(tags), Math.max(1, GsonHelper.getAsInt(json, "feeds", 5)),
				slots(GsonHelper.getAsString(json, "storage", "medium")), skills(json, file),
				Math.max(0.0, GsonHelper.getAsDouble(json, "xp_multiplier", 0.0)));
	}

	private static String skills(JsonObject json, ResourceLocation file) {
		String skills = GsonHelper.getAsString(json, "skills", "");

		if (skills.isEmpty()) {
			return null;
		}

		if (PetSkills.Kind.byId(skills) == null) {
			BloomPets.LOGGER.warn("Bloom Pets: {} names the skill tree {}, which doesn't exist", file, skills);
			return null;
		}

		return skills;
	}
}
