package com.freshorangeslices.bloompets;

import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToSitGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.player.Player;

/**
 * What a pet does about its owner, by its mode ({@link PetModes}): one on Follow follows them (and pops back to their
 * side when it falls far behind); one on Wander stays near where it was left; one on Stay stands on its spot. A combat
 * pet in the party or on Follow fights for its owner: whatever hurts them, and whatever they hit (never a player, nor
 * the owner's own pets). Companions get the Wander and Stay hold (their own mods follow); Migamigos ones are set
 * through their own state instead. Wolves and cats keep their own vanilla
 * following, sitting and fighting. Animals run by a "brain" (goats, camels, sniffers, allays, frogs, axolotls) are
 * steered through it.
 */
public final class PetGoals {
	/** Mobs given their goals already (goals aren't saved: every new entity object needs them once). */
	private static final Map<Mob, Boolean> GIVEN = new WeakHashMap<>();

	private PetGoals() {
	}

	/**
	 * Gives a listed pet its goals once (on load, and the first second after it's bonded or listed: before, a pet bonded
	 * mid-game had none until it was next loaded).
	 */
	public static void ensure(Mob mob) {
		if (mob.level().isClientSide() || GIVEN.containsKey(mob)) {
			return;
		}

		Pets.Kind kind = Pets.kind(mob);

		if (kind == Pets.Kind.BLOOM) {
			GIVEN.put(mob, true);
			add(mob);
		} else if (kind == Pets.Kind.MODDED && Pets.owner(mob) != null) {
			GIVEN.put(mob, true);

			// Migamigos companions keep to their own Follow, Wander and post (PetModes sets them).
			if (!Amigos.has(mob)) {
				mob.goalSelector.addGoal(0, new Keep(mob));
			}
		}
	}

	/** Whether it sits when told to (wolves, cats, parrots); Zoni's companions, tame too, don't. */
	public static boolean sits(net.minecraft.world.entity.Entity pet) {
		return pet instanceof Mob mob && mob.goalSelector.getAvailableGoals().stream().anyMatch(g -> g.getGoal() instanceof SitWhenOrderedToSitGoal);
	}

	/** Gives a Bloom pet its goals. */
	private static void add(Mob mob) {
		Species species = Pets.species(mob);
		boolean combat = species != null && species.combat();
		// At its post (any pet, wolves and cats too: it keeps them from following their owner off).
		mob.goalSelector.addGoal(2, new HoldPost(mob));
		// Stay and Wander, ahead of everything that moves it (a tame one's own following too).
		mob.goalSelector.addGoal(0, new Keep(mob));

		// Fetch, ahead of every goal that moves it.
		if (Fetch.plays(mob)) {
			mob.goalSelector.addGoal(0, new Fetch.FetchGoal(mob));
		}

		if (combat) {
			mob.targetSelector.addGoal(1, new GuardPost(mob));
			// What its owner hits, ahead of everything else (FreshOrangeSlices' playtest: the golem kept on whatever was
			// hitting it), wolves too.
			mob.targetSelector.addGoal(0, new AssistOwner(mob));
		}

		if (mob instanceof TamableAnimal tamable && tamable.isTame()) {
			return;
		}

		mob.goalSelector.addGoal(2, new FollowOwner(mob));
		mob.goalSelector.addGoal(3, new StayNearHome(mob));

		if (combat) {
			mob.targetSelector.addGoal(1, new DefendOwner(mob));

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

	/**
	 * A fighter fights for its owner while it's on Follow (as a party pet is unless told otherwise; FreshOrangeSlices'
	 * playtest), awake and not at a post (a posted one guards its post). One told to Wander or Stay keeps to its spot.
	 */
	static boolean fightsForOwner(Mob mob) {
		return PetModes.mode(mob) == PetModes.Mode.FOLLOW && !PetPosts.posted(mob) && !Fainting.fainted(mob);
	}

	/** How far from the pet its owner's fight may be for it to join in. */
	private static final double ASSIST_RANGE = 32.0;

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
			Player owner = fightsForOwner(mob) ? owner(mob) : null;

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

	/**
	 * Whatever the owner hits, first: ahead of whatever hurts the owner or the pet, and the moment the owner hits
	 * something else, that instead.
	 */
	public static final class AssistOwner extends TargetGoal {
		private LivingEntity victim;
		private int stamp;

		AssistOwner(Mob mob) {
			super(mob, false);
			setFlags(EnumSet.of(Flag.TARGET));
		}

		@Override
		public boolean canUse() {
			Player owner = fightsForOwner(mob) ? owner(mob) : null;

			if (owner == null || owner.getLastHurtMobTimestamp() == stamp) {
				return false;
			}

			victim = owner.getLastHurtMob();
			return fair(mob, victim) && mob.distanceToSqr(victim) < ASSIST_RANGE * ASSIST_RANGE && canAttack(victim, TargetingConditions.DEFAULT);
		}

		@Override
		public boolean canContinueToUse() {
			Player owner = fightsForOwner(mob) ? owner(mob) : null;

			if (owner == null) {
				return false;
			}

			// Its owner went for something else since: let go, to take that up at once.
			LivingEntity latest = owner.getLastHurtMob();

			if (owner.getLastHurtMobTimestamp() != stamp && latest != victim && fair(mob, latest)
					&& mob.distanceToSqr(latest) < ASSIST_RANGE * ASSIST_RANGE) {
				return false;
			}

			return super.canContinueToUse();
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
			return (fightsForOwner(pet) || PetPosts.posted(pet)) && !Fainting.fainted(pet) && super.canUse();
		}

		@Override
		public boolean canContinueToUse() {
			return (fightsForOwner(pet) || PetPosts.posted(pet)) && !Fainting.fainted(pet) && super.canContinueToUse();
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

	/**
	 * Stay and Wander for the pets whose own goals would walk them off: Stay for any pet that doesn't sit (it stands on
	 * its spot, and goes back to it after a fight), Wander for a tame one and for a companion (it roams around its spot
	 * itself, so its own following doesn't take it away). Ahead of every other goal that moves it, but never while it
	 * fights, plays fetch, keeps a post or is fainted.
	 */
	public static final class Keep extends Goal {
		private static final double WANDER = 8.0;
		private final Mob mob;
		private Boolean sits;
		private BlockPos home;
		private BlockPos spot;
		private int next;
		private int repath;

		Keep(Mob mob) {
			this.mob = mob;
			setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
		}

		private PetModes.Mode wanted() {
			if (!free(mob) || mob.getTarget() != null && mob.getTarget().isAlive() || Fetch.playing(mob) || PetPosts.posted(mob)
					|| Fainting.fainted(mob)) {
				return null;
			}

			if (sits == null) {
				sits = sits(mob);
			}

			PetModes.Mode mode = PetModes.mode(mob);
			boolean tameLike = mob instanceof TamableAnimal tamable && tamable.isTame() || Pets.kind(mob) == Pets.Kind.MODDED;
			return mode == PetModes.Mode.STAY && !sits || mode == PetModes.Mode.WANDER && tameLike ? mode : null;
		}

		@Override
		public boolean canUse() {
			return wanted() != null;
		}

		@Override
		public boolean canContinueToUse() {
			return wanted() != null;
		}

		@Override
		public void start() {
			home = Pets.home(mob);

			if (home == null) {
				home = mob.blockPosition();
				Pets.setHome(mob, home);
			}

			spot = null;
			next = 0;
			repath = 0;
		}

		@Override
		public void stop() {
			mob.getNavigation().stop();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			// Told again meanwhile (Stay after Wander): its new spot.
			BlockPos told = Pets.home(mob);

			if (told != null) {
				home = told;
			}

			double away = mob.blockPosition().distSqr(home);

			if (away > 48.0 * 48.0) {
				mob.teleportTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
				mob.getNavigation().stop();
				return;
			}

			BlockPos goal = home;

			if (PetModes.mode(mob) == PetModes.Mode.WANDER) {
				if (--next <= 0) {
					next = adjustedTickDelay(100 + mob.getRandom().nextInt(160));
					spot = home.offset((int) Math.round((mob.getRandom().nextDouble() * 2 - 1) * WANDER), 0,
							(int) Math.round((mob.getRandom().nextDouble() * 2 - 1) * WANDER));
				}

				goal = away > sq(PetsConfig.homeRange()) || spot == null ? home : spot;
			}

			if (mob.blockPosition().distSqr(goal) <= 2.0) {
				// There: still (a brain-run animal's own walk target cleared too).
				mob.getNavigation().stop();

				if (mob.getBrain().checkMemory(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED)) {
					mob.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
				}

				return;
			}

			if (--repath > 0) {
				return;
			}

			repath = adjustedTickDelay(20);

			if (!walkByBrain(mob, new WalkTarget(goal, 0.9F, 1))) {
				mob.getNavigation().moveTo(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5, 0.9);
			}
		}
	}

	/** A pet on Follow (the party pet's always is, unless told otherwise) follows its owner. */
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
			if (PetModes.mode(mob) != PetModes.Mode.FOLLOW || PetPosts.posted(mob) || !free(mob) || Fainting.fainted(mob)) {
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
			return owner != null && owner.isAlive() && owner.level() == mob.level() && PetModes.mode(mob) == PetModes.Mode.FOLLOW
					&& !PetPosts.posted(mob) && free(mob) && mob.distanceToSqr(owner) > 3.0 * 3.0;
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
			if (PetModes.mode(mob) != PetModes.Mode.WANDER || PetPosts.posted(mob) || !free(mob)) {
				return false;
			}

			home = Pets.home(mob);
			return home != null && mob.blockPosition().distSqr(home) > sq(PetsConfig.homeRange());
		}

		@Override
		public boolean canContinueToUse() {
			return home != null && PetModes.mode(mob) == PetModes.Mode.WANDER && !PetPosts.posted(mob) && free(mob)
					&& mob.blockPosition().distSqr(home) > 4.0 * 4.0;
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

	/**
	 * A pet at its post strolls around it (within 6 blocks), now and then standing at the post itself; a fight comes
	 * first, and afterwards it heads back. Far off (called away and back, a long chase) it pops back to its post.
	 */
	public static final class HoldPost extends Goal {
		private final Mob mob;
		private BlockPos post;
		private BlockPos spot;
		private int next;
		private int repath;

		HoldPost(Mob mob) {
			this.mob = mob;
			setFlags(EnumSet.of(Flag.MOVE));
		}

		private boolean fighting() {
			return mob.getTarget() != null && mob.getTarget().isAlive();
		}

		@Override
		public boolean canUse() {
			post = PetPosts.post(mob);
			return post != null && free(mob) && !fighting();
		}

		@Override
		public boolean canContinueToUse() {
			return post != null && post.equals(PetPosts.post(mob)) && free(mob) && !fighting();
		}

		@Override
		public void start() {
			spot = null;
			next = 0;
			repath = 0;
		}

		@Override
		public void stop() {
			mob.getNavigation().stop();
		}

		@Override
		public void tick() {
			if (Fainting.fainted(mob)) {
				return;
			}

			double away = mob.blockPosition().distSqr(post);

			if (away > 48.0 * 48.0) {
				mob.teleportTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5);
				mob.getNavigation().stop();
				return;
			}

			if (--next <= 0) {
				// 5 to 13 seconds a spot; one time in four, the post itself.
				next = adjustedTickDelay(100 + mob.getRandom().nextInt(160));
				spot = mob.getRandom().nextInt(4) == 0 ? null
						: post.offset((int) Math.round((mob.getRandom().nextDouble() * 2 - 1) * PetPosts.ROAM), 0,
								(int) Math.round((mob.getRandom().nextDouble() * 2 - 1) * PetPosts.ROAM));
			}

			BlockPos goal = away > sq(PetPosts.ROAM) || spot == null ? post : spot;

			if (mob.blockPosition().distSqr(goal) <= 2.0 || --repath > 0) {
				return;
			}

			repath = adjustedTickDelay(20);

			if (!walkByBrain(mob, new WalkTarget(goal, 0.8F, 1))) {
				mob.getNavigation().moveTo(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5, 0.8);
			}
		}
	}

	/** A combat pet at its post goes for the nearest monster within 16 blocks of it (24 at level 100), not too far after. */
	public static final class GuardPost extends TargetGoal {
		private LivingEntity found;
		private int wait;

		GuardPost(Mob mob) {
			super(mob, false);
			setFlags(EnumSet.of(Flag.TARGET));
		}

		@Override
		public boolean canUse() {
			if (--wait > 0) {
				return false;
			}

			wait = 10;
			BlockPos post = PetPosts.post(mob);

			if (post == null || Fainting.fainted(mob) || PetMood.sad(mob) || !(mob.level() instanceof net.minecraft.server.level.ServerLevel level)) {
				return false;
			}

			found = PetPosts.nearestMonster(level, post, PetPosts.guardRadius(mob), mob);
			return found != null;
		}

		@Override
		public void start() {
			mob.setTarget(found);
			super.start();
		}

		@Override
		public boolean canContinueToUse() {
			BlockPos post = PetPosts.post(mob);
			LivingEntity target = mob.getTarget();
			double reach = PetPosts.guardRadius(mob) + 8.0;
			return post != null && target != null && target.isAlive() && !Fainting.fainted(mob)
					&& target.distanceToSqr(post.getX() + 0.5, post.getY(), post.getZ() + 0.5) <= reach * reach;
		}
	}

	static double sq(double value) {
		return value * value;
	}
}
