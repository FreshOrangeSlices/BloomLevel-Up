package com.freshorangeslices.bloomlevelup.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.effect.MobEffectInstance;

/** Water to Wine: shortening an effect it stretched, in place (re-adding would pass Unyielding's dice again). */
@Mixin(MobEffectInstance.class)
public interface MobEffectInstanceAccessor {
	@Accessor("duration")
	void bloomlevelup$setDuration(int duration);
}
