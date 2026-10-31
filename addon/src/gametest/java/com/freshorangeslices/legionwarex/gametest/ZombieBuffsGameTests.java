package com.freshorangeslices.legionwarex.gametest;

import com.freshorangeslices.legionwarex.LegionWareX;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Zombies get no endless buffs (world/ZombieBuffs); short ones, harmful ones and other mobs' are left alone. */
@GameTestHolder(LegionWareX.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ZombieBuffsGameTests {
	@GameTest(template = "empty")
	public static void zombiesGetNoEndlessBuffs(GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
		zombie.setNoAi(true);
		zombie.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, Integer.MAX_VALUE));
		helper.assertFalse(zombie.hasEffect(MobEffects.DAMAGE_BOOST), "Strength for 2^31 ticks is refused");
		zombie.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, MobEffectInstance.INFINITE_DURATION));
		helper.assertFalse(zombie.hasEffect(MobEffects.INVISIBILITY), "Endless invisibility is refused");
		zombie.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200));
		helper.assertTrue(zombie.hasEffect(MobEffects.DAMAGE_BOOST), "A short strength (a witch's potion, a spell) is kept");
		zombie.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, Integer.MAX_VALUE));
		helper.assertTrue(zombie.hasEffect(MobEffects.WEAKNESS), "A harmful effect is left alone, however long");

		Skeleton skeleton = helper.spawn(EntityType.SKELETON, new BlockPos(3, 2, 3));
		skeleton.setNoAi(true);
		skeleton.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, Integer.MAX_VALUE));
		helper.assertTrue(skeleton.hasEffect(MobEffects.DAMAGE_BOOST), "Other mobs keep theirs");
		helper.succeed();
	}
}
