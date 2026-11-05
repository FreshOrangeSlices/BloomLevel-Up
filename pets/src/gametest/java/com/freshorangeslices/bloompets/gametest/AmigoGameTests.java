package com.freshorangeslices.bloompets.gametest;

import com.freshorangeslices.bloompets.Amigos;
import com.freshorangeslices.bloompets.BloomPets;
import com.freshorangeslices.bloompets.PetLevels;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Migamigos companions at a Bloom pet's pace (FreshOrangeSlices, 2026-11-05): each level Bloom Pets' own count for an
 * Amigo gains gives it one Amigo level's worth of XP, which Migamigos turns into levels on its next kill; up to Amigo
 * level 75. Migamigos (and GeckoLib, Framework) are in this test run only.
 */
@GameTestHolder(BloomPets.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AmigoGameTests {
	private static final ResourceLocation CLAYMORE = new ResourceLocation("migamigos", "claymore");

	@SuppressWarnings("unchecked")
	private static Mob amigo(GameTestHelper helper, BlockPos at) {
		helper.assertTrue(ForgeRegistries.ENTITY_TYPES.containsKey(CLAYMORE), "Migamigos is in the test run");
		Mob mob = helper.spawn((EntityType<Mob>) ForgeRegistries.ENTITY_TYPES.getValue(CLAYMORE), at);
		mob.setNoAi(true);
		return mob;
	}

	@GameTest(template = "empty")
	public static void anAmigoLevelsAtABloomPetsPace(GameTestHelper helper) {
		Mob amigo = amigo(helper, new BlockPos(2, 2, 2));
		helper.assertTrue(Amigos.has(amigo) && Amigos.level(amigo) == 1 && Amigos.xp(amigo) == 0, "A new Amigo: level 1, no XP");
		int first = Amigos.xpNext(amigo, 1);
		int second = Amigos.xpNext(amigo, 2);

		PetLevels.addXp(amigo, PetLevels.xpToNext(1, true) - 1.0);
		helper.assertTrue(Amigos.xp(amigo) == 0, "Short of a Bloom pet's level: nothing yet");
		PetLevels.addXp(amigo, 1.0);
		helper.assertTrue(Amigos.xp(amigo) == first && Amigos.level(amigo) == 1,
				"A Bloom pet's level: one Amigo level's worth of XP, waiting in it for a kill (" + Amigos.xp(amigo) + ")");
		PetLevels.addXp(amigo, PetLevels.xpToNext(2, true));
		helper.assertTrue(Amigos.xp(amigo) == first + second, "Another: the next Amigo level's worth (" + Amigos.xp(amigo) + ")");
		helper.assertTrue(Amigos.owedLevel(amigo) == 3 && PetLevels.level(amigo) == 1, "Owed level 3; its level is still Migamigos' own");

		// Its next kill: Migamigos levels it twice, the zombie's own 5 XP left over.
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(5, 2, 5));
		zombie.setNoAi(true);
		zombie.hurt(amigo.damageSources().mobAttack(amigo), 1000.0F);
		helper.assertTrue(Amigos.level(amigo) == 3 && Amigos.xp(amigo) == 5,
				"Migamigos levelled it on its kill (level " + Amigos.level(amigo) + ", XP " + Amigos.xp(amigo) + ")");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void theHelpStopsAtAmigoLevel75(GameTestHelper helper) {
		Mob amigo = amigo(helper, new BlockPos(2, 2, 2));

		try {
			amigo.getClass().getMethod("setLevel", int.class).invoke(amigo, 74);
		} catch (ReflectiveOperationException e) {
			helper.fail("Migamigos' setLevel: " + e);
		}

		// Counted from its level (74), nothing owed for the past; far more than a Bloom pet needs to reach 100.
		PetLevels.addXp(amigo, 100_000.0);
		helper.assertTrue(Amigos.xp(amigo).intValue() == Amigos.xpNext(amigo, 74).intValue() && Amigos.owedLevel(amigo) == PetLevels.AMIGO_PACE_CAP,
				"One level's worth, to 75, and no more (XP " + Amigos.xp(amigo) + ", owed " + Amigos.owedLevel(amigo) + ")");
		helper.succeed();
	}
}
