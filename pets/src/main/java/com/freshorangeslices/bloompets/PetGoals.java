package com.freshorangeslices.bloompets;

import java.util.EnumSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.player.Player;

/**
 * What a Bloom pet does about its owner: the party pet follows them (and pops back to their side when it falls far
 * behind); one outside the party stays near where it was left. A combat pet in the party fights for its owner: whatever
 * hurts them, and whatever they hit (never a player, nor the owner's own pets). Wolves and cats keep their own vanilla
 * following, sitting and fighting. Animals run by a "brain" (goats, camels, sniffers, allays, frogs, axolotls) are
 * steered through it.
 */
public final class PetGoals {
	private PetGoals() {
	}

	/** Gives a Bloom pet its goals (they aren't saved, so on every load). */
	public static void add(Mob mob) {
		if (mob instanceof TamableAnimal tamable && tamable.isTame()) {
			return;
		}

		mob.goalSelector.addGoal(2, new FollowOwner(mob));
		mob.goalSelector.addGoal(3, new StayNearHome(mob));
		Species species = Pets.species(mob);

		if (species != null && species.combat()) {
			mob.targetSelector.addGoal(1, new DefendOwner(mob));
			mob.targetSelector.addGoal(2, new AssistOwner(mob));

			// One that has no melee attack of its own (the goat) gets one.
			if (mob instanceof PathfinderMob pathfinder && mob.goalSelector.getAvailableGoals().stream().noneMatch(g -> g.getGoal() instanceof MeleeAttackGoal)) {
				mob.goalSelector.addGoal(1, new Fight(pathfinder));
			}
		}
	}

	/** Someone the pet may fight for its owner: alive, not a player, not one of the owner's own. */
	static boolean fair(Mob mob, LivingEntity target) {
		return target != null && target.isAlive() && !(target instanceof Player) && target != mob && !PetXp.sameSide(mob, target);
	}

	/** Whatever hurts the owner. */
	public static final class DefendOwner extends TargetGoal {
		private LivingEntity attacker;
		private int stamp;

		DefendOwner(Mob mob) {
			super(mob, false);
			setFlags(EnumSet.of(Flag.TARGET));
		}

		@Override
		public boolean canUse() {
			Player owner = Pets.inParty(mob) && !Fainting.fainted(mob) ? owner(mob) : null;

			if (owner == null || owner.getLastHurtByMobTimestamp() == stamp) {
				return false;
			}

			attacker = owner.getLastHurtByMob();
			return fair(mob, attacker) && canAttack(attacker, TargetingConditions.DEFAULT);
		}

		@Override
		public void start() {
			mob.setTarget(attacker);
			Player owner = owner(mob);
			stamp = owner == null ? stamp : owner.getLastHurtByMobTimestamp();
			super.start();
		}
	}

	/** Whatever the owner hits. */
	public static final class AssistOwner extends TargetGoal {
		private LivingEntity victim;
		private int stamp;

		AssistOwner(Mob mob) {
			super(mob, false);
			setFlags(EnumSet.of(Flag.TARGET));
		}

		@Override
		public boolean canUse() {
			Player owner = Pets.inParty(mob) && !Fainting.fainted(mob) ? owner(mob) : null;

			if (owner == null || owner.getLastHurtMobTimestamp() == stamp) {
				return false;
			}

			victim = owner.getLastHurtMob();
			return fair(mob, victim) && canAttack(victim, TargetingConditions.DEFAULT);
		}

		@Override
		public void start() {
			mob.setTarget(victim);
			Player owner = owner(mob);
			stamp = owner == null ? stamp : owner.getLastHurtMobTimestamp();
			super.start();
		}
	}

	/** A melee attack for a combat pet that has none (the goat), only while it's in the party. */
	public static final class Fight extends MeleeAttackGoal {
		private final PathfinderMob pet;

		Fight(PathfinderMob pet) {
			super(pet, 1.25, true);
			this.pet = pet;
		}

		@Override
		public boolean canUse() {
			return Pets.inParty(pet) && !Fainting.fainted(pet) && super.canUse();
		}

		@Override
		public boolean canContinueToUse() {
			return Pets.inParty(pet) && !Fainting.fainted(pet) && super.canContinueToUse();
		}

		@Override
		public void tick() {
			// Its brain would walk it elsewhere.
			pet.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			super.tick();
		}
	}

	/** Steers a brain-run animal toward a spot; false if it has no brain walk target (then its navigation is used). */
	static boolean walkByBrain(Mob mob, WalkTarget target) {
		Brain<?> brain = mob.getBrain();

		if (brain.checkMemory(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED)) {
			brain.setMemory(MemoryModuleType.WALK_TARGET, target);
			return true;
		}

		return false;
	}

	static Player owner(Mob mob) {
		UUID owner = Pets.owner(mob);
		return owner == null ? null : mob.level().getPlayerByUUID(owner);
	}

	static boolean free(Mob mob) {
		return !mob.isLeashed() && !mob.isPassenger() && !mob.isVehicle();
	}

	/** The party pet follows its owner. */
	public static final class FollowOwner extends Goal {
		private final Mob mob;
		private Player owner;
		private int repath;

		FollowOwner(Mob mob) {
			this.mob = mob;
			setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			if (!Pets.inParty(mob) || !free(mob) || Fainting.fainted(mob)) {
				return false;
			}

			Player player = owner(mob);

			if (player == null || player.isSpectator() || mob.distanceToSqr(player) < sq(PetsConfig.followDistance())) {
				return false;
			}

			owner = player;
			return true;
		}

		@Override
		public boolean canContinueToUse() {
			return owner != null && owner.isAlive() && owner.level() == mob.level() && Pets.inParty(mob) && free(mob)
					&& mob.distanceToSqr(owner) > 3.0 * 3.0;
		}

		@Override
		public void start() {
			repath = 0;
		}

		@Override
		public void stop() {
			owner = null;
			mob.getNavigation().stop();
		}

		@Override
		public void tick() {
			mob.getLookControl().setLookAt(owner, 10.0F, mob.getMaxHeadXRot());

			if (mob.distanceToSqr(owner) > sq(PetsConfig.teleportDistance())) {
				Calling.bring(mob, owner);
				return;
			}

			if (--repath > 0) {
				return;
			}

			repath = adjustedTickDelay(10);

			if (!walkByBrain(mob, new WalkTarget(owner, 1.0F, 2))) {
				mob.getNavigation().moveTo(owner, 1.2);
			}
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}
	}

	/** A Bloom pet outside the party stays near where it was left (its home), walking back when it strays. */
	public static final class StayNearHome extends Goal {
		private final Mob mob;
		private BlockPos home;
		private int repath;

		StayNearHome(Mob mob) {
			this.mob = mob;
			setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			if (Pets.inParty(mob) || !free(mob)) {
				return false;
			}

			home = Pets.home(mob);
			return home != null && mob.blockPosition().distSqr(home) > sq(PetsConfig.homeRange());
		}

		@Override
		public boolean canContinueToUse() {
			return home != null && !Pets.inParty(mob) && free(mob) && mob.blockPosition().distSqr(home) > 4.0 * 4.0;
		}

		@Override
		public void start() {
			repath = 0;
		}

		@Override
		public void stop() {
			mob.getNavigation().stop();
		}

		@Override
		public void tick() {
			if (--repath > 0) {
				return;
			}

			repath = adjustedTickDelay(20);

			if (!walkByBrain(mob, new WalkTarget(home, 1.0F, 2))) {
				mob.getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 1.0);
			}
		}
	}

	static double sq(double value) {
		return value * value;
	}
}
