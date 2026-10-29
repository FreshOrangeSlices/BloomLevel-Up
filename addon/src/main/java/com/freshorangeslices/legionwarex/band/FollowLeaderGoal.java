package com.freshorangeslices.legionwarex.band;

import java.util.EnumSet;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * A band follower that isn't a patroller (a pirate) keeps near its leader: when more than 10 blocks off and not
 * fighting, it walks back to within 4. Past 64 blocks it gives up (the band has scattered). Not saved: the add-on
 * gives it again whenever the follower joins a level (BandEvents.onJoin).
 */
public final class FollowLeaderGoal extends Goal {
	private static final double START = 10.0;
	private static final double STOP = 4.0;
	private static final double GIVE_UP = 64.0;

	private final Mob mob;
	private final UUID leaderId;
	private final double speed;
	private Mob leader;
	private int repath;

	public FollowLeaderGoal(Mob mob, UUID leaderId, double speed) {
		this.mob = mob;
		this.leaderId = leaderId;
		this.speed = speed;
		setFlags(EnumSet.of(Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		if (mob.getTarget() != null || mob.isPassenger() || mob.isLeashed() || !(mob.level() instanceof ServerLevel level)
				|| !(level.getEntity(leaderId) instanceof Mob found) || !found.isAlive()) {
			return false;
		}

		double distance = mob.distanceToSqr(found);

		if (distance < START * START || distance > GIVE_UP * GIVE_UP) {
			return false;
		}

		leader = found;
		return true;
	}

	@Override
	public boolean canContinueToUse() {
		return leader != null && leader.isAlive() && mob.getTarget() == null && mob.distanceToSqr(leader) > STOP * STOP
				&& mob.distanceToSqr(leader) < GIVE_UP * GIVE_UP;
	}

	@Override
	public void start() {
		repath = 0;
	}

	@Override
	public void stop() {
		leader = null;
		mob.getNavigation().stop();
	}

	@Override
	public void tick() {
		if (--repath <= 0) {
			repath = adjustedTickDelay(10);
			mob.getNavigation().moveTo(leader, speed);
		}
	}
}
