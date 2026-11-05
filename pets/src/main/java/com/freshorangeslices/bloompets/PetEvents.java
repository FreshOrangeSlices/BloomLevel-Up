package com.freshorangeslices.bloompets;

import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Keeps each player's pet list up to date (when a pet loads, every 5 seconds while it's loaded, when it unloads or
 * dies), gives Bloom pets their goals and the party mark they should have, keeps a player's own hits (and their pets')
 * off their pets and pets' off players (co-op: no pet PvP), lets a party pet faint instead of dying, and runs each
 * pet's once-a-second work (XP for time together, a fainted pet's rest, Alpha Howl, a level-100 sparkle).
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

		// The party may have changed while the pet was away (and joining it takes a pet off its post).
		if (Pets.inParty(mob) != record.party) {
			Pets.setParty(mob, record.party);
		}

		if (record.party && PetPosts.posted(mob)) {
			PetPosts.leave(mob);
			record.post = null;
		}

		PetSkills.refresh(mob);

		if (record.heal) {
			mob.setHealth(mob.getMaxHealth());
			record.heal = false;
		}

		if (PetLevels.level(mob) >= PetLevels.MAX) {
			PetSkills.goldName(mob);
		}

		mob.setPersistenceRequired();
		PetGoals.add(mob);
	}

	@SubscribeEvent
	public static void onTick(LivingEvent.LivingTickEvent event) {
		LivingEntity entity = event.getEntity();

		if (entity.level().isClientSide()) {
			return;
		}

		if (Fainting.resting(entity)) {
			Fainting.rest(entity);
		}

		if (entity.tickCount % 20 != 0 || !Pets.listed(entity)) {
			return;
		}

		Fainting.check(entity);
		PetMood.second(entity);
		PetXp.second(entity);
		PetPosts.second(entity);
		PetTrees.second(entity);
		PetSkills.second(entity);

		if (entity.tickCount % SEEN_EVERY == 0) {
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

		// A party pet faints (or gets its second wind) instead.
		if (Fainting.save(entity, event.getSource())) {
			event.setCanceled(true);
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

	/**
	 * A player's own hits never land on their pets, nor their other pets' (a sweep, an arrow, a pet's bite); a pet's never
	 * land on a player (co-op: the plugin's pet PvP is dropped). A fainted pet can't be hurt (but {@code /kill} and the
	 * void still reach it), and can't hurt anything.
	 */
	@SubscribeEvent(priority = EventPriority.HIGH)
	public static void onAttack(LivingAttackEvent event) {
		LivingEntity target = event.getEntity();
		Entity attacker = event.getSource().getEntity();

		if (target.level().isClientSide()) {
			return;
		}

		boolean pet = Pets.listed(target);

		if (pet && Fainting.fainted(target) && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			event.setCanceled(true);
			return;
		}

		if (attacker == null || attacker == target) {
			return;
		}

		if (Pets.listed(attacker) && (target instanceof Player || Fainting.fainted(attacker))) {
			event.setCanceled(true);
			return;
		}

		if (!pet) {
			return;
		}

		UUID owner = Pets.owner(target);

		if (owner.equals(attacker.getUUID()) || owner.equals(Pets.owner(attacker))) {
			event.setCanceled(true);
		}
	}

	/** Pets never go after players (co-op) or their owner's other pets, and a fainted one goes after nothing. */
	@SubscribeEvent
	public static void onTarget(LivingChangeTargetEvent event) {
		LivingEntity pet = event.getEntity();
		LivingEntity target = event.getNewTarget();

		if (target == null || pet.level().isClientSide() || !Pets.listed(pet)) {
			return;
		}

		if (target instanceof Player || PetXp.sameSide(pet, target) || Fainting.fainted(pet)) {
			event.setCanceled(true);
		}
	}
}
