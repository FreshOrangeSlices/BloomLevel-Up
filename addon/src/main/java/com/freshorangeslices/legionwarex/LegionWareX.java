package com.freshorangeslices.legionwarex;

import com.freshorangeslices.legionwarex.band.BandCommand;
import com.freshorangeslices.legionwarex.band.BandEvents;
import com.freshorangeslices.legionwarex.companion.CompanionEvents;
import com.freshorangeslices.legionwarex.companion.CompanionKeepsakeItem;
import com.freshorangeslices.legionwarex.world.WorldEvents;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.Commands;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

/**
 * The LegionWareX add-on: what FreshOrangeSlices' pack needs that no mod does. A befriended Migamigos companion that
 * dies leaves its owner a keepsake that brings that same companion back after a day's rest, and wild companions
 * despawn the way monsters do (Migamigos keeps every one for good). Illager war bands roam by day and pirate bands
 * land on the coasts (band/), and a new world starts on the mainland (world/).
 */
@Mod(LegionWareX.MOD_ID)
public final class LegionWareX {
	public static final String MOD_ID = "legionwarex";
	public static final Logger LOGGER = LogUtils.getLogger();

	public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);

	/** Holds a fallen companion (see {@link com.freshorangeslices.legionwarex.companion.Keepsakes}). */
	public static final RegistryObject<Item> COMPANION_KEEPSAKE = ITEMS.register("companion_keepsake",
			() -> new CompanionKeepsakeItem(new Item.Properties().stacksTo(1).fireResistant().rarity(Rarity.UNCOMMON)));

	public LegionWareX() {
		IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
		ITEMS.register(modBus);
		ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, AddonConfig.SPEC);
		MinecraftForge.EVENT_BUS.register(CompanionEvents.class);
		MinecraftForge.EVENT_BUS.register(BandEvents.class);
		MinecraftForge.EVENT_BUS.register(WorldEvents.class);
		MinecraftForge.EVENT_BUS.addListener(LegionWareX::registerCommands);
	}

	/** {@code /legionwarex band war|pirate [size]} (operators). */
	private static void registerCommands(RegisterCommandsEvent event) {
		event.getDispatcher().register(Commands.literal(MOD_ID).requires(source -> source.hasPermission(2)).then(BandCommand.node()));
	}
}
