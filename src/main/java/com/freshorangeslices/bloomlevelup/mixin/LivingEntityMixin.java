package com.freshorangeslices.bloomlevelup.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import com.freshorangeslices.bloomlevelup.data.BloomServices;
import com.freshorangeslices.bloomlevelup.player.BloomAttachments;
import com.freshorangeslices.bloomlevelup.skill.armor.ArmorIds;
import com.freshorangeslices.bloomlevelup.skill.armor.ArmorSkill;
import com.freshorangeslices.bloomlevelup.skill.armor.ArmorStyles;

@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
	/** After vanilla armor, Resistance and enchantment protection: apply the Armor category's own layers. */
	@Inject(method = "getDamageAfterMagicAbsorb", at = @At("RETURN"), cancellable = true)
	private void bloomlevelup$armorLayers(DamageSource source, float damage, CallbackInfoReturnable<Float> cir) {
		if ((Object) this instanceof ServerPlayer player) {
			cir.setReturnValue(ArmorSkill.modifyFinalDamage(player, source, cir.getReturnValueF()));
		}
	}

	/** Titan Hide's drawback: double knockback while fighting with no armor on. */
	@ModifyVariable(method = "knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private double bloomlevelup$titanHideKnockback(double strength) {
		if ((Object) this instanceof ServerPlayer player
				&& BloomServices.progression().isMajorActive(BloomAttachments.profile(player), ArmorIds.TITAN_HIDE)
				&& ArmorStyles.allArmorSlotsEmpty(player)) {
			return strength * 2.0;
		}

		return strength;
	}
}
