package com.freshorangeslices.legionwarex.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/** The "room" template (12 by 12, stone all round): where its floor is in a test's own coordinates. */
final class Rooms {
	private Rooms() {
	}

	/**
	 * The height a mob stands at on the room's floor, found rather than assumed (the game places a test's structure
	 * relative to its structure block, so the floor's height in the helper's coordinates is read off the blocks).
	 */
	static int standY(GameTestHelper helper) {
		for (int y = 0; y <= 4; y++) {
			if (helper.getBlockState(new BlockPos(5, y, 5)).is(Blocks.STONE) && helper.getBlockState(new BlockPos(5, y + 1, 5)).isAir()) {
				return y + 1;
			}
		}

		throw new IllegalStateException("No room floor found under the test");
	}

	/** A spot on the floor, x and z in the room's own coordinates (1 to 10 inside the walls). */
	static BlockPos on(GameTestHelper helper, int x, int z) {
		return new BlockPos(x, standY(helper), z);
	}
}
