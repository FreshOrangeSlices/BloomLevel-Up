package com.freshorangeslices.legionwarex.gametest;

import com.freshorangeslices.legionwarex.LegionWareX;
import com.freshorangeslices.legionwarex.world.LateCompat;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Compat code held until common setup ({@link LateCompat}, for Copper Age Backport's Sophisticated Storage compat,
 * which stopped the pack's world-and-decor layer from starting). Neither mod is in the add-on's test run, so a
 * stand-in module with an {@code init()} goes through the same steps: held while mods are being built, run once at
 * setup, and run straight away after that.
 */
@GameTestHolder(LegionWareX.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LateCompatGameTests {
	/** Stands in for Copper Age's compat module: public, with a public no-argument {@code init()}. */
	public static final class StandInCompat {
		public int inits;

		public void init() {
			if (!LateCompat.hold(this)) {
				inits++;
			}
		}
	}

	@GameTest(template = "empty")
	public static void heldCompatRunsOnceAtSetup(GameTestHelper helper) {
		StandInCompat compat = new StandInCompat();

		try {
			LateCompat.holdAgainForTest();
			compat.init();
			helper.assertTrue(compat.inits == 0, "a compat module run while mods are being built should wait, ran " + compat.inits);

			int ran = LateCompat.runHeld();
			helper.assertTrue(ran == 1 && compat.inits == 1,
					"common setup should run the held module once (ran " + ran + ", its init ran " + compat.inits + ")");

			compat.init();
			helper.assertTrue(compat.inits == 2, "after setup a module should run straight away, its init ran " + compat.inits);
			helper.assertTrue(LateCompat.runHeld() == 0, "nothing should be left held after setup");
		} finally {
			LateCompat.runHeld();
		}

		helper.succeed();
	}
}
