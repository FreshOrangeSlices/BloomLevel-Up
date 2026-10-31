package com.freshorangeslices.legionwarex.world;

import com.freshorangeslices.legionwarex.AddonConfig;
import com.freshorangeslices.legionwarex.LegionWareX;
import java.lang.reflect.Field;
import java.util.Map;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;

/**
 * Born in Chaos's naughtiness, its Krampus meter: a player who kills villagers, wandering traders or animals builds it up,
 * gets warnings, and then Krampus comes for them. The pack counts villagers and traders only (FreshOrangeSlices,
 * 2026-10-31): Born in Chaos's handler for animal kills ({@value #ANIMALS}, a static listener on the Forge bus) is taken
 * off the bus when the server starts with the config's {@code krampusAnimalNaughtiness} off, and put back when it's on.
 * Logged as "LegionWareX naughtiness: ...", which the pack test reads.
 */
public final class KrampusNaughtiness {
	static final String MOD = "born_in_chaos_v1";
	static final String ANIMALS = "net.mcreator.borninchaosv.procedures.KillinganimalsProcedure";
	private static boolean removed;

	private KrampusNaughtiness() {
	}

	@SubscribeEvent
	public static void onServerStarted(ServerStartedEvent event) {
		if (!ModList.get().isLoaded(MOD)) {
			return;
		}

		Class<?> handler;

		try {
			handler = Class.forName(ANIMALS, false, KrampusNaughtiness.class.getClassLoader());
		} catch (ClassNotFoundException | LinkageError e) {
			LegionWareX.LOGGER.warn("LegionWareX naughtiness: couldn't find Born in Chaos's animal handler {}: {}", ANIMALS, e.toString());
			return;
		}

		boolean count = AddonConfig.krampusAnimalNaughtiness();

		try {
			if (!count && !removed) {
				MinecraftForge.EVENT_BUS.unregister(handler);
				removed = true;
			} else if (count && removed) {
				MinecraftForge.EVENT_BUS.register(handler);
				removed = false;
			}
		} catch (RuntimeException e) {
			LegionWareX.LOGGER.warn("LegionWareX naughtiness: couldn't change Born in Chaos's animal handler: {}", e.toString());
			return;
		}

		Boolean listening = listening(handler);
		String check = listening == null ? " (the bus couldn't be read to check)"
				: listening == count ? "" : "; but its animal handler is " + (listening ? "still" : "not") + " on the bus";
		LegionWareX.LOGGER.info("LegionWareX naughtiness: killing animals {} toward Krampus, villagers and traders do{}", count ? "counts" : "doesn't count", check);
	}

	/** Whether a class's static listeners are on the Forge bus (null when the bus can't be read). */
	static Boolean listening(Class<?> handler) {
		try {
			Field field = MinecraftForge.EVENT_BUS.getClass().getDeclaredField("listeners");
			field.setAccessible(true);
			return ((Map<?, ?>) field.get(MinecraftForge.EVENT_BUS)).containsKey(handler);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}
}
