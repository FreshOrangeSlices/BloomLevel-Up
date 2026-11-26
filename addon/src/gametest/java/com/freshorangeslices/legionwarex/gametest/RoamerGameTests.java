package com.freshorangeslices.legionwarex.gametest;

import com.freshorangeslices.legionwarex.LegionWareX;
import com.freshorangeslices.legionwarex.roam.RoamerClock;
import com.freshorangeslices.legionwarex.roam.Roamers;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.PolarBear;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Roaming bosses (roam/). Stand-ins for the pack's bosses (not in the add-on's test run): a polar bear for the wait
 * (nothing else in the add-on touches a polar bear's natural spawn), an iron golem for drops (it always drops iron), a
 * zombie for XP. The lists are set and put back within one tick, so the tests beside these never see them.
 */
@GameTestHolder(LegionWareX.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RoamerGameTests {
	private static final String BEAR = "minecraft:polar_bear";

	@GameTest(template = "empty")
	public static void aRoamingBossWaitsItsDaysAndItsLairTwinDoesNot(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RoamerClock clock = RoamerClock.of(level.getServer());
		clock.forget(BEAR);
		Roamers.testRoaming = List.of(BEAR);
		Roamers.testQuiet = List.of(BEAR);

		try {
			BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
			PolarBear first = EntityType.POLAR_BEAR.create(level);
			first.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
			ForgeEventFactory.onFinalizeSpawn(first, level, level.getCurrentDifficultyAt(at), MobSpawnType.NATURAL, null, null);
			helper.assertFalse(first.isSpawnCancelled(), "the first roaming boss may come");
			helper.assertTrue(Roamers.isQuiet(first), "... and is a quiet one");
			level.addFreshEntity(first);
			helper.assertTrue(clock.last(BEAR) == level.getGameTime(), "its coming starts the clock");

			PolarBear second = EntityType.POLAR_BEAR.create(level);
			second.moveTo(at.getX() + 3.5, at.getY(), at.getZ() + 0.5);
			ForgeEventFactory.onFinalizeSpawn(second, level, level.getCurrentDifficultyAt(at), MobSpawnType.NATURAL, null, null);
			helper.assertTrue(second.isSpawnCancelled(), "the next waits its days");

			PolarBear lair = EntityType.POLAR_BEAR.create(level);
			lair.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 3.5);
			ForgeEventFactory.onFinalizeSpawn(lair, level, level.getCurrentDifficultyAt(at), MobSpawnType.STRUCTURE, null, null);
			helper.assertFalse(lair.isSpawnCancelled() || Roamers.isQuiet(lair), "one placed by its structure (a lair) is untouched");

			helper.assertTrue(Roamers.mayCome(-1L, 10L, 72000L), "never came: may come");
			helper.assertFalse(Roamers.mayCome(1000L, 1000L + 71999L, 72000L), "a tick short of 3 days: waits");
			helper.assertTrue(Roamers.mayCome(1000L, 1000L + 72000L, 72000L), "3 days on: may come");
			first.discard();
			second.discard();
			lair.discard();
		} finally {
			Roamers.testRoaming = null;
			Roamers.testQuiet = null;
			clock.forget(BEAR);
		}

		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aQuietRoamerDropsNothing(GameTestHelper helper) {
		IronGolem golem = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(2, 2, 2));
		golem.setNoAi(true);
		Roamers.makeQuiet(golem);
		helper.assertTrue(golem.getPersistentData().getDouble(Roamers.BLOOM_XP) == 2.0, "Bloom's Combat XP multiplier is set on it (2)");
		Player player = helper.makeMockPlayer();
		golem.hurt(helper.getLevel().damageSources().playerAttack(player), 1000.0F);
		helper.assertTrue(golem.isDeadOrDying(), "the player killed it");
		List<ItemEntity> items = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(golem.blockPosition()).inflate(3.0));
		helper.assertTrue(items.isEmpty(), "a roamer drops nothing (an iron golem always drops iron): " + items.stream().map(i -> i.getItem().toString()).toList());
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aQuietRoamerPaysDoubleXp(GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
		zombie.setNoAi(true);
		zombie.setBaby(false);
		Roamers.makeQuiet(zombie);
		helper.assertTrue(Roamers.xp(5) == 10, "5 XP becomes 10");
		Player player = helper.makeMockPlayer();
		zombie.hurt(helper.getLevel().damageSources().playerAttack(player), 1000.0F);
		helper.runAfterDelay(40, () -> {
			int xp = helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, new AABB(zombie.blockPosition()).inflate(4.0)).stream()
					.mapToInt(ExperienceOrb::getValue).sum();
			helper.assertTrue(xp == 10, "a zombie's 5 XP, doubled, was " + xp);
			helper.succeed();
		});
	}

	@GameTest(template = "empty")
	public static void aQuietRoamerLeavesPlayersAloneUntilHitOrCloseBy(GameTestHelper helper) {
		Zombie roamer = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
		roamer.setNoAi(true);
		Roamers.makeQuiet(roamer);
		Vec3 at = roamer.position();
		Player far = helper.makeMockPlayer();
		far.setPos(at.x + 20.0, at.y, at.z);
		roamer.setTarget(far);
		helper.assertTrue(roamer.getTarget() == null, "a player 20 blocks off who didn't hit it isn't taken as a target");

		roamer.hurt(helper.getLevel().damageSources().playerAttack(far), 1.0F);
		roamer.setTarget(far);
		helper.assertTrue(roamer.getTarget() == far, "once hit by that player, it may go for them");

		Roamers.calm(roamer);
		roamer.setTarget(null);
		roamer.setTarget(far);
		helper.assertTrue(roamer.getTarget() == null, "calmed down, it leaves them alone again");

		Player close = helper.makeMockPlayer();
		close.setPos(at.x + 5.0, at.y, at.z);
		roamer.setTarget(close);
		helper.assertTrue(roamer.getTarget() == close, "a player within 12 blocks is noticed");
		helper.assertTrue(Roamers.provokedBy(roamer, close), "... and stays its target past 12 blocks (it's angry at them now)");

		Zombie plain = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 2, 1));
		plain.setNoAi(true);
		plain.setTarget(far);
		helper.assertTrue(plain.getTarget() == far, "a zombie that isn't a roamer targets as usual");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void whatRisesWhereAQuietRoamerFellIsQuietToo(GameTestHelper helper) {
		Vec3 fell = new Vec3(100.0, 64.0, 100.0);
		helper.assertTrue(Roamers.inherits("soulsweapons:night_shade", fell, 1000L, "soulsweapons:night_shade", fell.add(5.0, 0.0, 5.0), 1100L),
				"the Frenzied Shade rising beside a roaming Champion's fall is its heir");
		helper.assertFalse(Roamers.inherits("soulsweapons:night_shade", fell, 1000L, "soulsweapons:night_shade", fell.add(40.0, 0.0, 0.0), 1100L),
				"... not 40 blocks off");
		helper.assertFalse(Roamers.inherits("soulsweapons:night_shade", fell, 1000L, "soulsweapons:night_shade", fell, 1000L + 601L),
				"... nor more than 30 seconds later");
		helper.assertFalse(Roamers.inherits("soulsweapons:night_shade", fell, 1000L, "minecraft:zombie", fell, 1001L), "... and only the heir's kind");
		helper.succeed();
	}
}
