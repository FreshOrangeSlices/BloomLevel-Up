package com.freshorangeslices.legionwarex.sentinel;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * A sentinel holds its post: with no target it stays within a few blocks of it (walking back when it has been drawn
 * off), which keeps its own wandering goals from taking it away; looking around and at players is left to its own
 * goals. With a target it lets its attack goals have it. Not saved: given again whenever the sentinel joins a level.
 */
public final class HoldPostGoal extends Goal {
	private static final double NEAR = 3.0;
	private final Mob mob;
	private final BlockPos post;
	private int repath;

	public HoldPostGoal(Mob mob, BlockPos post) {
		this.mob = mob;
		this.post = post;
		setFlags(EnumSet.of(Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		return mob.getTarget() == null && !mob.isPassenger() && !mob.isLeashed();
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
		if (mob.blockPosition().distSqr(post) <= NEAR * NEAR) {
			mob.getNavigation().stop();
			return;
		}

		if (--repath <= 0) {
			repath = adjustedTickDelay(20);
			mob.getNavigation().moveTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, 1.0);
		}
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return false;
	}
}
