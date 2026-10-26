package com.freshorangeslices.bloomlevelup.client;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.SharedConstants;
import net.minecraftforge.fml.loading.FMLPaths;

import com.freshorangeslices.bloomlevelup.config.PackDefaults;

/**
 * The Forge build's stand-in for main's {@code PackDefaultsLaunch}: applies the Bloom Pack's
 * {@code config/bloomlevelup/pack-defaults.properties} to options.txt (once per change of that file), called from
 * {@code OptionsMixin} before the game reads its options. Does nothing outside the pack. Runs before Bloom itself
 * is loaded, so it touches nothing of Bloom's but the plain-Java {@link PackDefaults}.
 */
public final class PackDefaultsHook {
	private static final Logger LOGGER = LoggerFactory.getLogger("bloomlevelup");

	private PackDefaultsHook() {
	}

	public static void apply(Path optionsFile) {
		try {
			PackDefaults.apply(FMLPaths.CONFIGDIR.get(), optionsFile, SharedConstants.getCurrentVersion().getDataVersion().getVersion(), LOGGER::info);
		} catch (Exception e) {
			LOGGER.warn("Couldn't apply the Bloom Pack's resource pack and key defaults", e);
		}
	}
}
