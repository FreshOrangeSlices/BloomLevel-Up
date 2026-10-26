package com.freshorangeslices.bloomlevelup.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;

@Mixin(AbstractArrow.class)
public interface AbstractArrowInvoker {
	/** The item picking the arrow up gives (an arrow's own stack, a trident's trident). */
	@Invoker("getPickupItem")
	ItemStack bloomlevelup$getPickupItem();
}
