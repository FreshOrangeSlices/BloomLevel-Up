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

import com.freshorangeslices.bloomlevelup.skill.misc.HoardersPocket;

/**
 * Stack Overflow in menus: a plain slot over the player's inventory or pocket takes that container's limit. Slots that
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
		// The player's inventory and Hoarder's Pocket (both sides) carry Stack Overflow's limit.
		boolean overflowing = this.container instanceof Inventory || this.container instanceof HoardersPocket.PocketContainer;

		if (overflowing && this.getMaxStackSize() >= this.container.getMaxStackSize()) {
			int limit = this.container.getMaxStackSize(stack);

			if (limit > cir.getReturnValueI()) {
				cir.setReturnValue(limit);
			}
		}
	}
}
