package com.freshorangeslices.bloomlevelup.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;

import com.freshorangeslices.bloomlevelup.skill.combat.CombatSkill;

/** Unrestricted Riptide: sneak to riptide with any trident, with no water or rain needed. Both sides. */
@Mixin(TridentItem.class)
abstract class TridentItemMixin {
	@WrapOperation(method = {"use", "releaseUsing"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isInWaterOrRain()Z"))
	private boolean bloomlevelup$riptideAnywhere(Player player, Operation<Boolean> original) {
		return original.call(player) || CombatSkill.riptideAnywhere(player);
	}

	@WrapOperation(method = "use", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;getRiptide(Lnet/minecraft/world/item/ItemStack;)I"))
	private int bloomlevelup$riptideOnUse(ItemStack stack, Operation<Integer> original, @Local(argsOnly = true) Player player) {
		return CombatSkill.riptideLevel(stack, player, original.call(stack));
	}

	@WrapOperation(method = "releaseUsing", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;getRiptide(Lnet/minecraft/world/item/ItemStack;)I"))
	private int bloomlevelup$riptideOnRelease(ItemStack stack, Operation<Integer> original, @Local(argsOnly = true) LivingEntity user) {
		return CombatSkill.riptideLevel(stack, user, original.call(stack));
	}
}
