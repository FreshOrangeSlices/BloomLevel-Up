package com.freshorangeslices.legionwarex.companion;

import com.freshorangeslices.legionwarex.AddonConfig;
import com.freshorangeslices.legionwarex.Despawning;
import com.freshorangeslices.legionwarex.LegionWareX;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * A befriended companion's death leaves its owner a keepsake; wild companions despawn like monsters (a raid's Enemigo
 * only once the raid is over); a companion told to stay with wandering off stays put ({@link StayPutGoal}); a player who
 * beats an Enemigo (Migamigos' hostile mode) earns the hidden advancement {@link #ENEMIGO}, which the quest book asks for.
 */
public final class CompanionEvents {
	/** Beat an Enemigo: no display of its own (the quest book shows it), granted by {@link #onEnemigoDeath}. */
	public static final ResourceLocation ENEMIGO = new ResourceLocation(LegionWareX.MOD_ID, "enemigo");

	private CompanionEvents() {
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onEnemigoDeath(LivingDeathEvent event) {
		if (event.isCanceled() || !(event.getSource().getEntity() instanceof ServerPlayer player) || !Companions.isEnemigo(event.getEntity())) {
			return;
		}

		Advancement advancement = player.server.getAdvancements().getAdvancement(ENEMIGO);

		if (advancement != null) {
			AdvancementProgress progress = player.getAdvancements().getOrStartProgress(advancement);

			for (String criterion : progress.getRemainingCriteria()) {
				player.getAdvancements().award(advancement, criterion);
			}
		}
	}

	// Lowest, so a totem or another mod that cancels the death has had its say.
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onDeath(LivingDeathEvent event) {
		LivingEntity entity = event.getEntity();

		if (event.isCanceled() || !(entity.level() instanceof ServerLevel level) || !Companions.isCompanion(entity)) {
			return;
		}

		UUID owner = Companions.owner(entity);

		if (owner != null) {
			ItemStack keepsake = Keepsakes.of(entity, owner, level.getGameTime() + AddonConfig.reviveWait());
			deliver(level.getServer(), owner, keepsake);
		}
	}

	/** To the owner now, or when they next join. */
	public static void deliver(MinecraftServer server, UUID owner, ItemStack keepsake) {
		ServerPlayer player = server.getPlayerList().getPlayer(owner);

		if (player == null) {
			PendingKeepsakes.get(server).add(owner, keepsake);
		} else {
			give(player, keepsake);
		}
	}

	private static void give(ServerPlayer player, ItemStack keepsake) {
		Component name = Keepsakes.name(keepsake);
		String wait = Keepsakes.time(Keepsakes.ticksLeft(player.level(), keepsake));

		if (!player.getInventory().add(keepsake)) {
			// A full inventory: at their feet, and it never despawns.
			ItemEntity dropped = player.drop(keepsake, false);

			if (dropped != null) {
				dropped.setUnlimitedLifetime();
				dropped.setNoPickUpDelay();
			}
		}

		player.sendSystemMessage(Component.translatable("message.legionwarex.keepsake_given", name, wait).withStyle(ChatFormatting.LIGHT_PURPLE));
	}

	@SubscribeEvent
	public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			PendingKeepsakes.get(player.server).take(player.getUUID()).forEach(stack -> give(player, stack));
		}
	}

	/** A wild companion despawns like a monster, except a wild Enemigo while a raid is on near it ({@link RaidEnemigos}). */
	@SubscribeEvent
	public static void onTick(LivingEvent.LivingTickEvent event) {
		if (event.getEntity() instanceof Mob mob && mob.tickCount % 20 == 0 && !mob.level().isClientSide
				&& AddonConfig.wildDespawn() && Companions.isWild(mob) && RaidEnemigos.raid(mob) == null) {
			Despawning.tick(mob);
		}
	}

	/** Goals aren't saved, so a companion gets the add-on's whenever it joins a level: staying put, and marching with a raid. */
	@SubscribeEvent
	public static void onJoin(EntityJoinLevelEvent event) {
		if (!event.getLevel().isClientSide() && event.getEntity() instanceof PathfinderMob mob && Companions.isCompanion(mob)) {
			mob.goalSelector.addGoal(StayPutGoal.PRIORITY, new StayPutGoal(mob));
			mob.goalSelector.addGoal(RaidEnemigos.MarchGoal.PRIORITY, new RaidEnemigos.MarchGoal(mob));
		}
	}

	/** Vanilla's rule for a monster (see {@link Despawning#despawns}). */
	public static boolean despawns(double distanceSqr, int noActionTicks, RandomSource random) {
		return Despawning.despawns(distanceSqr, noActionTicks, random);
	}
}
