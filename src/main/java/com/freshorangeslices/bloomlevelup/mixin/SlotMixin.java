package com.freshorangeslices.bloomlevelup.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Stack Overflow in menus: a plain slot over the player's inventory takes the inventory's limit. Slots that
 * set a smaller limit of their own (armor slots hold 1) keep it.
 */
@Mixin(Slot.class)
abstract class SlotMixin {
	@Shadow
	@Final
	public Container container;

	@Shadow
	public abstract int getMaxStackSize();

	@Inject(method = "getMaxStackSize(Lnet/minecraft/world/item/ItemStack;)I", at = @At("RETURN"), cancellable = true)
	private void bloomlevelup$stackOverflow(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
		if (this.container instanceof Inventory inventory && this.getMaxStackSize() >= inventory.getMaxStackSize()) {
			int limit = inventory.getMaxStackSize(stack);

			if (limit > cir.getReturnValueI()) {
				cir.setReturnValue(limit);
			}
		}
	}
}
