package com.freshorangeslices.bloomlevelup.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

import com.freshorangeslices.bloomlevelup.skill.misc.XpAtDeath;

/** XP Insurance: remembers the XP held as the player starts dying, before any death drop (or grave) touches it. */
@Mixin(ServerPlayer.class)
abstract class ServerPlayerXpMixin implements XpAtDeath {
	@Unique
	private int bloomlevelup$levelAtDeath = -1;
	@Unique
	private float bloomlevelup$progressAtDeath;

	@Inject(method = "die(Lnet/minecraft/world/damagesource/DamageSource;)V", at = @At("HEAD"))
	private void bloomlevelup$rememberXp(DamageSource source, CallbackInfo ci) {
		ServerPlayer self = (ServerPlayer) (Object) this;
		this.bloomlevelup$levelAtDeath = self.experienceLevel;
		this.bloomlevelup$progressAtDeath = self.experienceProgress;
	}

	@Override
	public int bloomlevelup$levelAtDeath() {
		return this.bloomlevelup$levelAtDeath;
	}

	@Override
	public float bloomlevelup$progressAtDeath() {
		return this.bloomlevelup$progressAtDeath;
	}
}
