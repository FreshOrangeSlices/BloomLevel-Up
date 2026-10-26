package com.freshorangeslices.bloomlevelup.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.freshorangeslices.bloomlevelup.skill.misc.StackOverflow;

/**
 * Stack Overflow: picking things up fills the player's own slots past the usual limit. 1.20.1 works the limit out
 * inline (the item's max and the inventory's 64), so both are raised where the inventory adds to a stack.
 */
@Mixin(Inventory.class)
abstract class InventoryMixin {
	@Shadow
	@Final
	public Player player;

	@WrapOperation(method = {"addResource(ILnet/minecraft/world/item/ItemStack;)I", "hasRemainingSpaceForItem"},
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getMaxStackSize()I"))
	private int bloomlevelup$itemLimit(ItemStack stack, Operation<Integer> original) {
		return StackOverflow.limit(this.player, stack, original.call(stack));
	}

	@WrapOperation(method = {"addResource(ILnet/minecraft/world/item/ItemStack;)I", "hasRemainingSpaceForItem"},
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;getMaxStackSize()I"))
	private int bloomlevelup$slotLimit(Inventory inventory, Operation<Integer> original) {
		return StackOverflow.slotLimit(this.player, original.call(inventory));
	}

	/**
	 * Putting items back (closing a menu with something on the cursor) sizes each chunk as "item max - what the slot
	 * holds". A slot holding more than the item's max would make that negative and the loop would never end, so it
	 * uses the real limit instead.
	 */
	@WrapOperation(method = "placeItemBackInInventory(Lnet/minecraft/world/item/ItemStack;Z)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getMaxStackSize()I"))
	private int bloomlevelup$putBackLimit(ItemStack stack, Operation<Integer> original) {
		return StackOverflow.limit(this.player, stack, original.call(stack));
	}
}
