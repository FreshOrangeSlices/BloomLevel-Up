package com.freshorangeslices.bloomlevelup.data;

import com.freshorangeslices.bloomlevelup.config.BloomConfig;
import com.freshorangeslices.bloomlevelup.definition.DefinitionSet;
import com.freshorangeslices.bloomlevelup.progression.ProgressionService;

/**
 * Holds the currently active definitions + config and the {@link ProgressionService} built from them.
 * Both are immutable snapshots swapped as a unit, so a reload can never leave gameplay half-updated.
 */
public final class BloomServices {
	private static volatile DefinitionSet definitions = DefinitionSet.EMPTY;
	private static volatile BloomConfig config = new BloomConfig();
	private static volatile ProgressionService progression = new ProgressionService(definitions, config);

	private BloomServices() {
	}

	public static DefinitionSet definitions() {
		return definitions;
	}

	public static BloomConfig config() {
		return config;
	}

	public static ProgressionService progression() {
		return progression;
	}

	public static synchronized void setDefinitions(DefinitionSet newDefinitions) {
		definitions = newDefinitions;
		progression = new ProgressionService(definitions, config);
	}

	public static synchronized void setConfig(BloomConfig newConfig) {
		config = newConfig;
		progression = new ProgressionService(definitions, config);
	}
}
