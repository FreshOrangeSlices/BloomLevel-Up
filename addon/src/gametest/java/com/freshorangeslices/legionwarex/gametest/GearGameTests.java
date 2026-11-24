package com.freshorangeslices.legionwarex.gametest;

import com.freshorangeslices.legionwarex.LegionWareX;
import com.freshorangeslices.legionwarex.world.BossDrops;
import com.freshorangeslices.legionwarex.world.GearTuning;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Weapon damage the pack sets (world/GearTuning) and weapons bosses drop (world/BossDrops), with vanilla stand-ins: an iron
 * sword (6 damage) set to 10, and a zombie that always drops a diamond sword. Each test puts the config's tables back.
 */
@GameTestHolder(LegionWareX.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GearGameTests {
	private static double damage(ItemStack stack) {
		double sum = 1.0;

		for (AttributeModifier modifier : stack.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE)) {
			if (modifier.getOperation() == AttributeModifier.Operation.ADDITION) {
				sum += modifier.getAmount();
			}
		}

		return sum;
	}

	private static double speed(ItemStack stack) {
		return 4.0 + stack.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_SPEED).stream().mapToDouble(AttributeModifier::getAmount).sum();
	}

	@GameTest(template = "empty")
	public static void aSetWeaponShowsItsDamageAndKeepsItsSpeed(GameTestHelper helper) {
		try {
			ItemStack sword = new ItemStack(Items.IRON_SWORD);
			helper.assertTrue(Math.abs(damage(sword) - 6.0) < 0.001, "an iron sword does 6 before, did " + damage(sword));
			double speedBefore = speed(sword);
			GearTuning.testTable = Map.of(new ResourceLocation("minecraft", "iron_sword"), 10.0);
			helper.assertTrue(Math.abs(damage(sword) - 10.0) < 0.001, "set to 10, it should do 10, did " + damage(sword));
			helper.assertTrue(Math.abs(speed(sword) - speedBefore) < 0.001, "its speed stays " + speedBefore + ", was " + speed(sword));
			UUID id = sword.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE).iterator().next().getId();
			helper.assertTrue(id.equals(UUID.fromString("CB3F55D3-645C-4F38-A497-9C13A33DB5CF")), "its modifier keeps the item's own id (the tooltip's base damage)");
			helper.assertTrue(Math.abs(damage(new ItemStack(Items.GOLDEN_SWORD)) - 4.0) < 0.001, "a weapon not in the table is left alone");
			helper.assertTrue(Math.abs(damage(sword.copy()) - 10.0) < 0.001 && speed(new ItemStack(Items.IRON_SWORD, 1)) == speedBefore, "every iron sword the same");
		} finally {
			GearTuning.testTable = null;
		}

		helper.succeed();
	}

	/** A zombie killed at this spot, by a player or not; whether it left a diamond sword. */
	private static boolean leftASword(GameTestHelper helper, BlockPos at, boolean byPlayer) {
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, at);
		zombie.setNoAi(true);

		if (byPlayer) {
			Player player = helper.makeMockPlayer();
			zombie.hurt(helper.getLevel().damageSources().playerAttack(player), 1000.0F);
		} else {
			zombie.hurt(helper.getLevel().damageSources().generic(), 1000.0F);
		}

		helper.assertTrue(zombie.isDeadOrDying(), "the zombie died");
		AABB around = new AABB(zombie.blockPosition()).inflate(2.0);
		boolean sword = helper.getLevel().getEntitiesOfClass(ItemEntity.class, around).stream().anyMatch(i -> i.getItem().is(Items.DIAMOND_SWORD));
		helper.getLevel().getEntitiesOfClass(ItemEntity.class, around).forEach(ItemEntity::discard);
		return sword;
	}

	@GameTest(template = "empty")
	public static void aBossAlwaysDropsItsWeaponToAPlayersKill(GameTestHelper helper) {
		try {
			BossDrops.testTable = Map.of(new ResourceLocation("minecraft", "zombie"), new BossDrops.Drop(new ResourceLocation("minecraft", "diamond_sword"), 1.0));

			for (int i = 0; i < 4; i++) {
				helper.assertTrue(leftASword(helper, new BlockPos(1 + i, 2, 1), true), "a player's kill always drops it (try " + i + ")");
			}

			helper.assertFalse(leftASword(helper, new BlockPos(1, 2, 4), false), "a kill with no player in it drops nothing");
			BossDrops.testTable = Map.of(new ResourceLocation("minecraft", "zombie"), new BossDrops.Drop(new ResourceLocation("minecraft", "diamond_sword"), 0.0));
			helper.assertFalse(leftASword(helper, new BlockPos(3, 2, 4), true), "at a chance of 0, never");
			BossDrops.testTable = Map.of(new ResourceLocation("minecraft", "skeleton"), new BossDrops.Drop(new ResourceLocation("minecraft", "diamond_sword"), 1.0));
			helper.assertFalse(leftASword(helper, new BlockPos(5, 2, 4), true), "another mob's entry doesn't touch the zombie");
		} finally {
			BossDrops.testTable = null;
		}

		helper.succeed();
	}
}
