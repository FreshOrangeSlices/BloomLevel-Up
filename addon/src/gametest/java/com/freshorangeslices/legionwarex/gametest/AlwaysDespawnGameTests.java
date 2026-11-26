package com.freshorangeslices.legionwarex.gametest;

import com.freshorangeslices.legionwarex.Despawning;
import com.freshorangeslices.legionwarex.LegionWareX;
import com.freshorangeslices.legionwarex.world.AlwaysDespawn;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Monsters their own mod keeps for good despawn as vanilla's (world/AlwaysDespawn): a skeleton made persistent stands
 * in for The Damned's Scarlet Persecutor, Infernal Spirit and Flamebreather (not in the add-on's test run).
 */
@GameTestHolder(LegionWareX.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AlwaysDespawnGameTests {
	@GameTest(template = "empty")
	public static void aMobItsModKeepsDespawnsUnlessAPlayerKeptIt(GameTestHelper helper) {
		AlwaysDespawn.testMobs = List.of("minecraft:skeleton");

		try {
			Skeleton kept = EntityType.SKELETON.create(helper.getLevel());
			kept.setPersistenceRequired();
			helper.assertTrue(AlwaysDespawn.applies(kept), "a skeleton on the list, persistent by its mod, despawns as vanilla's");
			helper.assertTrue(Despawning.despawns(200.0 * 200.0, 0, kept.getRandom()), "... at once with no player within 128 blocks");
			kept.setCustomName(Component.literal("Rattles"));
			helper.assertFalse(AlwaysDespawn.applies(kept), "a name-tagged one stays");
			kept.setCustomName(null);
			helper.assertFalse(Despawning.pickedUpGear(kept), "it picked nothing up");
			kept.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
			kept.setGuaranteedDrop(EquipmentSlot.MAINHAND);
			helper.assertTrue(Despawning.pickedUpGear(kept), "holding a player's sword it picked up, it's kept (vanilla's rule)");
			AlwaysDespawn.testMobs = List.of("minecraft:stray");
			helper.assertFalse(AlwaysDespawn.applies(kept), "a mob off the list is left to its own mod");
			kept.discard();
		} finally {
			AlwaysDespawn.testMobs = null;
		}

		helper.succeed();
	}
}
