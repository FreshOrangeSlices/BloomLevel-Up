package com.freshorangeslices.bloomlevelup.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.freshorangeslices.bloomlevelup.skill.combat.CombatSkill;

@Mixin(ProjectileWeaponItem.class)
abstract class ProjectileWeaponItemMixin {
	/** Crossbow Infinity: loading a plain arrow treats it like Infinity (not used up, can't be picked back up). */
	@ModifyVariable(method = "useAmmo", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private static boolean bloomlevelup$crossbowInfinity(boolean intangible, ItemStack weapon, ItemStack ammo, LivingEntity shooter, boolean originalIntangible) {
		return intangible || CombatSkill.freeAmmo(weapon, ammo, shooter);
	}
}
