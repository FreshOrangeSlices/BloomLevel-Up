package com.freshorangeslices.legionwarex.world;

import com.freshorangeslices.legionwarex.AddonConfig;
import com.freshorangeslices.legionwarex.companion.Companions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * A mob killed by another mob (an iron golem, a rival faction, a skeleton's stray arrow, a creeper) drops nothing,
 * unless a player took part in that fight: a player, their pet or companion hit it at some point (FreshOrangeSlices,
 * 2026-10-30: no farms run by golems or rivalries). Deaths with no killer (a fall, lava, a cactus) drop as usual, and so
 * do mobs a player's pet or companion kills.
 */
public final class MobKillDrops {
	/** Set on a mob (its persistent data) the first time a player's side hurts it. */
	public static final String ENGAGED = "legionwarex:player_engaged";

	private MobKillDrops() {
	}

	/** A player, or a pet or companion one owns. */
	public static boolean playerSide(Entity entity) {
		if (entity == null) {
			return false;
		}

		if (entity instanceof Player) {
			return true;
		}

		if (entity instanceof OwnableEntity owned && owned.getOwnerUUID() != null) {
			return true;
		}

		return Companions.isCompanion(entity) && Companions.owner(entity) != null;
	}

	@SubscribeEvent
	public static void onHurt(LivingHurtEvent event) {
		LivingEntity hurt = event.getEntity();

		if (!(hurt instanceof Player) && !hurt.level().isClientSide() && playerSide(event.getSource().getEntity())) {
			hurt.getPersistentData().putBoolean(ENGAGED, true);
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGH)
	public static void onDrops(LivingDropsEvent event) {
		LivingEntity dead = event.getEntity();

		if (AddonConfig.dropsWhenKilledByMobs() || dead instanceof Player || event.isRecentlyHit() || dead.getPersistentData().getBoolean(ENGAGED)) {
			return;
		}

		Entity killer = event.getSource().getEntity();

		if (killer instanceof LivingEntity && killer != dead && !playerSide(killer)) {
			event.setCanceled(true);
		}
	}
}
