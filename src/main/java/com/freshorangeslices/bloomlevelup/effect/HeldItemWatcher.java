package com.freshorangeslices.bloomlevelup.effect;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

/**
 * Several effects are attributes that depend on what's in the main hand (Shovel Mastery, Brawler, Long
 * Reach, Guaranteed Critical's penalty, Full Sweep) or on wearing no armor (Rune Embedding, Titan Hide).
 * Refresh them the tick either changes.
 */
public final class HeldItemWatcher {
	private static final Map<UUID, Item> LAST = new HashMap<>();
	/** Rune Embedding's body runes and Titan Hide depend on whether all armor slots are empty. */
	private static final Map<UUID, Boolean> ARMORLESS = new HashMap<>();

	private HeldItemWatcher() {
	}

	public static void init() {
		MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) -> {
			if (event.phase == TickEvent.Phase.END) {
				tick(event.getServer());
			}
		});
		MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
			LAST.remove(event.getEntity().getUUID());
			ARMORLESS.remove(event.getEntity().getUUID());
		});
		MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
			LAST.clear();
			ARMORLESS.clear();
		});
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Item held = player.getMainHandItem().getItem();
			Item previous = LAST.put(player.getUUID(), held);
			boolean armorless = player.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()
					&& player.getItemBySlot(EquipmentSlot.LEGS).isEmpty() && player.getItemBySlot(EquipmentSlot.FEET).isEmpty();
			Boolean wasArmorless = ARMORLESS.put(player.getUUID(), armorless);

			if ((previous != null && previous != held) || (wasArmorless != null && wasArmorless != armorless)) {
				StatEffects.refresh(player);
			}
		}
	}
}
