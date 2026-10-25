package com.freshorangeslices.bloomlevelup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

import com.freshorangeslices.bloomlevelup.config.PackDefaults;

/**
 * Before the game starts (so before it reads options.txt and before Iris reads its config): switches on the Bloom
 * Pack's resource packs and shader, once per change of the pack's defaults file. Does nothing outside the pack or on
 * a server. Touches no game classes (it runs before they're safe to load).
 */
public final class PackDefaultsLaunch implements PreLaunchEntrypoint {
	private static final Logger LOGGER = LoggerFactory.getLogger("bloomlevelup");

	@Override
	public void onPreLaunch() {
		FabricLoader loader = FabricLoader.getInstance();

		if (loader.getEnvironmentType() != EnvType.CLIENT) {
			return;
		}

		try {
			PackDefaults.apply(loader.getConfigDir(), loader.getGameDir().resolve("options.txt"), dataVersion(), LOGGER::info);
		} catch (Exception e) {
			LOGGER.warn("Couldn't apply the Bloom Pack's resource pack, shader and key defaults", e);
		}
	}

	/** The game's data version, from the version.json in its jar (read as a file: no game classes yet), or null. */
	public static Integer dataVersion() {
		try {
			java.util.Enumeration<java.net.URL> found = PackDefaultsLaunch.class.getClassLoader().getResources("version.json");

			while (found.hasMoreElements()) {
				try (java.io.InputStream in = found.nextElement().openStream()) {
					Integer version = PackDefaults.dataVersion(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));

					if (version != null) {
						return version;
					}
				}
			}
		} catch (java.io.IOException e) {
			LOGGER.warn("Couldn't read the game's version.json", e);
		}

		return null;
	}
}
