package com.freshorangeslices.legionwarex.gametest;

import com.freshorangeslices.legionwarex.LegionWareX;
import com.freshorangeslices.legionwarex.world.RareMobGear;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Worn gear the pack keeps rare (world/RareMobGear: Epic Knights' rust series at 1 in 200, flat). Epic Knights isn't in
 * the add-on's test run, so a chainmail helmet stands in for a rusted piece, and the chance is set per test: 1 always
 * drops it and 0 never does, whatever vanilla's own chance for the slot. Kills by a player only, as in vanilla, and a
 * picked-up piece (a guaranteed drop) still drops. Each test runs in one go, so the stand-ins never meet another test.
 */
@GameTestHolder(LegionWareX.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RareMobGearGameTests {
	/** A zombie in a rusted helmet (the stand-in) at this spot, killed there; how many helmets it left. */
	private static long helmetsLeft(GameTestHelper helper, BlockPos at, float vanillaChance, boolean byPlayer) {
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, at);
		zombie.setNoAi(true);
		zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
		zombie.setDropChance(EquipmentSlot.HEAD, vanillaChance);

		if (byPlayer) {
			Player player = helper.makeMockPlayer();
			zombie.hurt(helper.getLevel().damageSources().playerAttack(player), 1000.0F);
		} else {
			zombie.hurt(helper.getLevel().damageSources().generic(), 1000.0F);
		}

		helper.assertTrue(zombie.isDeadOrDying(), "the zombie died");
		AABB around = new AABB(zombie.blockPosition()).inflate(1.5);
		long helmets = helper.getLevel().getEntitiesOfClass(ItemEntity.class, around).stream().filter(i -> i.getItem().is(Items.CHAINMAIL_HELMET)).count();
		helper.getLevel().getEntitiesOfClass(ItemEntity.class, around).forEach(ItemEntity::discard);
		return helmets;
	}

	private static void standIn(Double chance) {
		RareMobGear.testExtra = Items.CHAINMAIL_HELMET;
		RareMobGear.testChance = chance;
	}

	private static void done() {
		RareMobGear.testExtra = null;
		RareMobGear.testChance = null;
	}

	@GameTest(template = "empty")
	public static void rareGearDropsAtTheAddOnsChanceNotVanillas(GameTestHelper helper) {
		try {
			standIn(1.0);
			long always = helmetsLeft(helper, new BlockPos(1, 2, 1), 0.085F, true) + helmetsLeft(helper, new BlockPos(3, 2, 1), 0.085F, true);
			helper.assertTrue(always == 2, "at a chance of 1 both zombies should drop their helmet, " + always + " did");

			standIn(0.0);
			long never = 0;

			for (int i = 0; i < 4; i++) {
				// Vanilla's chance for the slot at 1 (a sure drop its way): the add-on's chance decides, so none.
				never += helmetsLeft(helper, new BlockPos(1 + i, 2, 3), 1.0F, true);
			}

			helper.assertTrue(never == 0, "at a chance of 0 no zombie should drop its helmet, " + never + " did");
		} finally {
			done();
		}

		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void rareGearOnlyDropsFromAPlayersKillAndPickedUpGearStillDrops(GameTestHelper helper) {
		try {
			standIn(1.0);
			long noPlayer = helmetsLeft(helper, new BlockPos(1, 2, 1), 0.085F, false);
			helper.assertTrue(noPlayer == 0, "killed with no player in it, the helmet stays on as in vanilla, " + noPlayer + " dropped");

			standIn(0.0);
			// A helmet the zombie picked up has vanilla's guaranteed chance (above 1): the add-on leaves it be.
			long pickedUp = helmetsLeft(helper, new BlockPos(3, 2, 1), 2.0F, true);
			helper.assertTrue(pickedUp == 1, "a picked-up helmet should still drop, " + pickedUp + " did");
		} finally {
			done();
		}

		helper.succeed();
	}
}
