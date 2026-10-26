package com.freshorangeslices.bloomlevelup.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import com.freshorangeslices.bloomlevelup.skill.misc.HoardersPocket;
import com.freshorangeslices.bloomlevelup.skill.misc.StackOverflow;

/** Stack Overflow: the player's own inventory slots and Hoarder's Pocket hold big stacks (both sides). */
@Mixin(Slot.class)
abstract class SlotMixin {
	@Shadow
	@Final
	public Container container;

	@Inject(method = "getMaxStackSize(Lnet/minecraft/world/item/ItemStack;)I", at = @At("RETURN"), cancellable = true)
	private void bloomlevelup$stackOverflow(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
		Player owner = this.container instanceof Inventory inventory ? inventory.player
				: this.container instanceof HoardersPocket.PocketContainer pocket ? pocket.owner() : null;

		// Armor and offhand slots keep their own small limits (they report less than the container's 64).
		if (owner != null && ((Slot) (Object) this).getMaxStackSize() >= this.container.getMaxStackSize()) {
			int limit = StackOverflow.limit(owner, stack, cir.getReturnValueI());

			if (limit > cir.getReturnValueI()) {
				cir.setReturnValue(limit);
			}
		}
	}
}
