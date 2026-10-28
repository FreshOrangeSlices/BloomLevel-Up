package com.freshorangeslices.bloomlevelup.client.gui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraftforge.fml.loading.FMLPaths;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;

/**
 * What the stat sheet was last set to, per tab (weapon or tool slot, target, each switch, summary or all stats),
 * kept in {@code config/bloomlevelup-sheet.json} so it survives quitting. Only this player's own choices; nothing
 * here changes what the server works out.
 */
final class SheetMemory {
	/** One tab's choices; {@code modes} holds only the switches not on Live (1 = Max, 2 = Off). */
	record Tab(int slot, int target, Map<String, Integer> modes, boolean details) {
	}

	private static final Tab DEFAULT = new Tab(StatSheetWindow.HELD, -1, Map.of(), false);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Map<String, Tab> TABS = new HashMap<>();
	/** The Simplify switch, for every tab (saved as "_simplify", next to the tabs). */
	private static final String SIMPLIFY = "_simplify";
	private static boolean simplify;
	private static boolean loaded;

	private SheetMemory() {
	}

	static Tab get(String category) {
		load();
		return TABS.getOrDefault(category, DEFAULT);
	}

	static void put(String category, Tab tab) {
		load();
		TABS.put(category, tab);
		save();
	}

	static boolean simplify() {
		load();
		return simplify;
	}

	static void setSimplify(boolean on) {
		load();
		simplify = on;
		save();
	}

	private static Path file() {
		return FMLPaths.CONFIGDIR.get().resolve("bloomlevelup-sheet.json");
	}

	private static void load() {
		if (loaded) {
			return;
		}

		loaded = true;
		Path file = file();

		if (!Files.isRegularFile(file)) {
			return;
		}

		try {
			JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();

			simplify = root.has(SIMPLIFY) && root.get(SIMPLIFY).getAsBoolean();

			for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
				if (entry.getKey().startsWith("_") || !entry.getValue().isJsonObject()) {
					continue;
				}

				JsonObject tab = entry.getValue().getAsJsonObject();
				Map<String, Integer> modes = new LinkedHashMap<>();

				if (tab.has("modes")) {
					for (Map.Entry<String, JsonElement> mode : tab.getAsJsonObject("modes").entrySet()) {
						modes.put(mode.getKey(), mode.getValue().getAsInt());
					}
				}

				TABS.put(entry.getKey(), new Tab(tab.has("slot") ? tab.get("slot").getAsInt() : StatSheetWindow.HELD,
						tab.has("target") ? tab.get("target").getAsInt() : -1, modes, tab.has("details") && tab.get("details").getAsBoolean()));
			}
		} catch (IOException | RuntimeException e) {
			BloomLevelUp.LOGGER.warn("Couldn't read {}; the stat sheet starts fresh", file, e);
		}
	}

	private static void save() {
		JsonObject root = new JsonObject();
		root.addProperty(SIMPLIFY, simplify);

		for (Map.Entry<String, Tab> entry : TABS.entrySet()) {
			JsonObject tab = new JsonObject();
			Tab value = entry.getValue();
			tab.addProperty("slot", value.slot());
			tab.addProperty("target", value.target());
			tab.addProperty("details", value.details());
			JsonObject modes = new JsonObject();
			value.modes().forEach((id, mode) -> modes.addProperty(id, (Number) mode));
			tab.add("modes", modes);
			root.add(entry.getKey(), tab);
		}

		try {
			Files.writeString(file(), GSON.toJson(root), StandardCharsets.UTF_8);
		} catch (IOException e) {
			BloomLevelUp.LOGGER.warn("Couldn't save the stat sheet's choices to {}", file(), e);
		}
	}
}
