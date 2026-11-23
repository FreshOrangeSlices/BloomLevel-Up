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
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Pillager;
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
		return new NightTable.Theme(new ResourceLocation(LegionWareX.MOD_ID, "test_" + name), "Test " + name, "", "", ChatFormatting.RED, health,
				NightTable.Climate.ANY, List.of(mobs));
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
					new NightTable.Theme(new ResourceLocation(LegionWareX.MOD_ID, "test_missing"), "Missing", "", "", ChatFormatting.RED, 1.5, NightTable.Climate.ANY,
							List.of(new NightTable.Mob(new ResourceLocation("nosuchmod", "ghost"), 1, NightTable.Climate.ANY, 0, null)))));
			NightCycle other = NightCycle.fresh();

			for (int i = 0; i < 5; i++) {
				helper.assertTrue(other.deal(helper.getLevel().getRandom()).id().getPath().equals("test_a"), "only the installed night is dealt");
			}

			// A night with two copies comes twice a deck, never back to back.
			NightTable.Theme twice = new NightTable.Theme(new ResourceLocation(LegionWareX.MOD_ID, "test_twice"), "Twice", "", "", ChatFormatting.GREEN, 2.0,
					NightTable.Climate.ANY, -1.0, true, 2, List.of(mob(EntityType.ZOMBIE, 1)));
			NightTable.set(List.of(twice, theme("once", 1.5, mob(EntityType.SKELETON, 1))));
			NightCycle copies = NightCycle.fresh();
			ResourceLocation previous = null;
			int twiceCount = 0;

			for (int i = 0; i < 30; i++) {
				ResourceLocation next = copies.deal(helper.getLevel().getRandom()).id();
				helper.assertFalse(next.equals(previous), "a night twice running (deal " + i + "): " + next);
				twiceCount += next.getPath().equals("test_twice") ? 1 : 0;
				previous = next;
			}

			helper.assertTrue(twiceCount == 20, "the two-copy night should be 20 of 30 deals, was " + twiceCount);

			// A night of any climate is full strength everywhere; a cold one a fifth in temperate land, a twenty-fifth in the heat.
			NightTable.Theme any = theme("any", 1.5, mob(EntityType.SKELETON, 1), new NightTable.Mob(EntityType.getKey(EntityType.STRAY), 1,
					NightTable.Climate.COLD, 0, null));
			NightTable.Theme cold = new NightTable.Theme(new ResourceLocation(LegionWareX.MOD_ID, "test_cold"), "Cold", "", "", ChatFormatting.AQUA, 1.5,
					NightTable.Climate.COLD, List.of(new NightTable.Mob(EntityType.getKey(EntityType.STRAY), 1, NightTable.Climate.COLD, 0, null)));
			double off = AddonConfig.themedNightOffClimateShare();
			helper.assertTrue(any.strength(NightTable.Climate.HOT) == 1.0 && any.strength(NightTable.Climate.ANY) == 1.0, "a night of any climate is full everywhere");
			helper.assertTrue(cold.strength(NightTable.Climate.COLD) == 1.0, "a cold night is full in the cold");
			helper.assertTrue(Math.abs(cold.strength(NightTable.Climate.ANY) - off) < 1e-9, "... less in temperate land");
			helper.assertTrue(Math.abs(cold.strength(NightTable.Climate.HOT) - off * off) < 1e-9, "... far less in the heat");
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
			NightEvents.testMore = 0.0;

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

			// A night's own share wins over the config's: The Old Night turns every other monster, whatever the config says.
			NightEvents.testTheme = new NightTable.Theme(new ResourceLocation(LegionWareX.MOD_ID, "test_old"), "Old", "", "", ChatFormatting.GREEN, 2.0,
					NightTable.Climate.ANY, 1.0, false, 2, List.of(mob(EntityType.SKELETON, 1)));
			Zombie turned = EntityType.ZOMBIE.create(level);
			turned.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
			ForgeEventFactory.onFinalizeSpawn(turned, level, level.getCurrentDifficultyAt(at), MobSpawnType.NATURAL, null, null);
			helper.assertTrue(turned.isSpawnCancelled(), "with the night's own share of 1 the zombie is turned (config share 0)");
			turned.discard();
			level.getEntitiesOfClass(Skeleton.class, new AABB(at).inflate(1.0), NightMobs::isNight).forEach(Skeleton::discard);

			// ... and holds vanilla's patrols (and the add-on's pirate bands) off.
			Pillager pillager = EntityType.PILLAGER.create(level);
			pillager.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
			ForgeEventFactory.onFinalizeSpawn(pillager, level, level.getCurrentDifficultyAt(at), MobSpawnType.PATROL, null, null);
			helper.assertTrue(pillager.isSpawnCancelled(), "a patrol's pillager is turned away on a night without patrols");
			helper.assertTrue(NightEvents.holdsBandsOff(level), "... and pirate bands wait");
			pillager.discard();
			NightEvents.testTheme = theme("bones", 2.0, mob(EntityType.SKELETON, 1));
			helper.assertFalse(NightEvents.holdsBandsOff(level), "other nights let patrols and pirates come");

			// One more beside each: an extra, persistent so the monster cap doesn't count it.
			NightEvents.testShare = 1.0;
			NightEvents.testMore = 1.0;

			for (int x = 0; x < 8; x++) {
				for (int z = 0; z < 8; z++) {
					helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
				}
			}

			BlockPos middle = helper.absolutePos(new BlockPos(4, 2, 4));
			Zombie twin = EntityType.ZOMBIE.create(level);
			twin.moveTo(middle.getX() + 0.5, middle.getY(), middle.getZ() + 0.5);
			ForgeEventFactory.onFinalizeSpawn(twin, level, level.getCurrentDifficultyAt(middle), MobSpawnType.NATURAL, null, null);
			twin.discard();
			List<Skeleton> pair = level.getEntitiesOfClass(Skeleton.class, new AABB(middle).inflate(5.0), NightMobs::isNight);
			helper.assertTrue(pair.size() == 2, "the zombie's place and one more: 2 night skeletons, found " + pair.size());
			long extras = pair.stream().filter(NightMobs::isExtra).count();
			helper.assertTrue(extras == 1, "one of them the extra, found " + extras);
			helper.assertTrue(pair.stream().filter(NightMobs::isExtra).allMatch(Skeleton::isPersistenceRequired), "the extra is persistent (outside the cap)");
			helper.assertTrue(pair.stream().allMatch(each -> Math.abs(each.getMaxHealth() - 40.0F) < 0.01F), "both with the night's health");
			pair.forEach(Skeleton::discard);
		} finally {
			NightEvents.testTheme = null;
			NightEvents.testShare = null;
			NightEvents.testMore = null;
		}

		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void leftoversGoOnceTheirNightIsOverAndNoOneIsNear(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		NightCycle over = NightCycle.fresh();
		NightTable.Theme theme = theme("leftover", 1.5, mob(EntityType.SKELETON, 1));
		// Made here, not with the helper's spawn (which makes a mob persistent, as a name tag would).
		Skeleton marked = EntityType.SKELETON.create(level);
		NightMobs.mark(marked, theme, 3);
		helper.assertTrue(NightMobs.leaves(marked, over, false), "a mob of an ended night with no one near goes");
		helper.assertFalse(NightMobs.leaves(marked, over, true), "... not while a player is near");
		marked.setPersistenceRequired();
		helper.assertFalse(NightMobs.leaves(marked, over, false), "... nor once a player has kept it (a name tag)");
		Skeleton plain = EntityType.SKELETON.create(level);
		helper.assertFalse(NightMobs.leaves(plain, over, false), "a skeleton of no night stays");
		// An extra is persistent (outside the monster cap) but not kept: it still goes, and despawns as vanilla's monsters do.
		Skeleton extra = EntityType.SKELETON.create(level);
		NightMobs.mark(extra, theme, 3);
		NightMobs.markExtra(extra);
		helper.assertTrue(extra.isPersistenceRequired() && NightMobs.leaves(extra, over, false), "an extra of an ended night goes");
		helper.assertTrue(NightMobs.despawns(extra, 200.0, level.getRandom()), "an extra with no player within 128 blocks despawns at once");
		helper.assertFalse(NightMobs.despawns(extra, 10.0, level.getRandom()), "... never with a player within 32");
		int gone = 0;

		for (int i = 0; i < 800; i++) {
			gone += NightMobs.despawns(extra, 60.0, level.getRandom()) ? 1 : 0;
		}

		helper.assertTrue(gone > 50 && gone < 160, "beyond 32 blocks about 1 in 8 checks, was " + gone + " of 800");
		helper.assertFalse(NightMobs.despawns(marked, 200.0, level.getRandom()), "a mob that isn't an extra despawns by vanilla's rule, not this");
		extra.setCustomName(Component.literal("Bones"));
		helper.assertFalse(NightMobs.leaves(extra, over, false) || NightMobs.despawns(extra, 200.0, level.getRandom()), "a name-tagged extra stays");
		extra.discard();
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
