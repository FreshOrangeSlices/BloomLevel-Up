package com.freshorangeslices.bloomlevelup.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import com.freshorangeslices.bloomlevelup.data.BloomServices;
import com.freshorangeslices.bloomlevelup.player.BloomAttachments;
import com.freshorangeslices.bloomlevelup.skill.armor.ArmorIds;
import com.freshorangeslices.bloomlevelup.skill.armor.ArmorSkill;
import com.freshorangeslices.bloomlevelup.skill.armor.ArmorStyles;
import com.freshorangeslices.bloomlevelup.skill.combat.CombatSkill;
import com.freshorangeslices.bloomlevelup.skill.misc.MiscSkill;

@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
	/** After vanilla armor, Resistance and enchantment protection: apply the Armor category's own layers. */
	/** Long Reach: a spear's own attack range (its jab and charge) reaches further. Both sides. */
	@Inject(method = "getAttackRangeWith", at = @At("RETURN"), cancellable = true)
	private void bloomlevelup$longReach(net.minecraft.world.item.ItemStack stack, CallbackInfoReturnable<net.minecraft.world.item.component.AttackRange> cir) {
		net.minecraft.world.item.component.AttackRange range = CombatSkill.longReach((LivingEntity) (Object) this, stack, cir.getReturnValue());

		if (range != cir.getReturnValue()) {
			cir.setReturnValue(range);
		}
	}

	@Inject(method = "getDamageAfterMagicAbsorb", at = @At("RETURN"), cancellable = true)
	private void bloomlevelup$armorLayers(DamageSource source, float damage, CallbackInfoReturnable<Float> cir) {
		if ((Object) this instanceof ServerPlayer player) {
			cir.setReturnValue(ArmorSkill.modifyFinalDamage(player, source, cir.getReturnValueF()));
		}

		CombatSkill.recordMitigated((LivingEntity) (Object) this, cir.getReturnValueF());
	}

	/** Misc: Water to Wine (10-minute floor) and Overdose (re-applying climbs a level) reshape incoming effects. */
	@ModifyVariable(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"),
			argsOnly = true)
	private MobEffectInstance bloomlevelup$miscEffects(MobEffectInstance instance) {
		return MiscSkill.modifyIncomingEffect((LivingEntity) (Object) this, instance);
	}

	@Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z", at = @At("RETURN"))
	private void bloomlevelup$miscEffectLanded(MobEffectInstance instance, Entity source, CallbackInfoReturnable<Boolean> cir) {
		MiscSkill.afterEffectAdded((LivingEntity) (Object) this, instance);
	}

	/** Combat: classify the hit and add the bonuses that count as real damage, before any mitigation. */
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float bloomlevelup$combatBonuses(float amount, ServerLevel level, DamageSource source, float originalAmount) {
		return CombatSkill.modifyIncoming((LivingEntity) (Object) this, source, amount);
	}

	/** Combat: measure the health actually lost (XP, Execute, Armor Break, Overkill...). */
	@Inject(method = "actuallyHurt", at = @At("HEAD"))
	private void bloomlevelup$beforeHurt(ServerLevel level, DamageSource source, float amount, CallbackInfo ci) {
		CombatSkill.beforeRealDamage((LivingEntity) (Object) this);
	}

	@Inject(method = "actuallyHurt", at = @At("RETURN"))
	private void bloomlevelup$afterHurt(ServerLevel level, DamageSource source, float amount, CallbackInfo ci) {
		CombatSkill.afterRealDamage((LivingEntity) (Object) this, level, source);
	}

	/** Combat / Penetration: the attacker ignores part of the armor. */
	@WrapOperation(method = "getDamageAfterArmorAbsorb", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/damagesource/CombatRules;getDamageAfterAbsorb(Lnet/minecraft/world/entity/LivingEntity;FLnet/minecraft/world/damagesource/DamageSource;FF)F"))
	private float bloomlevelup$penetration(LivingEntity entity, float damage, DamageSource source, float armor, float toughness, Operation<Float> original) {
		return original.call(entity, damage, source, CombatSkill.penetrate(source, entity, armor), toughness);
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
