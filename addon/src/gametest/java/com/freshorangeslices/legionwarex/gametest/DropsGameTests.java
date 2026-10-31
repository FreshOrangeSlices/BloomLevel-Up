package com.freshorangeslices.legionwarex.gametest;

import com.freshorangeslices.legionwarex.LegionWareX;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Mobs killed by other mobs drop nothing unless a player took part (world/MobKillDrops). A chicken always drops raw chicken. */
@GameTestHolder(LegionWareX.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DropsGameTests {
	private static Chicken chicken(GameTestHelper helper, BlockPos at) {
		Chicken chicken = helper.spawn(EntityType.CHICKEN, at);
		chicken.setNoAi(true);
		return chicken;
	}

	private static List<ItemEntity> drops(GameTestHelper helper, Chicken chicken) {
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(chicken.blockPosition()).inflate(3.0));
	}

	@GameTest(template = "empty")
	public static void aMobAGolemKillsDropsNothing(GameTestHelper helper) {
		Chicken chicken = chicken(helper, new BlockPos(1, 2, 1));
		IronGolem golem = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(4, 2, 4));
		golem.setNoAi(true);
		chicken.hurt(helper.getLevel().damageSources().mobAttack(golem), 100.0F);
		helper.assertTrue(chicken.isDeadOrDying(), "The golem killed the chicken");
		List<ItemEntity> items = drops(helper, chicken);
		helper.assertTrue(items.isEmpty(), "Killed by a golem, it drops nothing: " + items.stream().map(i -> i.getItem().toString()).toList());
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 200)
	public static void aMobAPlayerHitDropsWhenAGolemFinishesIt(GameTestHelper helper) {
		Chicken chicken = chicken(helper, new BlockPos(1, 2, 1));
		IronGolem golem = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(4, 2, 4));
		golem.setNoAi(true);
		Player player = helper.makeMockPlayer();
		chicken.hurt(helper.getLevel().damageSources().playerAttack(player), 0.5F);
		// Past vanilla's 5 seconds of "recently hit by a player": what keeps the drops now is the add-on's mark.
		helper.runAfterDelay(110, () -> {
			chicken.hurt(helper.getLevel().damageSources().mobAttack(golem), 100.0F);
			helper.assertTrue(chicken.isDeadOrDying(), "The golem finished the chicken");
			helper.assertTrue(drops(helper, chicken).stream().anyMatch(i -> i.getItem().is(Items.CHICKEN)),
					"A player took part in the fight, so it drops its meat");
			helper.succeed();
		});
	}

	@GameTest(template = "empty")
	public static void aMobThatFallsDropsAsUsual(GameTestHelper helper) {
		Chicken chicken = chicken(helper, new BlockPos(1, 2, 1));
		chicken.hurt(helper.getLevel().damageSources().fall(), 100.0F);
		helper.assertTrue(chicken.isDeadOrDying(), "The fall killed the chicken");
		helper.assertTrue(drops(helper, chicken).stream().anyMatch(i -> i.getItem().is(Items.CHICKEN)), "No killer, so it drops as usual");
		helper.succeed();
	}
}
