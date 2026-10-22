package com.freshorangeslices.bloomlevelup.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.freshorangeslices.bloomlevelup.skill.combat.CombatSkill;
import com.freshorangeslices.bloomlevelup.skill.misc.MiscSkill;
import com.freshorangeslices.bloomlevelup.skill.misc.RuneEmbedding;

/** Players override actuallyHurt without calling super, so the real-damage hook is needed here too. Also Misc's food rules. */
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

	/** Stored Impact works from the fall itself, before Feather Falling, armor or Titan Hide reduce it. */
	@Inject(method = "causeFallDamage", at = @At("HEAD"))
	private void bloomlevelup$noteFall(double fallDistance, float multiplier, DamageSource source, CallbackInfoReturnable<Boolean> cir) {
		CombatSkill.noteFall((Player) (Object) this, fallDistance, multiplier);
	}

	/** A spear jab's first stab is the swing's target (see PiercingWeaponMixin). */
	@Inject(method = "stabAttack", at = @At("HEAD"))
	private void bloomlevelup$stab(EquipmentSlot slot, Entity target, float damage, boolean dealsDamage, boolean knockback, boolean dismount,
			CallbackInfoReturnable<Boolean> cir) {
		CombatSkill.onStab((Player) (Object) this, target);
	}

	/** Endurance: less exhaustion from everything that drains food. */
	@ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true)
	private float bloomlevelup$endurance(float amount) {
		return MiscSkill.exhaustion((Player) (Object) this, amount);
	}

	/** Second Serving: food can always be eaten (both sides, so the client starts eating too). */
	@Inject(method = "canEat", at = @At("RETURN"), cancellable = true)
	private void bloomlevelup$secondServing(boolean canAlwaysEat, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && MiscSkill.canAlwaysEat((Player) (Object) this)) {
			cir.setReturnValue(true);
		}
	}

	/** Rune Embedding: a bare-handed attack hits with the bare-hand rune's enchantments. */
	@Inject(method = "getWeaponItem", at = @At("RETURN"), cancellable = true)
	private void bloomlevelup$bareHandRune(CallbackInfoReturnable<ItemStack> cir) {
		ItemStack weapon = RuneEmbedding.weaponItem((Player) (Object) this, cir.getReturnValue());

		if (weapon != cir.getReturnValue()) {
			cir.setReturnValue(weapon);
		}
	}

	/** Ascension: being allowed to fly doesn't make falls harmless; only actually flying does. */
	@ModifyExpressionValue(method = "causeFallDamage", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/player/Abilities;mayfly:Z"))
	private boolean bloomlevelup$ascensionFalls(boolean mayfly) {
		return MiscSkill.fallImmune((Player) (Object) this, mayfly);
	}
}
