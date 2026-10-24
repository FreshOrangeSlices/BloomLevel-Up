package com.freshorangeslices.bloomlevelup.client.compat.mixin;

import net.bettercombat.client.AttackInteractor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.freshorangeslices.bloomlevelup.client.compat.BetterCombatClient;

/**
 * Better Combat's client swing: the animation it's about to play (and send to everyone watching) becomes Bloom's
 * slam when the swing is a charged release. Only applied when Better Combat is installed.
 */
@Mixin(value = AttackInteractor.class, remap = false)
abstract class BetterCombatAttackInteractorMixin {
	@ModifyVariable(method = "startUpswing", at = @At("STORE"), ordinal = 0)
	private String bloomlevelup$chargedSlam(String animation) {
		return BetterCombatClient.swingAnimation(animation);
	}
}
