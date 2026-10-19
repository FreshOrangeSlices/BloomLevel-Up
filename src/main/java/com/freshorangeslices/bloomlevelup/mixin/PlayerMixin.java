package com.freshorangeslices.bloomlevelup.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.freshorangeslices.bloomlevelup.skill.combat.CombatSkill;

/** Players override actuallyHurt without calling super, so the real-damage hook is needed here too. */
@Mixin(Player.class)
abstract class PlayerMixin {
	@Inject(method = "actuallyHurt", at = @At("HEAD"))
	private void bloomlevelup$beforeHurt(ServerLevel level, DamageSource source, float amount, CallbackInfo ci) {
		CombatSkill.beforeRealDamage((LivingEntity) (Object) this);
	}

	@Inject(method = "actuallyHurt", at = @At("RETURN"))
	private void bloomlevelup$afterHurt(ServerLevel level, DamageSource source, float amount, CallbackInfo ci) {
		CombatSkill.afterRealDamage((LivingEntity) (Object) this, level, source);
	}
}
