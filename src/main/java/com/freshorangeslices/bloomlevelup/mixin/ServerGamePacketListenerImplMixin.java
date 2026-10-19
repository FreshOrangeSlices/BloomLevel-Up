package com.freshorangeslices.bloomlevelup.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;

/**
 * Stack Overflow in creative mode: the creative inventory reports slots one by one, and vanilla rejects a stack
 * bigger than the item's size (deleting it on the server). Allow what the player's inventory allows.
 */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class ServerGamePacketListenerImplMixin {
	@Shadow
	public ServerPlayer player;

	@WrapOperation(method = "handleSetCreativeModeSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getMaxStackSize()I"))
	private int bloomlevelup$creativeStackOverflow(ItemStack stack, Operation<Integer> original) {
		return Math.max(original.call(stack), this.player.getInventory().getMaxStackSize(stack));
	}
}
