package com.freshorangeslices.bloomlevelup.effect;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

/**
 * Several effects are attributes that depend on what's in the main hand (Shovel Mastery, Brawler, Long
 * Reach, Guaranteed Critical's penalty, Full Sweep). Refresh them the tick the held item changes.
 */
public final class HeldItemWatcher {
	private static final Map<UUID, Item> LAST = new HashMap<>();

	private HeldItemWatcher() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(HeldItemWatcher::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((listener, server) -> LAST.remove(listener.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LAST.clear());
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Item held = player.getMainHandItem().getItem();
			Item previous = LAST.put(player.getUUID(), held);

			if (previous != null && previous != held) {
				StatEffects.refresh(player);
			}
		}
	}
}
