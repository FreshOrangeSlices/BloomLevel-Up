package com.freshorangeslices.bloomlevelup.client;

import java.util.List;

import com.freshorangeslices.bloomlevelup.config.BloomConfig;
import com.freshorangeslices.bloomlevelup.definition.DefinitionSet;
import com.freshorangeslices.bloomlevelup.network.BloomPayloads;
import com.freshorangeslices.bloomlevelup.player.BloomProfile;
import com.freshorangeslices.bloomlevelup.progression.ProgressionService;

/**
 * What the client knows, exactly as the server last sent it. Read-only from the UI's point of view:
 * the hub draws from this and sends requests; only server snapshots change it.
 */
public final class ClientBloomState {
	private static DefinitionSet definitions;
	private static BloomConfig config = new BloomConfig();
	private static BloomProfile profile = new BloomProfile();
	private static ProgressionService service;
	private static BloomPayloads.Result lastResult;
	private static long lastResultAt;
	private static String selectedCategory;
	private static int version;

	private ClientBloomState() {
	}

	/** True once the server has told us it runs the mod. */
	public static boolean ready() {
		return definitions != null;
	}

	public static DefinitionSet definitions() {
		return definitions == null ? DefinitionSet.EMPTY : definitions;
	}

	public static BloomConfig config() {
		return config;
	}

	public static BloomProfile profile() {
		return profile;
	}

	/** The same rule engine the server uses, for display only (costs, prerequisites, XP needed). */
	public static ProgressionService service() {
		if (service == null) {
			service = new ProgressionService(definitions(), config);
		}

		return service;
	}

	/** Bumps on every snapshot so open screens know to rebuild. */
	public static int version() {
		return version;
	}

	static void setDefinitions(DefinitionSet newDefinitions, BloomConfig newConfig) {
		definitions = newDefinitions;
		config = newConfig;
		service = null;
		IconTextures.clearCache();
		version++;
	}

	static void setProfile(BloomProfile newProfile) {
		profile = newProfile;
		version++;
	}

	static void setLastResult(BloomPayloads.Result result) {
		lastResult = result;
		lastResultAt = System.currentTimeMillis();
		version++;
	}

	public static BloomPayloads.Result lastResult() {
		return lastResult;
	}

	public static long lastResultAt() {
		return lastResultAt;
	}

	public static String selectedCategory() {
		List<String> ids = definitions().categories().stream().map(c -> c.id()).toList();

		if (selectedCategory == null || !ids.contains(selectedCategory)) {
			selectedCategory = ids.isEmpty() ? null : ids.get(0);
		}

		return selectedCategory;
	}

	public static void selectCategory(String categoryId) {
		selectedCategory = categoryId;
	}

	static void clear() {
		definitions = null;
		config = new BloomConfig();
		profile = new BloomProfile();
		service = null;
		lastResult = null;
		version++;
	}
}
