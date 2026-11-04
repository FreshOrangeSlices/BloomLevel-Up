package com.freshorangeslices.legionwarex.companion;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.phys.Vec3;

/**
 * Migamigos swaps one raider in ten for an Enemigo ("A new foe has appeared!"), at the raider's spot, up to about 64
 * blocks from the village. But the Enemigo isn't a raider: it didn't march in, and the add-on despawned it as a stray
 * wild companion once no player was within 32 blocks (FreshOrangeSlices, 2026-11-02: the warning came three times and
 * no Enemigo ever showed). So while a raid is on near it, a wild Enemigo stays (no despawning) and, with nothing to
 * fight, marches on the raid's centre as the raiders do (15 blocks at a time). After the raid it's a wild companion
 * again. Sentinel Enemigos near a raid join in too.
 */
public final class RaidEnemigos {
	/** Close enough to the centre to stop marching: its own goals find it a target from there. */
	private static final double ARRIVED = 12.0;

	private RaidEnemigos() {
	}

	/** The raid going on near a wild Enemigo (within 96 blocks of its centre, as the game counts raids), or null. */
	public static Raid raid(Mob mob) {
		if (!(mob.level() instanceof ServerLevel level) || Companions.owner(mob) != null || !Companions.isEnemigo(mob)) {
			return null;
		}

		Raid raid = level.getRaidAt(mob.blockPosition());
		return raid != null && raid.isActive() && !raid.isOver() ? raid : null;
	}

	/** Marches a wild Enemigo on the raid's centre while it has nothing to fight. Added whenever a companion joins a level. */
	public static final class MarchGoal extends Goal {
		/** After Migamigos' attacks (5), with its post and follow goals (6), which a wild Enemigo never uses. */
		public static final int PRIORITY = 6;
		private final PathfinderMob mob;
		private int repath;

		public MarchGoal(PathfinderMob mob) {
			this.mob = mob;
			setFlags(EnumSet.of(Flag.MOVE));
		}

		/** The raid's centre while it should march there: nothing to fight, not held, and still far off. */
		private BlockPos centre() {
			if (mob.getTarget() != null || mob.isPassenger() || mob.isLeashed()) {
				return null;
			}

			Raid raid = raid(mob);

			if (raid == null) {
				return null;
			}

			BlockPos centre = raid.getCenter();
			return mob.blockPosition().distSqr(centre) > ARRIVED * ARRIVED ? centre : null;
		}

		@Override
		public boolean canUse() {
			return centre() != null;
		}

		@Override
		public boolean canContinueToUse() {
			return centre() != null;
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
			BlockPos centre = centre();

			if (centre == null || (--repath > 0 && !mob.getNavigation().isDone())) {
				return;
			}

			repath = adjustedTickDelay(40);
			Vec3 target = Vec3.atBottomCenterOf(centre);
			Vec3 step = DefaultRandomPos.getPosTowards(mob, 15, 4, target, Math.PI / 2.0);

			if (step == null) {
				step = target;
			}

			mob.getNavigation().moveTo(step.x, step.y, step.z, 1.0);
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return false;
		}
	}
}
