package com.freshorangeslices.bloomlevelup.client.mixin;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.freshorangeslices.bloomlevelup.client.combat.CombatControls;

@Mixin(Minecraft.class)
abstract class MinecraftMixin {
	/** The weapon-skill key on right click: hold to charge, tap to throw (see CombatControls for when it steps aside). */
	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void bloomlevelup$weaponSkill(CallbackInfo ci) {
		if (CombatControls.interceptUse((Minecraft) (Object) this)) {
			ci.cancel();
		}
	}

}
