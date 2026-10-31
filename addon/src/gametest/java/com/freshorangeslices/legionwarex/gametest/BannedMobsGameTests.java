package com.freshorangeslices.legionwarex.gametest;

import com.freshorangeslices.legionwarex.AddonConfig;
import com.freshorangeslices.legionwarex.LegionWareX;
import com.freshorangeslices.legionwarex.world.BannedMobs;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Banned mobs never join a world (world/BannedMobs); others do. */
@GameTestHolder(LegionWareX.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BannedMobsGameTests {
	@GameTest(template = "empty")
	public static void aBannedMobNeverJoins(GameTestHelper helper) {
		helper.assertTrue(AddonConfig.BANNED_DEFAULT.contains("born_in_chaos_v1:corpse_fly") && AddonConfig.BANNED_DEFAULT.contains("ddenizens:gazer"),
				"The default list has the flies and the floating eyes");
		AABB room = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(8.0);

		try {
			BannedMobs.overrideForTest(Set.of(EntityType.BAT));
			helper.spawn(EntityType.BAT, new BlockPos(1, 3, 1));
			helper.spawn(EntityType.CHICKEN, new BlockPos(2, 2, 2));
			helper.assertTrue(helper.getLevel().getEntitiesOfClass(Bat.class, room).isEmpty(), "The banned bat never joined");
			helper.assertFalse(helper.getLevel().getEntitiesOfClass(Chicken.class, room).isEmpty(), "The chicken did");
		} finally {
			BannedMobs.overrideForTest(null);
		}

		helper.succeed();
	}
}
