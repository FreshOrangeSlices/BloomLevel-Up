package com.freshorangeslices.bloomlevelup.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import net.fabricmc.loader.api.FabricLoader;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;

/**
 * Reads {@code config/bloomlevelup.json}, filling in defaults for anything missing, and writes the merged
 * result back so new options appear in existing files after an update.
 */
public final class ConfigLoader {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private ConfigLoader() {
	}

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(BloomLevelUp.MOD_ID + ".json");
	}

	public static BloomConfig load() {
		Path path = path();
		BloomConfig config = new BloomConfig();
		config.configVersion = BloomConfig.CURRENT_VERSION;

		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				BloomConfig read = GSON.fromJson(reader, BloomConfig.class);

				if (read != null) {
					config = read.migrate();
				}
			} catch (IOException | JsonParseException e) {
				BloomLevelUp.LOGGER.error("Couldn't read {}, using defaults", path, e);
				return new BloomConfig().validated();
			}
		}

		config.validated();
		save(config);
		return config;
	}

	public static void save(BloomConfig config) {
		Path path = path();

		try {
			Files.createDirectories(path.getParent());

			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(config, writer);
			}
		} catch (IOException e) {
			BloomLevelUp.LOGGER.error("Couldn't write {}", path, e);
		}
	}
}
