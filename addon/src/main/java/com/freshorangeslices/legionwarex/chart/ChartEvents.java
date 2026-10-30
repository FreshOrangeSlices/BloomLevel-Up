package com.freshorangeslices.legionwarex.chart;

import com.freshorangeslices.legionwarex.AddonConfig;
import com.freshorangeslices.legionwarex.LegionWareX;
import com.freshorangeslices.legionwarex.band.BandKind;
import com.freshorangeslices.legionwarex.band.Bands;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

/** Pirate charts in the world: the lair list, readings, arrivals, beaten bosses and the band pirates' drop. */
public final class ChartEvents {
	private ChartEvents() {
	}

	@SubscribeEvent
	public static void onReload(AddReloadListenerEvent event) {
		event.addListener(new Lairs());
	}

	@SubscribeEvent
	public static void onServerTick(TickEvent.ServerTickEvent event) {
		if (event.phase == TickEvent.Phase.END) {
			ChartReading.tick(event.getServer());
		}
	}

	@SubscribeEvent
	public static void onServerStopped(ServerStoppedEvent event) {
		ChartReading.clear();
	}

	@SubscribeEvent
	public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			ChartReading.loggedOut(player);
		}
	}

	@SubscribeEvent
	public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
		if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player && player.tickCount % 20 == 11 && player.isAlive()) {
			Charts.checkArrival(player);
		}
	}

	/** A lair boss killed by a player (or a player's pet) is beaten for everyone on the world. */
	@SubscribeEvent
	public static void onDeath(LivingDeathEvent event) {
		LivingEntity dead = event.getEntity();

		if (dead.level().isClientSide || dead.getServer() == null) {
			return;
		}

		ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(dead.getType());

		if (id == null || !Lairs.bosses().contains(id) || !byPlayer(event.getSource().getEntity(), dead)) {
			return;
		}

		if (BeatenBosses.of(dead.getServer()).add(id)) {
			LegionWareX.LOGGER.info("LegionWareX chart: {} beaten; charts skip its lair from now on", id);
		}
	}

	private static boolean byPlayer(Entity killer, LivingEntity dead) {
		return killer instanceof Player || killer instanceof OwnableEntity pet && pet.getOwner() instanceof Player
				|| dead.getLastHurtByMob() instanceof Player || dead.getKillCredit() instanceof Player;
	}

	/** A band pirate killed by a player drops a chart about 1 in 15 times (the Kraken ship's own crew never does). */
	@SubscribeEvent
	public static void onDrops(LivingDropsEvent event) {
		LivingEntity dead = event.getEntity();

		if (dead.level().isClientSide || Bands.kindOf(dead) != BandKind.PIRATE || !event.isRecentlyHit()
				|| dead.getRandom().nextDouble() >= AddonConfig.chartDropChance()) {
			return;
		}

		event.getDrops().add(new ItemEntity(dead.level(), dead.getX(), dead.getY(), dead.getZ(), new ItemStack(LegionWareX.PIRATE_CHART.get())));
	}
}
