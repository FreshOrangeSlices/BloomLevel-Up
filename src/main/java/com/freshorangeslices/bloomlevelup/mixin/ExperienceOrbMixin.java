package com.freshorangeslices.bloomlevelup.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;

import com.freshorangeslices.bloomlevelup.skill.misc.MiscSkill;

/** Misc XP mirrors each collected orb's own value; Wisdom multiplies the vanilla XP the player then gets. */
@Mixin(ExperienceOrb.class)
abstract class ExperienceOrbMixin {
	@Inject(method = "playerTouch", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/player/Player;take(Lnet/minecraft/world/entity/Entity;I)V"))
	private void bloomlevelup$miscXp(Player player, CallbackInfo ci) {
		if (player instanceof ServerPlayer serverPlayer) {
			MiscSkill.orbCollected(serverPlayer, ((ExperienceOrb) (Object) this).getValue());
		}
	}

	@WrapOperation(method = "playerTouch", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/player/Player;giveExperiencePoints(I)V"))
	private void bloomlevelup$wisdom(Player player, int amount, Operation<Void> original) {
		original.call(player, MiscSkill.wisdomXp(player, amount));
	}
}
