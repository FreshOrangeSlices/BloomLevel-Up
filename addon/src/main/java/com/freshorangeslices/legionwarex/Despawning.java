package com.freshorangeslices.legionwarex;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * Vanilla's despawning for a monster (Mob.checkDespawn), for mobs the game keeps for good (wild Migamigos companions,
 * Bosses'Rise's pirates in a band): checked once a second instead of every tick.
 */
public final class Despawning {
	/** Vanilla monsters' despawn distances, in blocks: at once past the first, now and then past the second. */
	public static final int FAR = 128;
	public static final int NEAR = 32;

	private Despawning() {
	}

	/** Never one that is name-tagged, leashed, riding or ridden. */
	public static boolean keptByPlayer(Mob mob) {
		return mob.hasCustomName() || mob.isLeashed() || mob.isPassenger() || mob.isVehicle();
	}

	/** Once a second (every 20 ticks): gone, or its idle time reset with a player close. */
	public static void tick(Mob mob) {
		Player nearest = mob.level().getNearestPlayer(mob, -1.0);

		if (nearest == null) {
			return;
		}

		double distance = nearest.distanceToSqr(mob);

		if (despawns(distance, mob.getNoActionTime(), mob.getRandom())) {
			mob.discard();
		} else if (distance < NEAR * NEAR) {
			mob.setNoActionTime(0);
		}
	}

	/**
	 * Gone at once with no player within 128 blocks; past 32, after 30 seconds without a player close, a 1 in 40
	 * chance a second (vanilla's 1 in 800 a tick).
	 */
	public static boolean despawns(double distanceSqr, int noActionTicks, RandomSource random) {
		if (distanceSqr > FAR * FAR) {
			return true;
		}

		return distanceSqr > NEAR * NEAR && noActionTicks > 600 && random.nextInt(40) == 0;
	}
}
