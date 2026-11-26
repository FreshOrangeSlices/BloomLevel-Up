package com.freshorangeslices.legionwarex.gametest;

import com.freshorangeslices.legionwarex.LegionWareX;
import com.freshorangeslices.legionwarex.world.FarFromSpawn;
import com.freshorangeslices.legionwarex.world.FarHealth;
import com.freshorangeslices.legionwarex.world.FarWeapons;
import java.util.Set;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Distance from spawn: monsters' health (FarHealth) and found weapons' damage (FarWeapons), at the config's defaults. */
@GameTestHolder(LegionWareX.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FarGameTests {
	private static boolean near(double a, double b) {
		return Math.abs(a - b) < 1.0e-6;
	}

	private static double damage(ItemStack stack) {
		double sum = 0.0;

		for (AttributeModifier modifier : stack.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE)) {
			if (modifier.getOperation() == AttributeModifier.Operation.ADDITION) {
				sum += modifier.getAmount();
			}
		}

		return sum + 1.0;
	}

	@GameTest(template = "empty")
	public static void healthGrowsFromAThousandToSixThousandBlocks(GameTestHelper helper) {
		helper.assertTrue(near(FarFromSpawn.steps(500), 0.0) && near(FarFromSpawn.steps(2500), 1.5) && near(FarFromSpawn.steps(60000), 5.0), "steps past 1000, to 5");
		helper.assertTrue(near(FarHealth.multiplier(800, false), 1.0), "normal within 1000 blocks");
		helper.assertTrue(near(FarHealth.multiplier(3500, false), 2.25), "+50% a step: x2.25 at 3500");
		helper.assertTrue(near(FarHealth.multiplier(6000, false), 3.5) && near(FarHealth.multiplier(20000, false), 3.5), "x3.5 at 6000 and beyond");
		helper.assertTrue(near(FarHealth.multiplier(6000, true), 2.25), "a boss gets half: x2.25 at 6000");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aFarMonsterIsTougherOnce(GameTestHelper helper) {
		Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1, 2, 1);
		float before = zombie.getMaxHealth();
		helper.assertTrue(FarHealth.apply(zombie, 3500), "a zombie 3500 blocks out gets its health");
		helper.assertTrue(Math.abs(zombie.getMaxHealth() - before * 2.25F) < 0.01F, "x2.25: " + zombie.getMaxHealth());
		helper.assertTrue(Math.abs(zombie.getHealth() - zombie.getMaxHealth()) < 0.01F, "... and starts at full");
		helper.assertTrue(!FarHealth.apply(zombie, 6000), "only once");
		helper.assertTrue(Math.abs(zombie.getMaxHealth() - before * 2.25F) < 0.01F, "still x2.25");
		zombie.discard();
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aFoundWeaponHitsHarderByWhereItWasFound(GameTestHelper helper) {
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		double plain = damage(sword);
		helper.assertTrue(FarWeapons.mark(sword, 3500), "an iron sword from a chest 3500 blocks out is marked");
		helper.assertTrue(near(damage(sword), plain * 1.25), "+25% of its own damage: " + plain + " -> " + damage(sword));
		helper.assertTrue(!FarWeapons.mark(sword, 6000), "marked once, where it was first found");
		ItemStack wooden = new ItemStack(Items.WOODEN_SWORD);
		helper.assertTrue(!FarWeapons.mark(wooden, 6000) && near(damage(wooden), damage(new ItemStack(Items.WOODEN_SWORD))), "never a wooden one");
		ItemStack golden = new ItemStack(Items.GOLDEN_SWORD);
		helper.assertTrue(!FarWeapons.mark(golden, 6000), "nor a golden one");
		helper.assertTrue(!FarWeapons.mark(new ItemStack(Items.STICK), 6000), "nor something that isn't a weapon");
		ItemStack crafted = new ItemStack(Items.DIAMOND_SWORD);
		helper.assertTrue(near(damage(crafted), 7.0), "a crafted (unmarked) one is as it was");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void legendaryWeaponsGrowByTheFarthestBossKill(GameTestHelper helper) {
		FarWeapons.testLegendary = Set.of(Items.TRIDENT);

		try {
			ItemStack trident = new ItemStack(Items.TRIDENT);
			double plain = damage(trident);
			helper.assertTrue(FarWeapons.kind(trident).legendary(), "the stand-in counts as legendary");
			helper.assertTrue(!FarWeapons.mark(trident, 6000), "a legendary weapon isn't marked where it's found");
			trident.getOrCreateTag().putInt(FarWeapons.BOSS, 6000);
			helper.assertTrue(near(damage(trident), plain * 1.5), "its holder's farthest boss kill 6000 blocks out: +50%");
		} finally {
			FarWeapons.testLegendary = null;
		}

		helper.succeed();
	}
}
