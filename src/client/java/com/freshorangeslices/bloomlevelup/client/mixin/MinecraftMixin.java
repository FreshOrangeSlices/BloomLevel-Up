package com.freshorangeslices.bloomlevelup.client.mixin;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.freshorangeslices.bloomlevelup.client.combat.CombatControls;

@Mixin(Minecraft.class)
abstract class MinecraftMixin {
	/** Charge Attack / Heavy Hands hold the swing until release; Lightning Summon takes over sneak-attacks with a trident. */
	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void bloomlevelup$combatControls(CallbackInfoReturnable<Boolean> cir) {
		if (CombatControls.interceptAttack((Minecraft) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
