package com.freshorangeslices.legionwarex.gametest;

import com.freshorangeslices.legionwarex.LegionWareX;
import com.freshorangeslices.legionwarex.world.DeadEnchantments;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Switched-off enchantments (FreshOrangeSlices, 2026-11-22: "switch off the dead enchantments"). The pack's list (Spell
 * Engine's and Skarrier Mobs') isn't in the add-on's test run, so Sharpness stands in, switched off for this test only:
 * the enchanting table's list never holds it (the mixin), the librarian's book trade never sells it (asked again), and
 * loot loses it (a book left with none is a plain book). Without it switched off, both still come up (so the test
 * proves something).
 */
@GameTestHolder(LegionWareX.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DeadEnchantmentGameTests {
	private static final ResourceLocation SHARPNESS = new ResourceLocation("minecraft", "sharpness");

	private static boolean sharp(ItemStack stack) {
		return EnchantmentHelper.getEnchantments(stack).containsKey(Enchantments.SHARPNESS);
	}

	@GameTest(template = "empty")
	public static void aSwitchedOffEnchantmentNeverComes(GameTestHelper helper) {
		RandomSource random = RandomSource.create(42L);
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		Entity trader = EntityType.VILLAGER.create(helper.getLevel());
		VillagerTrades.ItemListing books = DeadEnchantments.librarianBooks(trader);
		helper.assertTrue(books != null, "Vanilla's librarian book trade is found");
		boolean tableHadIt = EnchantmentHelper.getAvailableEnchantmentResults(30, sword, false).stream()
				.anyMatch(instance -> instance.enchantment == Enchantments.SHARPNESS);
		int plainTrades = 0;

		for (int i = 0; i < 400; i++) {
			MerchantOffer offer = books.getOffer(trader, random);

			if (offer != null && sharp(offer.getResult())) {
				plainTrades++;
			}
		}

		helper.assertTrue(tableHadIt && plainTrades > 0, "Switched on, Sharpness comes at the table and in trades (" + plainTrades + ")");
		DeadEnchantments.TEST_EXTRA.add(SHARPNESS);

		try {
			helper.assertFalse(EnchantmentHelper.getAvailableEnchantmentResults(30, sword, false).stream()
					.anyMatch(instance -> instance.enchantment == Enchantments.SHARPNESS), "Switched off, the table never offers Sharpness");
			helper.assertFalse(EnchantmentHelper.getAvailableEnchantmentResults(30, new ItemStack(Items.BOOK), false).stream()
					.anyMatch(instance -> instance.enchantment == Enchantments.SHARPNESS), "Nor on a book");
			DeadEnchantments.Clean cleaned = new DeadEnchantments.Clean(books);
			int cleanTrades = 0;
			int offers = 0;

			for (int i = 0; i < 400; i++) {
				MerchantOffer offer = cleaned.getOffer(trader, random);

				if (offer != null) {
					offers++;

					if (sharp(offer.getResult())) {
						cleanTrades++;
					}
				}
			}

			helper.assertTrue(cleanTrades == 0 && offers > 300, "The book trade never sells it (" + cleanTrades + " of " + offers + " offers)");

			ItemStack both = new ItemStack(Items.ENCHANTED_BOOK);
			EnchantedBookItem.addEnchantment(both, new EnchantmentInstance(Enchantments.SHARPNESS, 3));
			EnchantedBookItem.addEnchantment(both, new EnchantmentInstance(Enchantments.SMITE, 2));
			ItemStack kept = DeadEnchantments.strip(both);
			helper.assertTrue(kept.is(Items.ENCHANTED_BOOK) && !sharp(kept) && EnchantmentHelper.getEnchantments(kept).getOrDefault(Enchantments.SMITE, 0) == 2,
					"Loot: a book loses Sharpness and keeps Smite II");
			ItemStack only = new ItemStack(Items.ENCHANTED_BOOK);
			EnchantedBookItem.addEnchantment(only, new EnchantmentInstance(Enchantments.SHARPNESS, 1));
			helper.assertTrue(DeadEnchantments.strip(only).is(Items.BOOK), "A book with only Sharpness becomes a plain book");
			ItemStack blade = new ItemStack(Items.IRON_SWORD);
			blade.enchant(Enchantments.SHARPNESS, 2);
			helper.assertFalse(sharp(DeadEnchantments.strip(blade)), "A sword loses it");
			String check = DeadEnchantments.check(helper.getLevel());
			helper.assertTrue(check.contains("the table: 0 of") && check.contains("trades: 0 of"), "The check finds none: " + check);
		} finally {
			DeadEnchantments.TEST_EXTRA.remove(SHARPNESS);
		}

		helper.succeed();
	}
}
