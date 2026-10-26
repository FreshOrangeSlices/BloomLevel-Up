package com.freshorangeslices.bloomlevelup.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;

import com.freshorangeslices.bloomlevelup.skill.misc.MiscSkill;

/** Iron Stomach: unsafe food fills you at least like cooked chicken. */
@Mixin(FoodData.class)
abstract class FoodDataMixin {
	// Forge's eat(Item, ItemStack, LivingEntity) passes the food's numbers on to eat(int, float).
	@WrapOperation(method = "eat(Lnet/minecraft/world/item/Item;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/food/FoodData;eat(IF)V"))
	private void bloomlevelup$ironStomach(FoodData food, int nutrition, float saturationModifier, Operation<Void> original,
			@Local(argsOnly = true) ItemStack stack, @Local(argsOnly = true) LivingEntity eater) {
		MiscSkill.Food boosted = MiscSkill.ironStomachFood(eater, stack, nutrition, saturationModifier);
		original.call(food, boosted.nutrition(), boosted.saturationModifier());
	}
}
