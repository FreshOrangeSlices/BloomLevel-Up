package com.freshorangeslices.bloomlevelup.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.consume_effects.ClearAllStatusEffectsConsumeEffect;

import com.freshorangeslices.bloomlevelup.skill.misc.MiscSkill;

/** Selective Cleansing: milk (and anything else that "clears all effects" when consumed) only clears the bad ones. */
@Mixin(ClearAllStatusEffectsConsumeEffect.class)
abstract class ClearAllStatusEffectsConsumeEffectMixin {
	@WrapOperation(method = "apply", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;removeAllEffects()Z"))
	private boolean bloomlevelup$selective(LivingEntity entity, Operation<Boolean> original) {
		return MiscSkill.selectiveMilk(entity) || original.call(entity);
	}
}
