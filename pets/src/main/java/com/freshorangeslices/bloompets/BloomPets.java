package com.freshorangeslices.bloompets;

import com.freshorangeslices.bloompets.network.PetsNetwork;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Bloom Pets (docs/design/PETS.md): pets are the animals a player bonds with by feeding them their favourite food (the
 * roster in data/bloompets/bloompets/species) and the companions other mods give them (Migamigos, Zoni's Companions).
 * Every pet stays out in the world; one Bloom pet is in the player's party and follows them; any pet can be called to
 * its owner; Bloom pets carry a storage. Phase 1: bonding, the list of a player's pets, the party, calling and storage,
 * and the pet screen (a key, K, or the tab beside Bloom's hub button; {@code /bloompets} too). Phase 2: pet levels
 * (1 to 100) and XP, combat pets' skills, fainting instead of dying, the level-100 perks. Phase 3: mood and hunger,
 * owner bonuses, posts and jobs. Phase 4: One and the Same's weapon trees.
 */
@Mod(BloomPets.MOD_ID)
public final class BloomPets {
	public static final String MOD_ID = "bloompets";
	public static final Logger LOGGER = LogUtils.getLogger();

	public BloomPets() {
		IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
		ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, PetsConfig.SPEC);
		PetsNetwork.register();
		DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.freshorangeslices.bloompets.client.PetsClient.init(modBus));
		MinecraftForge.EVENT_BUS.register(Bonding.class);
		MinecraftForge.EVENT_BUS.register(PetEvents.class);
		MinecraftForge.EVENT_BUS.register(Calling.class);
		MinecraftForge.EVENT_BUS.register(PetXp.class);
		MinecraftForge.EVENT_BUS.register(PetSkills.class);
		MinecraftForge.EVENT_BUS.register(PetBonuses.class);
		MinecraftForge.EVENT_BUS.register(PetPosts.class);
		MinecraftForge.EVENT_BUS.register(PetTrees.class);
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
