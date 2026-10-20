package com.freshorangeslices.bloomlevelup.mixin;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.PiercingWeapon;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.freshorangeslices.bloomlevelup.skill.combat.CombatSkill;

/**
 * A spear's left-click jab doesn't go through {@code Player.attack} (it stabs everything along the reach
 * instead), so the swing is marked here: the first thing it stabs counts as the swing's own target, like a
 * normal attack, and Batter Up!, Impale and the rest follow it.
 */
@Mixin(PiercingWeapon.class)
abstract class PiercingWeaponMixin {
	@Inject(method = "attack", at = @At("HEAD"))
	private void bloomlevelup$startJab(LivingEntity attacker, EquipmentSlot slot, CallbackInfo ci) {
		CombatSkill.startJab(attacker);
	}

	@Inject(method = "attack", at = @At("RETURN"))
	private void bloomlevelup$endJab(LivingEntity attacker, EquipmentSlot slot, CallbackInfo ci) {
		CombatSkill.endJab(attacker);
	}
}
