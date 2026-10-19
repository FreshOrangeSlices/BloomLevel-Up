package com.freshorangeslices.bloomlevelup.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

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
}
