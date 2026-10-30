package com.freshorangeslices.legionwarex.chart;

import com.freshorangeslices.legionwarex.LegionWareX;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * The boss lairs a pirate chart can lead to: data files at {@code data/<namespace>/legionwarex/lairs/<name>.json}, each
 * {@code {"lairs": [{"structure": "<structure id>", "bosses": ["<entity id>", ...], "why": "..."}]}} (a file a later data
 * pack gives the same name replaces it). The add-on ships one per boss mod; a lair whose structure or bosses aren't in
 * the world is skipped when a chart is read, so a mod that isn't installed costs nothing.
 */
public final class Lairs extends SimpleJsonResourceReloadListener {
	private static final Gson GSON = new GsonBuilder().create();
	private static volatile List<Lair> lairs = List.of();
	private static volatile Set<ResourceLocation> bosses = Set.of();

	public Lairs() {
		super(GSON, LegionWareX.MOD_ID + "/lairs");
	}

	/** A structure and the bosses that live in it (a lair counts as beaten once all of them are). */
	public record Lair(ResourceLocation structure, List<ResourceLocation> bosses) {
	}

	public static List<Lair> all() {
		return lairs;
	}

	/** Every boss that has a lair: their deaths are what the add-on counts as beaten. */
	public static Set<ResourceLocation> bosses() {
		return bosses;
	}

	/** For the game tests: the lairs as if loaded from data. */
	public static void set(List<Lair> list) {
		lairs = List.copyOf(list);
		bosses = lairs.stream().flatMap(lair -> lair.bosses().stream()).collect(Collectors.toUnmodifiableSet());
	}

	@Override
	protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
		Map<ResourceLocation, Lair> out = new LinkedHashMap<>();

		for (Map.Entry<ResourceLocation, JsonElement> file : files.entrySet()) {
			try {
				JsonArray list = file.getValue().getAsJsonObject().getAsJsonArray("lairs");

				for (JsonElement element : list) {
					JsonObject entry = element.getAsJsonObject();
					ResourceLocation structure = new ResourceLocation(entry.get("structure").getAsString());
					List<ResourceLocation> bosses = new ArrayList<>();

					for (JsonElement boss : entry.getAsJsonArray("bosses")) {
						bosses.add(new ResourceLocation(boss.getAsString()));
					}

					if (!bosses.isEmpty()) {
						out.put(structure, new Lair(structure, List.copyOf(bosses)));
					}
				}
			} catch (RuntimeException e) {
				LegionWareX.LOGGER.warn("LegionWareX: lair file {} couldn't be read: {}", file.getKey(), e.toString());
			}
		}

		set(new ArrayList<>(out.values()));
		LegionWareX.LOGGER.info("LegionWareX: {} boss lairs for pirate charts", lairs.size());
	}
}
