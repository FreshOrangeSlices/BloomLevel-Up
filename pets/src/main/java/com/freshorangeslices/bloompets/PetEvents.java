package com.freshorangeslices.bloompets;

import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Keeps each player's pet list up to date (when a pet loads, every 5 seconds while it's loaded, when it unloads or
 * dies), gives Bloom pets their goals and the party mark they should have, and keeps a player's own hits (and their
 * pets') off their pets.
 */
public final class PetEvents {
	private static final int SEEN_EVERY = 100;

	private PetEvents() {
	}

	/** Brings the pet's entry up to date (a pet tamed the vanilla way is added here). Public for the game tests. */
	public static PetRegistry.Record observe(Entity entity) {
		UUID owner = Pets.owner(entity);

		if (owner == null || entity.level().getServer() == null) {
			return null;
		}

		return PetRegistry.get(entity.level().getServer()).upsert(owner, entity);
	}

	@SubscribeEvent
	public static void onJoin(EntityJoinLevelEvent event) {
		if (event.getLevel().isClientSide() || !Pets.listed(event.getEntity())) {
			return;
		}

		Entity entity = event.getEntity();
		PetRegistry.Record record = observe(entity);

		if (record == null || record.kind != Pets.Kind.BLOOM || !(entity instanceof Mob mob)) {
			return;
		}

		// The party may have changed while the pet was away.
		if (Pets.inParty(mob) != record.party) {
			Pets.setParty(mob, record.party);
		}

		if (record.heal) {
			mob.setHealth(mob.getMaxHealth());
			record.heal = false;
		}

		mob.setPersistenceRequired();
		PetGoals.add(mob);
	}

	@SubscribeEvent
	public static void onTick(LivingEvent.LivingTickEvent event) {
		LivingEntity entity = event.getEntity();

		if (entity.tickCount % SEEN_EVERY == 0 && !entity.level().isClientSide() && Pets.listed(entity)) {
			observe(entity);
		}
	}

	@SubscribeEvent
	public static void onLeave(EntityLeaveLevelEvent event) {
		Entity entity = event.getEntity();

		if (!event.getLevel().isClientSide() && !(entity instanceof LivingEntity living && living.isDeadOrDying()) && Pets.listed(entity)) {
			observe(entity);
		}
	}

	// Lowest, so a totem or a mod that cancels the death has had its say.
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onDeath(LivingDeathEvent event) {
		LivingEntity entity = event.getEntity();

		if (event.isCanceled() || !(entity.level() instanceof ServerLevel level) || !Pets.listed(entity)) {
			return;
		}

		MinecraftServer server = level.getServer();
		PetRegistry.Record record = PetRegistry.get(server).remove(entity.getUUID());

		if (record == null) {
			return;
		}

		PetStorage.dropAll(entity, record);
		ServerPlayer owner = server.getPlayerList().getPlayer(Pets.owner(entity));

		if (owner != null) {
			owner.sendSystemMessage(Component.translatable("bloompets.died", record.name));
		}
	}

	/** A player's own hits never land on their pets, nor their other pets' (a sweep, an arrow, a pet's bite). */
	@SubscribeEvent(priority = EventPriority.HIGH)
	public static void onAttack(LivingAttackEvent event) {
		LivingEntity target = event.getEntity();
		Entity attacker = event.getSource().getEntity();

		if (attacker == null || attacker == target || target.level().isClientSide() || !Pets.listed(target)) {
			return;
		}

		UUID owner = Pets.owner(target);

		if (owner.equals(attacker.getUUID()) || owner.equals(Pets.owner(attacker))) {
			event.setCanceled(true);
		}
	}
}
