package com.freshorangeslices.bloomlevelup.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.world.entity.player.Abilities;

import com.freshorangeslices.bloomlevelup.skill.misc.MiscSkill;

/**
 * Overenchant: show the real level cost instead of "Too Expensive!" (1.20.1's renderLabels shows it from 40 levels
 * unless the player is in creative, which it reads from the abilities' instabuild flag).
 */
@Mixin(AnvilScreen.class)
abstract class AnvilScreenMixin {
	@WrapOperation(method = "renderLabels", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/player/Abilities;instabuild:Z"))
	private boolean bloomlevelup$overenchant(Abilities abilities, Operation<Boolean> original) {
		Minecraft minecraft = Minecraft.getInstance();
		return original.call(abilities) || (minecraft.player != null && MiscSkill.overenchant(minecraft.player));
	}
}
