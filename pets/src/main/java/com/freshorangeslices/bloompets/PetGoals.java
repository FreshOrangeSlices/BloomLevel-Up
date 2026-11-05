package com.freshorangeslices.bloompets;

import java.util.EnumSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.player.Player;

/**
 * What a Bloom pet does about its owner: the party pet follows them (and pops back to their side when it falls far
 * behind); one outside the party stays near where it was left. Wolves and cats keep their own vanilla following and
 * sitting. Animals run by a "brain" (goats, camels, sniffers, allays, frogs, axolotls) are steered through it.
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
			if (!Pets.inParty(mob) || !free(mob)) {
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
