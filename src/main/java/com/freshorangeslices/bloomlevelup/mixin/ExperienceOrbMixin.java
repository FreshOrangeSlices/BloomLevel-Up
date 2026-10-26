package com.freshorangeslices.bloomlevelup.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;

import com.freshorangeslices.bloomlevelup.skill.misc.MiscSkill;

/** Wisdom: an orb's vanilla XP (what Mending leaves) is multiplied. The Misc XP itself comes from Forge's pickup event. */
@Mixin(ExperienceOrb.class)
abstract class ExperienceOrbMixin {
	@WrapOperation(method = "playerTouch", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/player/Player;giveExperiencePoints(I)V"))
	private void bloomlevelup$wisdom(Player player, int amount, Operation<Void> original) {
		original.call(player, MiscSkill.wisdomXp(player, amount));
	}
}
