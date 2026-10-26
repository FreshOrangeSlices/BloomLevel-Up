package com.freshorangeslices.bloomlevelup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import com.freshorangeslices.bloomlevelup.registry.BloomRegistries;

/**
 * Forge entrypoint (server + client). Everything authoritative is wired from {@link BloomRegistries}; presentation
 * lives in the {@code client} package and is only touched on the physical client.
 */
@Mod(BloomLevelUp.MOD_ID)
public final class BloomLevelUp {
	public static final String MOD_ID = "bloomlevelup";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public BloomLevelUp() {
		IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
		BloomRegistries.init(modBus);
		LOGGER.info("Bloom Level-Up {} initialised", version());
	}

	/** This jar's version, e.g. {@code 0.1.0+build.57} (CI run number) or {@code 0.1.0+local}. */
	public static String version() {
		return ModList.get().getModContainerById(MOD_ID).map(container -> container.getModInfo().getVersion().toString()).orElse("?");
	}

	public static ResourceLocation id(String path) {
		return new ResourceLocation(MOD_ID, path);
	}

	/** String form of {@link #id(String)}, used by the pure-Java core. */
	public static String key(String path) {
		return MOD_ID + ":" + path;
	}
}
