package com.freshorangeslices.bloomlevelup.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.TridentItem;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.freshorangeslices.bloomlevelup.skill.combat.CombatSkill;

@Mixin(TridentItem.class)
abstract class TridentItemMixin {
	/** Unrestricted Riptide: no water or rain needed. */
	@WrapOperation(method = {"use", "releaseUsing"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isInWaterOrRain()Z"))
	private boolean bloomlevelup$riptideAnywhere(Player player, Operation<Boolean> original) {
		return original.call(player) || CombatSkill.riptideAnywhere(player);
	}
}
