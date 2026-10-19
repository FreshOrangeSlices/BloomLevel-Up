package com.freshorangeslices.bloomlevelup.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;

import com.freshorangeslices.bloomlevelup.skill.ConsumeContext;

@Mixin(Consumable.class)
abstract class ConsumableMixin {
	@Inject(method = "onConsume", at = @At("HEAD"))
	private void bloomlevelup$beginConsume(Level level, LivingEntity entity, ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
		if (entity instanceof ServerPlayer player) {
			ConsumeContext.begin(player, stack);
		}
	}

	@Inject(method = "onConsume", at = @At("RETURN"))
	private void bloomlevelup$endConsume(Level level, LivingEntity entity, ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
		if (entity instanceof ServerPlayer player) {
			ConsumeContext.end(player);
		}
	}
}
