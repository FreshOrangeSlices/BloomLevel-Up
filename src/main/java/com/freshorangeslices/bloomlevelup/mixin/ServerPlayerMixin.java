package com.freshorangeslices.bloomlevelup.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Stack Overflow: a stack bigger than the item's normal size never leaves the player as one item entity. It is
 * dropped as normal-size stacks, so hoppers, mobs and anything else that assumes vanilla sizes can't cut it down.
 * Covers Q / ctrl+Q, death drops, clicking outside a menu, creative drops and Disarm.
 */
@Mixin(ServerPlayer.class)
abstract class ServerPlayerMixin {
	@Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZLnet/minecraft/util/Prediction;)Lnet/minecraft/world/entity/item/ItemEntity;",
			at = @At("HEAD"))
	private void bloomlevelup$splitBigStacks(ItemStack stack, boolean dropAround, Prediction prediction, CallbackInfoReturnable<ItemEntity> cir) {
		int max = stack.getMaxStackSize();

		while (max > 0 && stack.getCount() > max) {
			((ServerPlayer) (Object) this).drop(stack.split(max), dropAround, prediction);
		}
	}
}
