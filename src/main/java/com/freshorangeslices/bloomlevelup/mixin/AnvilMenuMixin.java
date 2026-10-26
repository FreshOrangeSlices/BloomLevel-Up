package com.freshorangeslices.bloomlevelup.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.enchantment.Enchantment;

import com.freshorangeslices.bloomlevelup.skill.misc.MiscSkill;

/** Overenchant: enchantments combine past their usual cap (to 8), and the anvil is never "Too Expensive!". */
@Mixin(AnvilMenu.class)
abstract class AnvilMenuMixin extends ItemCombinerMenu {
	AnvilMenuMixin(MenuType<?> type, int containerId, Inventory inventory, ContainerLevelAccess access) {
		super(type, containerId, inventory, access);
	}

	@WrapOperation(method = "createResult", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/enchantment/Enchantment;getMaxLevel()I"))
	private int bloomlevelup$overenchantCap(Enchantment enchantment, Operation<Integer> original) {
		return MiscSkill.anvilMaxLevel(this.player, original.call(enchantment));
	}

	/** The second creative check in createResult is the "cost >= 40 and not creative" Too Expensive rule. */
	@ModifyExpressionValue(method = "createResult", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/player/Abilities;instabuild:Z",
			ordinal = 1))
	private boolean bloomlevelup$noTooExpensive(boolean instabuild) {
		return instabuild || MiscSkill.overenchant(this.player);
	}
}
