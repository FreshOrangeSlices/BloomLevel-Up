package com.freshorangeslices.bloomlevelup.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.freshorangeslices.bloomlevelup.skill.misc.StackOverflow;

/**
 * Stack Overflow: the player's own inventory holds up to 300 of anything stackable. Vanilla's Inventory uses
 * Container's default per-item limit, so this adds an override (pickup, add and merge all go through it).
 */
@Mixin(Inventory.class)
abstract class InventoryMixin implements Container {
	@Shadow
	@Final
	public Player player;

	@Override
	public int getMaxStackSize(ItemStack stack) {
		return StackOverflow.limit(this.player, stack, Math.min(this.getMaxStackSize(), stack.getMaxStackSize()));
	}

	/**
	 * Putting items back (closing a menu with something on the cursor or in a crafting grid) sizes each chunk as
	 * "item max - what the slot holds". A slot holding more than the item's max would make that negative and the
	 * loop would never end, so it uses this inventory's real limit instead.
	 */
	@WrapOperation(method = "placeItemBackInInventory(Lnet/minecraft/world/item/ItemStack;ZLnet/minecraft/util/Prediction;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getMaxStackSize()I"))
	private int bloomlevelup$putBackLimit(ItemStack stack, Operation<Integer> original) {
		return Math.max(original.call(stack), this.getMaxStackSize(stack));
	}
}
