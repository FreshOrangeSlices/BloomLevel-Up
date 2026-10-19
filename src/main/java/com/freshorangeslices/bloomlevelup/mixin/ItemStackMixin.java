package com.freshorangeslices.bloomlevelup.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import com.freshorangeslices.bloomlevelup.skill.DurabilitySaves;

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
}
