package com.freshorangeslices.bloomlevelup.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;

import com.freshorangeslices.bloomlevelup.skill.armor.ArmorSkill;

/** Fitting Armor / Medium style: shields are disabled for half as long. */
@Mixin(BlocksAttacks.class)
abstract class BlocksAttacksMixin {
	@ModifyVariable(method = "disable", at = @At("HEAD"), argsOnly = true)
	private float bloomlevelup$mediumShield(float seconds, ServerLevel level, LivingEntity entity, float originalSeconds, ItemStack shield) {
		return ArmorSkill.shieldDisableSeconds(entity, seconds);
	}
}
