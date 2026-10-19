package com.freshorangeslices.bloomlevelup.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import com.freshorangeslices.bloomlevelup.skill.DurabilitySaves;
import com.freshorangeslices.bloomlevelup.skill.misc.StackOverflow;

@Mixin(ItemStack.class)
abstract class ItemStackMixin {
	/** Vanilla Unbreaking has resolved by now; give the remaining damage our backup save rolls. */
	@Inject(method = "processDurabilityChange", at = @At("RETURN"), cancellable = true)
	private void bloomlevelup$backupDurabilitySave(int amount, ServerLevel level, ServerPlayer player, CallbackInfoReturnable<Integer> cir) {
		int damage = cir.getReturnValueI();

		if (player != null && damage > 0) {
			cir.setReturnValue(DurabilitySaves.adjust((ItemStack) (Object) this, damage, player));
		}
	}

	/** Stack Overflow: saved stacks may hold up to 300 (vanilla's codec stops at 99). */
	@ModifyArg(method = "lambda$static$1", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ExtraCodecs;intRange(II)Lcom/mojang/serialization/Codec;"),
			index = 1)
	private static int bloomlevelup$savedCountRange(int max) {
		return Math.max(max, StackOverflow.HARD_CAP);
	}

	/** Stack Overflow: a loaded stack bigger than the item's normal limit is still valid, up to 300 (stackables only). */
	@WrapOperation(method = "validateStrict", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getMaxStackSize()I"))
	private static int bloomlevelup$validatedMax(ItemStack stack, Operation<Integer> original) {
		int max = original.call(stack);
		return max > 1 ? Math.max(max, StackOverflow.HARD_CAP) : max;
	}
}
