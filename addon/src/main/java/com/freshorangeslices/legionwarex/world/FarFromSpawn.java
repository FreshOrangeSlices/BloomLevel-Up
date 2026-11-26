package com.freshorangeslices.legionwarex.world;

import com.freshorangeslices.legionwarex.AddonConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * How far from spawn a spot is, for the pack's distance rules (FreshOrangeSlices, 2026-11-25): {@link FarHealth} (monsters'
 * health) and {@link FarWeapons} (found weapons' damage). Across the ground from the world's spawn; a Nether block counts
 * as 8, as its coordinates do; any other dimension from its own 0, 0. Steps past {@code farStart}, every
 * {@code farStep} blocks, up to {@code farSteps}, as a fraction (2500 blocks out is 1.5 steps).
 */
public final class FarFromSpawn {
	private FarFromSpawn() {
	}

	public static double distance(ServerLevel level, double x, double z) {
		double dx = x;
		double dz = z;

		if (level.dimension() == Level.OVERWORLD || level.dimension() == Level.NETHER) {
			BlockPos spawn = level.getServer().overworld().getSharedSpawnPos();
			double scale = level.dimension() == Level.NETHER ? 8.0 : 1.0;
			dx = x * scale - spawn.getX();
			dz = z * scale - spawn.getZ();
		}

		return Math.sqrt(dx * dx + dz * dz);
	}

	/** Steps past the start, as a fraction, from 0 up to the cap. */
	public static double steps(double distance) {
		return steps(distance, AddonConfig.farStart(), AddonConfig.farStep(), AddonConfig.farSteps());
	}

	public static double steps(double distance, int start, int step, int max) {
		return Math.max(0.0, Math.min(max, (distance - start) / Math.max(1, step)));
	}
}
