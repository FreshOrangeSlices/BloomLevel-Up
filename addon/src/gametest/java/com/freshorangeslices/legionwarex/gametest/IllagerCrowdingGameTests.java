package com.freshorangeslices.legionwarex.gametest;

import com.freshorangeslices.legionwarex.LegionWareX;
import com.freshorangeslices.legionwarex.world.IllagerCrowding;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Illagers grow rarer where they keep coming (world/IllagerCrowding). One test, all in one tick, so the warmth it sets
 * never reaches the tests running beside it.
 */
@GameTestHolder(LegionWareX.MOD_ID)
@PrefixGameTestTemplate(false)
public final class IllagerCrowdingGameTests {
	/** Warm enough that the chance is about one in a billion. */
	private static final double HOT = 1.0e9;

	@GameTest(template = "empty")
	public static void illagersGrowRarerWhereTheyKeepComing(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		IllagerCrowding data = IllagerCrowding.of(level);
		BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
		long now = level.getGameTime();

		try {
			// The numbers.
			data.cool(at);
			near(helper, data.warmthAt(at, now), 0.0, "A spot starts cool");
			near(helper, IllagerCrowding.chance(0.0), 1.0, "A cool spot always lets them come");
			data.warm(at, now, 1.0);
			near(helper, IllagerCrowding.chance(data.warmthAt(at, now)), 0.5, "One arrival halves the next one's chance");
			data.warm(at, now, 1.0);
			near(helper, IllagerCrowding.chance(data.warmthAt(at, now)), 1.0 / 3.0, "Two make it a third");
			near(helper, data.warmthAt(at, now + IllagerCrowding.halfLife()), 1.0, "After the cooling time the warmth is halved");
			near(helper, data.warmthAt(at.offset(48, 0, 0), now), 2.0, "Three chunks away still feels it (radius 4)");
			near(helper, data.warmthAt(at.offset(96, 0, 0), now), 0.0, "Six chunks away doesn't");

			// A vanilla patrol where it's hot is kept away: Forge keeps the cancelled mob out of the world.
			data.warm(at, now, HOT);
			data.forgetGroup();
			Pillager patroller = pillager(level, at);
			ForgeEventFactory.onFinalizeSpawn(patroller, level, level.getCurrentDifficultyAt(at), MobSpawnType.PATROL, null, null);
			helper.assertTrue(patroller.isSpawnCancelled(), "A patrol member is kept away where it's hot");
			helper.assertFalse(level.addFreshEntity(patroller), "... and never joins the world");

			// So is a natural spawn (out of any structure), and the add-on's war bands and scouts ask the same.
			data.forgetGroup();
			Pillager wild = pillager(level, at);
			ForgeEventFactory.onFinalizeSpawn(wild, level, level.getCurrentDifficultyAt(at), MobSpawnType.NATURAL, null, null);
			helper.assertTrue(wild.isSpawnCancelled(), "A natural spawn is thinned where it's hot");
			helper.assertFalse(IllagerCrowding.roll(level, at, level.getRandom(), "a test band"), "A war band or scout is kept away where it's hot");

			// Commands and raids are left alone however hot.
			data.forgetGroup();
			Pillager summoned = pillager(level, at);
			ForgeEventFactory.onFinalizeSpawn(summoned, level, level.getCurrentDifficultyAt(at), MobSpawnType.COMMAND, null, null);
			helper.assertFalse(summoned.isSpawnCancelled(), "A summoned pillager comes anyway");
			Pillager raider = pillager(level, at);
			ForgeEventFactory.onFinalizeSpawn(raider, level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null, null);
			helper.assertFalse(raider.isSpawnCancelled(), "A raid's pillager comes anyway");

			// Where it's cool a patrol comes whole and warms its spot once; a natural spawn doesn't warm.
			data.cool(at);
			data.forgetGroup();
			Pillager leader = pillager(level, at);
			ForgeEventFactory.onFinalizeSpawn(leader, level, level.getCurrentDifficultyAt(at), MobSpawnType.PATROL, null, null);
			helper.assertFalse(leader.isSpawnCancelled(), "A patrol comes where it's cool");
			near(helper, data.warmthAt(at, now), 1.0, "... and warms its spot by one");
			Pillager member = pillager(level, at.offset(3, 0, 2));
			ForgeEventFactory.onFinalizeSpawn(member, level, level.getCurrentDifficultyAt(at), MobSpawnType.PATROL, null, null);
			helper.assertFalse(member.isSpawnCancelled(), "Its other members come with it");
			near(helper, data.warmthAt(at, now), 1.0, "... and the patrol counts once");
			data.cool(at);
			data.forgetGroup();
			Pillager calm = pillager(level, at);
			ForgeEventFactory.onFinalizeSpawn(calm, level, level.getCurrentDifficultyAt(at), MobSpawnType.NATURAL, null, null);
			helper.assertFalse(calm.isSpawnCancelled(), "A natural spawn comes where it's cool");
			near(helper, data.warmthAt(at, now), 0.0, "... and doesn't warm it");
			helper.assertTrue(IllagerCrowding.roll(level, at, level.getRandom(), "a test band"), "A war band comes where it's cool");
		} finally {
			data.cool(at);
			data.forgetGroup();
		}

		helper.succeed();
	}

	private static Pillager pillager(ServerLevel level, BlockPos pos) {
		Pillager pillager = EntityType.PILLAGER.create(level);
		pillager.moveTo(Vec3.atBottomCenterOf(pos));
		return pillager;
	}

	private static void near(GameTestHelper helper, double value, double expected, String what) {
		helper.assertTrue(Math.abs(value - expected) < 1.0e-6, what + " (expected " + expected + ", got " + value + ")");
	}
}
