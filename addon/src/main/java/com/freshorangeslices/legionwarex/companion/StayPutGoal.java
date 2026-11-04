package com.freshorangeslices.legionwarex.companion;

import java.util.EnumSet;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import org.joml.Vector3f;

/**
 * "Cannot Wander" for a companion told to stay: it stands at the spot it was told to stay at. Migamigos' own toggle
 * only stops idle strolling while a companion follows its owner; one that stays still walks about its spot (Migamigos'
 * MoveBackToPostGoal picks a new spot up to 10 blocks out every few ticks and never checks the toggle; FreshOrangeSlices,
 * 2026-11-02). This goal comes before that one, so with wandering off it walks back when it has been pushed or drawn off
 * and otherwise stands still. Fighting, eating and its owner opening its backpack go on as before; with "Can Wander" on,
 * or while following, Migamigos' own goals have it. Not saved: given again whenever the companion joins a level.
 */
public final class StayPutGoal extends Goal {
	/** Migamigos' melee and ranged attacks are at 5 and its post and follow goals at 6, so this sits between. */
	public static final int PRIORITY = 5;
	private static final double NEAR = 1.5;
	private final Mob mob;
	private int repath;

	public StayPutGoal(Mob mob) {
		this.mob = mob;
		setFlags(EnumSet.of(Flag.MOVE));
	}

	/** Its spot, while it's a befriended companion told to stay with wandering off and nothing else to do; else null. */
	private Vector3f post() {
		if (mob.getTarget() != null || mob.isPassenger() || mob.isLeashed() || Companions.owner(mob) == null
				|| Amigos.following(mob) || Amigos.canWander(mob) || Amigos.attacking(mob) || Amigos.containerOpen(mob)) {
			return null;
		}

		return Amigos.post(mob);
	}

	@Override
	public boolean canUse() {
		return post() != null;
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
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
		Vector3f post = post();

		if (post == null) {
			return;
		}

		double x = Math.floor(post.x) + 0.5;
		double z = Math.floor(post.z) + 0.5;
		double dx = mob.getX() - x;
		double dz = mob.getZ() - z;

		if (dx * dx + dz * dz <= NEAR * NEAR && Math.abs(mob.getY() - post.y) < 2.0) {
			mob.getNavigation().stop();
			return;
		}

		if (--repath <= 0 || mob.getNavigation().isDone()) {
			repath = adjustedTickDelay(20);
			mob.getNavigation().moveTo(x, post.y, z, 1.0);
		}
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return false;
	}
}
