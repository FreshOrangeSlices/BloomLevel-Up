package com.freshorangeslices.bloomlevelup.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.freshorangeslices.bloomlevelup.skill.armor.ArmorSkill;
import com.freshorangeslices.bloomlevelup.skill.combat.CombatSkill;
import com.freshorangeslices.bloomlevelup.skill.misc.MiscSkill;
import com.freshorangeslices.bloomlevelup.skill.misc.RuneEmbedding;

@Mixin(Player.class)
abstract class PlayerMixin {
	// Player overrides actuallyHurt without calling LivingEntity's, so the Combat measurement goes here too.
	@Inject(method = "actuallyHurt", at = @At("HEAD"))
	private void bloomlevelup$beforeHurt(DamageSource source, float amount, CallbackInfo ci) {
		CombatSkill.beforeRealDamage((LivingEntity) (Object) this);
	}

	@Inject(method = "actuallyHurt", at = @At("RETURN"))
	private void bloomlevelup$afterHurt(DamageSource source, float amount, CallbackInfo ci) {
		if (((Player) (Object) this).level() instanceof ServerLevel level) {
			CombatSkill.afterRealDamage((LivingEntity) (Object) this, level, source);
		}
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

	/** Rune Embedding: a bare-handed attack's damage bonus comes from the bare-hand rune (sharpness, smite...). */
	@WrapOperation(method = "attack", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;getDamageBonus(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/MobType;)F"))
	private float bloomlevelup$bareHandRune(ItemStack weapon, MobType type, Operation<Float> original) {
		return original.call(RuneEmbedding.weaponItem((Player) (Object) this, weapon), type);
	}

	/** Full Sweep: a sword's sweep hits for the whole blow. */
	@WrapOperation(method = "attack", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;getSweepingDamageRatio(Lnet/minecraft/world/entity/LivingEntity;)F"))
	private float bloomlevelup$fullSweep(LivingEntity entity, Operation<Float> original) {
		Player self = (Player) (Object) this;
		return Math.max(original.call(entity), CombatSkill.sweepRatio(self, self.getMainHandItem()));
	}

	/** Ascension: being allowed to fly doesn't make falls harmless; only actually flying does. */
	@ModifyExpressionValue(method = "causeFallDamage", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/player/Abilities;mayfly:Z"))
	private boolean bloomlevelup$ascensionFalls(boolean mayfly) {
		return MiscSkill.fallImmune((Player) (Object) this, mayfly);
	}

	/** Fitting Armor / Medium: an axe disables your shield for less time. */
	@ModifyArg(method = "disableShield", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/ItemCooldowns;addCooldown(Lnet/minecraft/world/item/Item;I)V"), index = 1)
	private int bloomlevelup$mediumShield(int ticks) {
		return ArmorSkill.shieldDisableTicks((Player) (Object) this, ticks);
	}
}
