package com.freshorangeslices.legionwarex.gametest;

import com.freshorangeslices.legionwarex.LegionWareX;
import com.freshorangeslices.legionwarex.magic.MagicBridge;
import com.freshorangeslices.legionwarex.magic.Tomes;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Both magic mods' gear counting for both, and the tomes (magic/). Neither magic mod is in the add-on's test run, so
 * vanilla attributes stand in for theirs (set and put back within the test's one tick).
 */
@GameTestHolder(LegionWareX.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MagicGameTests {
	private static final ResourceLocation TARGET = new ResourceLocation("minecraft", "generic.luck");

	@GameTest(template = "empty")
	public static void eachKindOfBonusIsCopiedAsTheSameShare(GameTestHelper helper) {
		UUID id = UUID.randomUUID();
		AttributeModifier share = new AttributeModifier(id, "ten percent", 0.1, AttributeModifier.Operation.MULTIPLY_BASE);
		AttributeModifier flat = new AttributeModifier(id, "flat", 4.0, AttributeModifier.Operation.ADDITION);

		AttributeModifier school = MagicBridge.copy(share, MagicBridge.Kind.SCHOOL, true, TARGET);
		helper.assertTrue(school != null && school.getAmount() == 0.1 && school.getOperation() == AttributeModifier.Operation.MULTIPLY_BASE,
				"a school's +10% is +10% on the other side");
		helper.assertTrue(MagicBridge.copy(flat, MagicBridge.Kind.SCHOOL, true, TARGET) == null,
				"a Spell Engine staff's flat school power (its spells' damage scale) isn't copied");

		AttributeModifier points = MagicBridge.copy(new AttributeModifier(id, "points", 10.0, AttributeModifier.Operation.ADDITION),
				MagicBridge.Kind.POINTS, true, TARGET);
		helper.assertTrue(points != null && Math.abs(points.getAmount() - 0.1) < 1.0E-9, "Spell Engine's +10 Spell Power points are Iron's +10%");
		AttributeModifier back = MagicBridge.copy(new AttributeModifier(id, "share", 0.15, AttributeModifier.Operation.ADDITION),
				MagicBridge.Kind.POINTS, false, TARGET);
		helper.assertTrue(back != null && Math.abs(back.getAmount() - 15.0) < 1.0E-9, "... and Iron's +15% is 15 points");

		AttributeModifier resist = MagicBridge.copy(new AttributeModifier(id, "resist", 0.05, AttributeModifier.Operation.MULTIPLY_BASE),
				MagicBridge.Kind.RESIST, false, TARGET);
		helper.assertTrue(resist != null && Math.abs(resist.getAmount() - 1.0) < 1.0E-9 && resist.getOperation() == AttributeModifier.Operation.ADDITION,
				"Iron's +5% spell resist is 1 point of Spell Engine's resistance");
		AttributeModifier resistBack = MagicBridge.copy(new AttributeModifier(id, "points", 2.0, AttributeModifier.Operation.ADDITION),
				MagicBridge.Kind.RESIST, true, TARGET);
		helper.assertTrue(resistBack != null && Math.abs(resistBack.getAmount() - 0.1) < 1.0E-9, "... and 2 points are +10%");

		AttributeModifier again = MagicBridge.copy(share, MagicBridge.Kind.SCHOOL, true, TARGET);
		helper.assertTrue(again.getId().equals(school.getId()), "a copy's id is fixed (the same modifier each time, never stacking)");
		helper.assertTrue(MagicBridge.line(Attributes.ATTACK_DAMAGE, share).contains("10%"), "its tooltip line reads +10%: " + MagicBridge.line(Attributes.ATTACK_DAMAGE, share));
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void anItemsBonusCountsForTheOtherSideToo(GameTestHelper helper) {
		MagicBridge.testPairs = List.of(new MagicBridge.Pair("minecraft:generic.attack_knockback", "minecraft:generic.luck", MagicBridge.Kind.SCHOOL));

		try {
			ItemStack ring = new ItemStack(Items.GOLD_INGOT);
			ring.addAttributeModifier(Attributes.ATTACK_KNOCKBACK, new AttributeModifier(UUID.randomUUID(), "fire", 0.25, AttributeModifier.Operation.MULTIPLY_BASE),
					EquipmentSlot.MAINHAND);
			var modifiers = ring.getAttributeModifiers(EquipmentSlot.MAINHAND);
			helper.assertTrue(modifiers.get(Attributes.LUCK).stream().anyMatch(m -> m.getName().equals(MagicBridge.NAME) && m.getAmount() == 0.25),
					"the item's +25% also shows on the other side: " + modifiers);
			helper.assertTrue(modifiers.get(Attributes.LUCK).size() == 1, "once, not copied back and forth");
		} finally {
			MagicBridge.testPairs = null;
		}

		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void onlyTheBestTomeCountsInEitherHand(GameTestHelper helper) {
		Tomes.testPower = Map.of("minecraft:book", 0.3, "minecraft:enchanted_book", 0.5);
		Tomes.testAttributes = List.of("minecraft:generic.luck");

		try {
			Player player = helper.makeMockPlayer();
			helper.assertTrue(Tomes.best(player) == 0.0, "no tome, no bonus");
			player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.BOOK));
			helper.assertTrue(Tomes.best(player) == 0.3, "a tome in the off hand counts");
			Tomes.apply(player, Tomes.best(player));
			AttributeModifier set = player.getAttribute(Attributes.LUCK).getModifier(Tomes.ID);
			helper.assertTrue(set != null && set.getAmount() == 0.3 && set.getOperation() == AttributeModifier.Operation.MULTIPLY_BASE,
					"+30% Spell Power, added to the other bonuses (multiplying the base)");
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.ENCHANTED_BOOK));
			helper.assertTrue(Tomes.best(player) == 0.5, "two tomes: only the best counts");
			Tomes.apply(player, Tomes.best(player));
			helper.assertTrue(player.getAttribute(Attributes.LUCK).getModifier(Tomes.ID).getAmount() == 0.5, "... and the modifier follows it");
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
			Tomes.apply(player, Tomes.best(player));
			helper.assertTrue(player.getAttribute(Attributes.LUCK).getModifier(Tomes.ID) == null, "put away, it's gone");
		} finally {
			Tomes.testPower = null;
			Tomes.testAttributes = null;
		}

		helper.succeed();
	}
}
