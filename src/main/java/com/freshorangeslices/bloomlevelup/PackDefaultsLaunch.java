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
			PackDefaults.apply(loader.getConfigDir(), loader.getGameDir().resolve("options.txt"), LOGGER::info);
		} catch (Exception e) {
			LOGGER.warn("Couldn't apply the Bloom Pack's resource pack and shader defaults", e);
		}
	}
}
