package com.freshorangeslices.bloomlevelup.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ItemCombinerMenuSlotDefinition;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.enchantment.Enchantment;

import com.freshorangeslices.bloomlevelup.skill.misc.MiscSkill;

/**
 * Overenchant: combining can push enchantments past their vanilla cap (up to 8), and "Too Expensive!" never
 * blocks the result. You still pay the level cost. Runs on both sides, so the client's preview matches.
 */
@Mixin(AnvilMenu.class)
abstract class AnvilMenuMixin extends ItemCombinerMenu {
	AnvilMenuMixin(MenuType<?> type, int containerId, Inventory inventory, ContainerLevelAccess access, ItemCombinerMenuSlotDefinition slots) {
		super(type, containerId, inventory, access, slots);
	}

	@WrapOperation(method = "createResult", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/enchantment/Enchantment;getMaxLevel()I"))
	private int bloomlevelup$overenchantCap(Enchantment enchantment, Operation<Integer> original) {
		return MiscSkill.anvilMaxLevel(this.player, original.call(enchantment));
	}

	/** The second check in createResult is the "cost >= 40 and not creative" Too Expensive rule. */
	@WrapOperation(method = "createResult", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;hasInfiniteMaterials()Z",
			ordinal = 1))
	private boolean bloomlevelup$noTooExpensive(Player player, Operation<Boolean> original) {
		return original.call(player) || MiscSkill.overenchant(player);
	}
}
