package com.freshorangeslices.bloomlevelup.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/** Stack Overflow: a big stack thrown out lands as normal-sized stacks. */
@Mixin(ServerPlayer.class)
abstract class ServerPlayerMixin {
	@Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;", at = @At("HEAD"))
	private void bloomlevelup$splitBigStacks(ItemStack stack, boolean dropAround, boolean includeThrower, CallbackInfoReturnable<ItemEntity> cir) {
		int max = stack.getMaxStackSize();

		while (max > 0 && stack.getCount() > max) {
			((ServerPlayer) (Object) this).drop(stack.split(max), dropAround, includeThrower);
		}
	}
}
