package com.freshorangeslices.bloomlevelup.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.player.LocalPlayer;

import com.freshorangeslices.bloomlevelup.skill.misc.MiscSkill;

/** Overenchant: show the real level cost instead of "Too Expensive!". */
@Mixin(AnvilScreen.class)
abstract class AnvilScreenMixin {
	@WrapOperation(method = "extractLabels", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;hasInfiniteMaterials()Z"))
	private boolean bloomlevelup$overenchant(LocalPlayer player, Operation<Boolean> original) {
		return original.call(player) || MiscSkill.overenchant(player);
	}
}
