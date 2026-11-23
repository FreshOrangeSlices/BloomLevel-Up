package com.freshorangeslices.legionwarex.gametest;

import com.freshorangeslices.legionwarex.AddonConfig;
import com.freshorangeslices.legionwarex.LegionWareX;
import com.freshorangeslices.legionwarex.night.BloodMoon;
import com.freshorangeslices.legionwarex.night.NightCycle;
import com.freshorangeslices.legionwarex.night.NightEvents;
import com.freshorangeslices.legionwarex.night.NightMobs;
import com.freshorangeslices.legionwarex.night.NightTable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Themed nights (night/). Each test runs in one tick with its own stand-in nights (put back after), so nothing it sets
 * reaches the tests beside it; the world's own cycle stays as it was. The skeletons a test leaves behind carry night
 * number -1 and go with the next sweep.
 */
@GameTestHolder(LegionWareX.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NightGameTests {
	private static NightTable.Mob mob(EntityType<?> type, int weight) {
		return new NightTable.Mob(EntityType.getKey(type), weight, NightTable.Climate.ANY, 0, null);
	}

	private static NightTable.Theme theme(String name, double health, NightTable.Mob... mobs) {
		return new NightTable.Theme(new ResourceLocation(LegionWareX.MOD_ID, "test_" + name), "Test " + name, "", "", ChatFormatting.RED, health, List.of(mobs));
	}

	@GameTest(template = "empty")
	public static void theDeckDealsEveryNightOnceBeforeAnyComesAgain(GameTestHelper helper) {
		List<NightTable.Theme> saved = new ArrayList<>(NightTable.all().values());

		try {
			NightTable.set(List.of(theme("a", 1.5, mob(EntityType.SKELETON, 1)), theme("b", 1.5, mob(EntityType.ZOMBIE, 1)),
					theme("c", 1.5, mob(EntityType.SPIDER, 1))));
			NightCycle cycle = NightCycle.fresh();
			Set<ResourceLocation> dealt = new HashSet<>();
			ResourceLocation third = null;

			for (int i = 0; i < 3; i++) {
				third = cycle.deal(helper.getLevel().getRandom()).id();
				dealt.add(third);
			}

			helper.assertTrue(dealt.size() == 3, "three deals should give the three nights, gave " + dealt);

			for (int round = 0; round < 20; round++) {
				ResourceLocation next = cycle.deal(helper.getLevel().getRandom()).id();
				helper.assertFalse(next.equals(third), "the same night twice running across a shuffle: " + next);
				third = next;
			}

			// A night whose mobs aren't installed is never dealt.
			NightTable.set(List.of(theme("a", 1.5, mob(EntityType.SKELETON, 1)),
					new NightTable.Theme(new ResourceLocation(LegionWareX.MOD_ID, "test_missing"), "Missing", "", "", ChatFormatting.RED, 1.5,
							List.of(new NightTable.Mob(new ResourceLocation("nosuchmod", "ghost"), 1, NightTable.Climate.ANY, 0, null)))));
			NightCycle other = NightCycle.fresh();

			for (int i = 0; i < 5; i++) {
				helper.assertTrue(other.deal(helper.getLevel().getRandom()).id().getPath().equals("test_a"), "only the installed night is dealt");
			}
		} finally {
			NightTable.set(saved);
		}

		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void naturalSpawnsComeAsTheNightsMobs(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();

		try {
			NightEvents.testTheme = theme("bones", 2.0, mob(EntityType.SKELETON, 1));
			NightEvents.testShare = 1.0;

			for (int x = 0; x < 4; x++) {
				helper.setBlock(new BlockPos(x, 1, 1), Blocks.STONE);
				helper.setBlock(new BlockPos(x, 1, 3), Blocks.STONE);
			}

			// Another monster spawning naturally comes as one of the night's, on its spot, with the night's health.
			for (int x = 0; x < 4; x++) {
				BlockPos at = helper.absolutePos(new BlockPos(x, 2, 1));
				Zombie zombie = EntityType.ZOMBIE.create(level);
				zombie.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
				ForgeEventFactory.onFinalizeSpawn(zombie, level, level.getCurrentDifficultyAt(at), MobSpawnType.NATURAL, null, null);
				helper.assertTrue(zombie.isSpawnCancelled(), "the zombie should have been turned away for one of the night's");
				zombie.discard();
				List<Skeleton> came = level.getEntitiesOfClass(Skeleton.class, new AABB(at).inflate(0.5), NightMobs::isNight);
				helper.assertTrue(came.size() == 1, "a night skeleton should stand where the zombie would have, found " + came.size());
				Skeleton skeleton = came.get(0);
				helper.assertTrue(Math.abs(skeleton.getMaxHealth() - 40.0F) < 0.01F && Math.abs(skeleton.getHealth() - 40.0F) < 0.01F,
						"the night's skeleton should have twice its health, full: " + skeleton.getHealth() + "/" + skeleton.getMaxHealth());
				skeleton.discard();
			}

			// The night's own mob spawning naturally is let through, marked, and gets the night's health as it joins.
			BlockPos own = helper.absolutePos(new BlockPos(0, 2, 3));
			Skeleton skeleton = EntityType.SKELETON.create(level);
			skeleton.moveTo(own.getX() + 0.5, own.getY(), own.getZ() + 0.5);
			ForgeEventFactory.onFinalizeSpawn(skeleton, level, level.getCurrentDifficultyAt(own), MobSpawnType.NATURAL, null, null);
			helper.assertFalse(skeleton.isSpawnCancelled(), "the night's own skeleton comes");
			helper.assertTrue(NightMobs.isNight(skeleton), "... marked as the night's");
			helper.assertTrue(level.addFreshEntity(skeleton), "... and joins");
			helper.assertTrue(Math.abs(skeleton.getMaxHealth() - 40.0F) < 0.01F, "... with the night's health: " + skeleton.getMaxHealth());
			skeleton.discard();

			// Left alone: animals, spawns of any other kind, and every spawn when the share is 0.
			BlockPos at = helper.absolutePos(new BlockPos(2, 2, 3));
			Cow cow = EntityType.COW.create(level);
			cow.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
			ForgeEventFactory.onFinalizeSpawn(cow, level, level.getCurrentDifficultyAt(at), MobSpawnType.NATURAL, null, null);
			helper.assertFalse(cow.isSpawnCancelled(), "a cow is no monster");
			cow.discard();
			Zombie summoned = EntityType.ZOMBIE.create(level);
			summoned.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
			ForgeEventFactory.onFinalizeSpawn(summoned, level, level.getCurrentDifficultyAt(at), MobSpawnType.SPAWNER, null, null);
			helper.assertFalse(summoned.isSpawnCancelled(), "a spawner's zombie comes anyway");
			summoned.discard();
			NightEvents.testShare = 0.0;
			Zombie kept = EntityType.ZOMBIE.create(level);
			kept.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
			ForgeEventFactory.onFinalizeSpawn(kept, level, level.getCurrentDifficultyAt(at), MobSpawnType.NATURAL, null, null);
			helper.assertFalse(kept.isSpawnCancelled(), "with a share of 0 the zombie comes");
			kept.discard();
		} finally {
			NightEvents.testTheme = null;
			NightEvents.testShare = null;
		}

		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void leftoversGoOnceTheirNightIsOverAndNoOneIsNear(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		NightCycle over = NightCycle.fresh();
		NightTable.Theme theme = theme("leftover", 1.5, mob(EntityType.SKELETON, 1));
		Mob marked = helper.spawn(EntityType.SKELETON, new BlockPos(1, 2, 1));
		marked.setNoAi(true);
		NightMobs.mark(marked, theme, 3);
		helper.assertTrue(NightMobs.leaves(marked, over, false), "a mob of an ended night with no one near goes");
		helper.assertFalse(NightMobs.leaves(marked, over, true), "... not while a player is near");
		marked.setPersistenceRequired();
		helper.assertFalse(NightMobs.leaves(marked, over, false), "... nor once a player has kept it (a name tag)");
		Mob plain = helper.spawn(EntityType.SKELETON, new BlockPos(3, 2, 1));
		helper.assertFalse(NightMobs.leaves(plain, over, false), "a skeleton of no night stays");
		marked.discard();
		plain.discard();
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void theBloodMoonTakesTheNightAndTheThemedOneWaits(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<NightTable.Theme> saved = new ArrayList<>(NightTable.all().values());
		int day = AddonConfig.DAY;
		int every = AddonConfig.themedNightEveryDays();

		try {
			NightTable.set(List.of(theme("a", 1.5, mob(EntityType.SKELETON, 1))));
			NightCycle cycle = NightCycle.fresh();
			cycle.tick(level, 0L);
			helper.assertTrue(cycle.nextDay() == every, "a new world's first themed night is a full cycle away: day " + cycle.nextDay());

			BloodMoon.testUp = true;
			cycle.tick(level, (long) every * day + 13000L);
			helper.assertTrue(cycle.active() == null, "no themed night on the Blood Moon's");
			helper.assertTrue(cycle.nextDay() == every + 1, "... it waits for the next night: day " + cycle.nextDay());

			BloodMoon.testUp = false;
			cycle.tick(level, (long) (every + 1) * day + 13000L);
			helper.assertTrue(cycle.active() != null, "the next night it comes");
			helper.assertTrue(cycle.count() == 1, "... as themed night #1");
			cycle.tick(level, (long) (every + 1) * day + 20000L);
			helper.assertTrue(cycle.active() != null, "it lasts the night");

			BloodMoon.testUp = true;
			cycle.tick(level, (long) (every + 1) * day + 20020L);
			helper.assertTrue(cycle.active() == null, "a Blood Moon rising stops it");
			helper.assertTrue(cycle.nextDay() == 2L * every + 1, "the next one a full cycle on: day " + cycle.nextDay());

			BloodMoon.testUp = false;
			cycle.tick(level, (long) (2 * every + 1) * day + 13000L);
			helper.assertTrue(cycle.active() != null, "a cycle later the next comes");
			cycle.tick(level, (long) (2 * every + 2) * day + 100L);
			helper.assertTrue(cycle.active() == null, "sleeping through it (the time jumped to morning) ends it");

			// A mob that waits for a boss stays out until a player has killed one.
			NightTable.Mob waits = new NightTable.Mob(EntityType.getKey(EntityType.SKELETON), 1, NightTable.Climate.ANY, 0,
					new ResourceLocation("minecraft", "wither"));
			BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
			helper.assertFalse(NightEvents.allowed(level, cycle, waits, at), "Freeze waits for the Wither");
			cycle.beat(new ResourceLocation("minecraft", "wither"));
			helper.assertTrue(NightEvents.allowed(level, cycle, waits, at), "... and comes once it's beaten");
		} finally {
			BloodMoon.testUp = null;
			NightTable.set(saved);
		}

		helper.succeed();
	}
}
