package com.freshorangeslices.bloompets;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * How pets earn XP (FreshOrangeSlices, 2026-11-05). Combat pets the way Bloom's Combat tab does: for the real damage
 * they deal (after armor, never more than the target had left) times their kind's multiplier, and the killing blow adds
 * half of what that kill earned them. Non-combat party pets from time near their owner, walking with them, and being
 * fed. Tuned so a combat pet reaches 100 in about 16 hours of fighting and a non-combat one in about 8 hours out.
 */
public final class PetXp {
	/** XP each pet has earned on each target so far, for the kill bonus. */
	private static final Map<LivingEntity, Map<UUID, Double>> EARNED = new WeakHashMap<>();
	/** Per pet: its owner's last spot (x, z) and the blocks walked toward the next XP. */
	private static final Map<UUID, double[]> WALKS = new HashMap<>();
	private static final double NEAR = 32.0;
	/** A step longer than this in a second wasn't walking (a teleport, a fall, a minecart). */
	private static final double MAX_STEP = 12.0;

	private PetXp() {
	}

	/**
	 * The pet's XP multiplier: its kind's, or worked out from how hard it hits (a modded companion's weapon too), never
	 * below a wolf's: a companion that hits softly earns more per point, one that hits hard never less than a wolf
	 * (FreshOrangeSlices, 2026-11-05: levels mustn't hold back the companions that do less damage than a golem).
	 */
	public static double multiplier(LivingEntity pet) {
		Species species = Pets.species(pet);

		if (species != null && species.xpMultiplier() > 0) {
			return species.xpMultiplier();
		}

		AttributeInstance attack = pet.getAttribute(Attributes.ATTACK_DAMAGE);
		double damage = attack == null ? 4.0 : attack.getValue();
		return Mth.clamp(4.0 / Math.max(0.5, damage), 1.0, 3.0);
	}

	/** The pet and the target are on the same side (its owner, the owner's other pets): no XP, and no fighting. */
	public static boolean sameSide(Entity pet, Entity target) {
		UUID owner = Pets.owner(pet);
		return owner != null && (owner.equals(target.getUUID()) || owner.equals(Pets.owner(target)));
	}

	@SubscribeEvent(priority = EventPriority.LOW)
	public static void onDamage(LivingDamageEvent event) {
		LivingEntity target = event.getEntity();

		if (target.level().isClientSide() || !(event.getSource().getEntity() instanceof LivingEntity pet) || pet == target
				|| target instanceof Player || !Pets.listed(pet) || !PetLevels.active(pet) || !PetLevels.combat(pet) || sameSide(pet, target)) {
			return;
		}

		float dealt = Math.min(event.getAmount(), target.getHealth());

		if (dealt <= 0) {
			return;
		}

		double xp = dealt * PetsConfig.combatXpPerDamage() * multiplier(pet);
		EARNED.computeIfAbsent(target, k -> new HashMap<>()).merge(pet.getUUID(), xp, Double::sum);
		PetLevels.addXp(pet, xp);
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onKill(LivingDeathEvent event) {
		if (event.isCanceled() || event.getEntity().level().isClientSide()) {
			return;
		}

		Map<UUID, Double> earned = EARNED.remove(event.getEntity());

		if (earned != null && event.getSource().getEntity() instanceof LivingEntity pet && earned.containsKey(pet.getUUID())
				&& Pets.listed(pet) && PetLevels.active(pet)) {
			PetLevels.addXp(pet, earned.get(pet.getUUID()) * PetsConfig.killBonus());
		}
	}

	/** Once a second for a loaded non-combat party pet: time near its owner, and the blocks they walked together. */
	static void second(LivingEntity pet) {
		if (PetLevels.combat(pet) || Pets.kind(pet) != Pets.Kind.BLOOM || !PetLevels.active(pet) || pet.getServer() == null) {
			WALKS.remove(pet.getUUID());
			return;
		}

		ServerPlayer owner = pet.getServer().getPlayerList().getPlayer(Pets.owner(pet));

		if (owner == null || !owner.isAlive() || owner.level() != pet.level() || owner.distanceToSqr(pet) > NEAR * NEAR) {
			WALKS.remove(pet.getUUID());
			return;
		}

		double xp = PetsConfig.timeXpPerMinute() / 60.0;
		double[] walk = WALKS.get(pet.getUUID());

		if (walk != null) {
			double dx = owner.getX() - walk[0];
			double dz = owner.getZ() - walk[1];
			double step = Math.sqrt(dx * dx + dz * dz);

			// Walking (or running), not riding, flying, gliding or swimming.
			if (step <= MAX_STEP && !owner.isPassenger() && !owner.isFallFlying() && !owner.getAbilities().flying && !owner.isInWater()) {
				walk[2] += step;
			}

			int per = PetsConfig.walkBlocksPerXp();

			if (walk[2] >= per) {
				int steps = (int) (walk[2] / per);
				walk[2] -= steps * per;
				xp += steps;
			}

			walk[0] = owner.getX();
			walk[1] = owner.getZ();
		} else {
			WALKS.put(pet.getUUID(), new double[] {owner.getX(), owner.getZ(), 0.0});
		}

		PetLevels.addXp(pet, xp);
	}

	/**
	 * Its owner feeds the pet its favourite food (sneak + right-click): it heals two hearts, fills 6 hunger, cheers up
	 * (once a minute), and a non-combat pet in the party earns XP (at most every 30 seconds). Nothing is eaten if it's
	 * full, unhurt and has no XP to gain. Public for the game tests.
	 */
	public static Bonding.Result feed(ServerPlayer player, Mob pet, ItemStack food) {
		CompoundTag data = Pets.data(pet);
		long now = player.server.overworld().getGameTime();
		boolean hurt = pet.getHealth() < pet.getMaxHealth();
		boolean hungry = PetMood.hunger(pet) < PetMood.MAX_HUNGER;
		boolean xp = !PetLevels.combat(pet) && PetLevels.active(pet) && PetLevels.level(pet) < PetLevels.MAX
				&& (!data.contains("fedAt") || now - data.getLong("fedAt") >= PetsConfig.feedCooldownTicks());

		if (!hurt && !hungry && !xp) {
			player.displayClientMessage(Component.translatable("bloompets.feed.full", pet.getName()), true);
			return Bonding.Result.OWNED_BY_YOU;
		}

		if (!player.getAbilities().instabuild) {
			food.shrink(1);
		}

		pet.heal(4.0F);
		PetMood.fed(player, pet);
		ServerLevel level = player.serverLevel();
		level.sendParticles(ParticleTypes.HEART, pet.getX(), pet.getY() + pet.getBbHeight() * 0.8, pet.getZ(), 2, 0.3, 0.2, 0.3, 0.0);
		level.playSound(null, pet.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.6F, 1.0F);

		if (xp) {
			data.putLong("fedAt", now);
			PetLevels.addXp(pet, PetsConfig.feedXp());
			player.displayClientMessage(Component.translatable("bloompets.feed.xp", pet.getName(), (int) PetsConfig.feedXp()), true);
		} else {
			player.displayClientMessage(Component.translatable("bloompets.feed.done", pet.getName()), true);
		}

		return Bonding.Result.FED_OWN;
	}
}
