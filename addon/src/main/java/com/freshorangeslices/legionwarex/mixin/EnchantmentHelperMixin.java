package com.freshorangeslices.legionwarex.mixin;

import com.freshorangeslices.legionwarex.world.DeadEnchantments;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The add-on's one mixin: vanilla's list of enchantments to pick from at a given level (the enchanting table, books
 * included; a mob's gear; enchant_with_levels loot) never holds a switched-off one ({@link DeadEnchantments}). No event
 * covers the table in 1.20.1.
 */
@Mixin(EnchantmentHelper.class)
public abstract class EnchantmentHelperMixin {
	@Inject(method = "getAvailableEnchantmentResults", at = @At("RETURN"))
	private static void legionwarex$dropDeadEnchantments(int level, ItemStack stack, boolean allowTreasure,
			CallbackInfoReturnable<List<EnchantmentInstance>> info) {
		List<EnchantmentInstance> results = info.getReturnValue();

		if (results != null && !results.isEmpty()) {
			results.removeIf(instance -> DeadEnchantments.isDead(instance.enchantment));
		}
	}
}
