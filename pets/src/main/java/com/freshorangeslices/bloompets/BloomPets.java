package com.freshorangeslices.bloompets;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * Bloom Pets (docs/design/PETS.md): pets are the animals a player bonds with by feeding them their favourite food (the
 * roster in data/bloompets/bloompets/species) and the companions other mods give them (Migamigos, Zoni's Companions).
 * Every pet stays out in the world; one Bloom pet is in the player's party and follows them; any pet can be called to
 * its owner; Bloom pets carry a storage. Phase 1: bonding, the list of a player's pets, the party, calling and storage,
 * through {@code /bloompets}; the screen, levels and skills come next.
 */
@Mod(BloomPets.MOD_ID)
public final class BloomPets {
	public static final String MOD_ID = "bloompets";
	public static final Logger LOGGER = LogUtils.getLogger();

	public BloomPets() {
		ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, PetsConfig.SPEC);
		MinecraftForge.EVENT_BUS.register(Bonding.class);
		MinecraftForge.EVENT_BUS.register(PetEvents.class);
		MinecraftForge.EVENT_BUS.register(Calling.class);
		MinecraftForge.EVENT_BUS.addListener(BloomPets::reload);
		MinecraftForge.EVENT_BUS.addListener(BloomPets::commands);
	}

	private static void reload(AddReloadListenerEvent event) {
		event.addListener(new SpeciesData());
	}

	private static void commands(RegisterCommandsEvent event) {
		PetsCommand.register(event.getDispatcher());
	}

	public static ResourceLocation id(String path) {
		return new ResourceLocation(MOD_ID, path);
	}
}
