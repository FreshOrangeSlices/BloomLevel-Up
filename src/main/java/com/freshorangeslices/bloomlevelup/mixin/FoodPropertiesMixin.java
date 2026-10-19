package com.freshorangeslices.bloomlevelup.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

import com.freshorangeslices.bloomlevelup.skill.misc.MiscSkill;

/** Iron Stomach: unsafe food fills you at least as much as cooked chicken. */
@Mixin(FoodProperties.class)
abstract class FoodPropertiesMixin {
	@WrapOperation(method = "onConsume", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/food/FoodData;eat(Lnet/minecraft/world/food/FoodProperties;)V"))
	private void bloomlevelup$ironStomach(FoodData food, FoodProperties properties, Operation<Void> original,
			@Local(argsOnly = true) LivingEntity eater, @Local(argsOnly = true) ItemStack stack) {
		original.call(food, MiscSkill.ironStomachFood(eater, stack, properties));
	}
}
