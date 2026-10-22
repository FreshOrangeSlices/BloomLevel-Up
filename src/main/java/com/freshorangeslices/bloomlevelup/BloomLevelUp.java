package com.freshorangeslices.bloomlevelup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resources.Identifier;

import net.fabricmc.api.ModInitializer;

import com.freshorangeslices.bloomlevelup.registry.BloomRegistries;

/**
 * Common (server + client) entrypoint. Everything authoritative is wired from here; presentation lives in
 * the client source set.
 */
public final class BloomLevelUp implements ModInitializer {
	public static final String MOD_ID = "bloomlevelup";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		BloomRegistries.init();
		LOGGER.info("Bloom Level-Up {} initialised", version());
	}

	/** This jar's version, e.g. {@code 0.1.0+build.57} (CI run number) or {@code 0.1.0+local}. */
	public static String version() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(MOD_ID)
				.map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse("?");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	/** String form of {@link #id(String)}, used by the pure-Java core. */
	public static String key(String path) {
		return MOD_ID + ":" + path;
	}
}
