package com.freshorangeslices.legionwarex;

import com.freshorangeslices.legionwarex.band.BandCommand;
import com.freshorangeslices.legionwarex.band.BandEvents;
import com.freshorangeslices.legionwarex.chart.AddItemModifier;
import com.freshorangeslices.legionwarex.chart.ChartCommand;
import com.freshorangeslices.legionwarex.chart.ChartEvents;
import com.freshorangeslices.legionwarex.chart.PirateChartItem;
import com.freshorangeslices.legionwarex.companion.CompanionEvents;
import com.freshorangeslices.legionwarex.harvest.HarvestCommand;
import com.freshorangeslices.legionwarex.harvest.HarvestCourt;
import com.freshorangeslices.legionwarex.sentinel.SentinelCommand;
import com.freshorangeslices.legionwarex.sentinel.SentinelEvents;
import com.freshorangeslices.legionwarex.companion.CompanionKeepsakeItem;
import com.freshorangeslices.legionwarex.world.MobKillDrops;
import com.freshorangeslices.legionwarex.world.WorldEvents;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import net.minecraft.commands.Commands;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.loot.IGlobalLootModifier;
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
 * The Warionex add-on: what FreshOrangeSlices' pack needs that no mod does. A befriended Migamigos companion that
 * dies leaves its owner a keepsake that brings that same companion back after a day's rest, and wild companions
 * despawn the way monsters do (Migamigos keeps every one for good). Illager war bands roam by day and pirate bands
 * land on the coasts (band/); pirate charts lead to boss lairs (chart/); lone sentinels stand watch by day (sentinel/);
 * Born in Chaos's pumpkin folk spawn all year (harvest/); a new world starts on the mainland, and mobs other mobs kill
 * drop nothing unless a player took part (world/). The pack was
 * LegionWareX at first and is Warionex now; the mod id, commands and saved data keep the first name.
 */
@Mod(LegionWareX.MOD_ID)
public final class LegionWareX {
	public static final String MOD_ID = "legionwarex";
	public static final Logger LOGGER = LogUtils.getLogger();

	public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
	public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
			DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, MOD_ID);

	/** Holds a fallen companion (see {@link com.freshorangeslices.legionwarex.companion.Keepsakes}). */
	public static final RegistryObject<Item> COMPANION_KEEPSAKE = ITEMS.register("companion_keepsake",
			() -> new CompanionKeepsakeItem(new Item.Properties().stacksTo(1).fireResistant().rarity(Rarity.UNCOMMON)));

	/** Leads to a boss lair once read (see {@link com.freshorangeslices.legionwarex.chart.Charts}). */
	public static final RegistryObject<Item> PIRATE_CHART = ITEMS.register("pirate_chart",
			() -> new PirateChartItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));

	static {
		LOOT_MODIFIERS.register("add_item", AddItemModifier.CODEC);
	}

	public LegionWareX() {
		IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
		ITEMS.register(modBus);
		LOOT_MODIFIERS.register(modBus);
		ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, AddonConfig.SPEC);
		MinecraftForge.EVENT_BUS.register(CompanionEvents.class);
		MinecraftForge.EVENT_BUS.register(BandEvents.class);
		MinecraftForge.EVENT_BUS.register(WorldEvents.class);
		MinecraftForge.EVENT_BUS.register(ChartEvents.class);
		MinecraftForge.EVENT_BUS.register(SentinelEvents.class);
		MinecraftForge.EVENT_BUS.register(HarvestCourt.class);
		MinecraftForge.EVENT_BUS.register(MobKillDrops.class);
		MinecraftForge.EVENT_BUS.addListener(LegionWareX::registerCommands);
	}

	/** {@code /legionwarex band|chart|sentinel|harvest ...} (operators). */
	private static void registerCommands(RegisterCommandsEvent event) {
		event.getDispatcher().register(Commands.literal(MOD_ID).requires(source -> source.hasPermission(2))
				.then(BandCommand.node()).then(ChartCommand.node()).then(SentinelCommand.node()).then(HarvestCommand.node()));
	}
}
