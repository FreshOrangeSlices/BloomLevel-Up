package com.freshorangeslices.legionwarex.gametest;

import com.freshorangeslices.legionwarex.LegionWareX;
import com.freshorangeslices.legionwarex.armor.HiddenArmor;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Hidden armor (armor/HiddenArmor): a mask on the entity, one piece at a time or all; the armor itself stays on. */
@GameTestHolder(LegionWareX.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HiddenArmorGameTests {
	@GameTest(template = "empty")
	public static void armorIsHiddenPieceByPiece(GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
		zombie.setNoAi(true);
		zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		int armor = zombie.getArmorValue();
		helper.assertTrue(HiddenArmor.mask(zombie) == 0, "Nothing is hidden to begin with");

		HiddenArmor.toggle(zombie, EquipmentSlot.HEAD.getIndex());
		helper.assertTrue(HiddenArmor.mask(zombie) == 0b1000, "The helmet's bit (" + HiddenArmor.mask(zombie) + ")");
		helper.assertTrue(zombie.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET) && zombie.getArmorValue() == armor,
				"Hidden is only how it's drawn: the helmet is still worn and still protects");
		HiddenArmor.toggle(zombie, EquipmentSlot.FEET.getIndex());
		helper.assertTrue(HiddenArmor.mask(zombie) == 0b1001, "and the boots' (" + HiddenArmor.mask(zombie) + ")");

		helper.assertTrue(HiddenArmor.toggleAll(zombie) && HiddenArmor.mask(zombie) == HiddenArmor.ALL, "All of it (a companion's switch)");
		helper.assertTrue(!HiddenArmor.toggleAll(zombie) && HiddenArmor.mask(zombie) == 0, "and all shown again");
		helper.assertTrue(!zombie.getPersistentData().contains(HiddenArmor.KEY), "Nothing left on it when it all shows");
		helper.succeed();
	}
}
