package com.freshorangeslices.bloomlevelup.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.freshorangeslices.bloomlevelup.compat.BetterCombat;
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

	/** Better Combat: Bloom's counter to its crowd penalty lasts one hit (put on at the start, by the attack event). */
	@Inject(method = "attack", at = @At("RETURN"))
	private void bloomlevelup$afterAttack(Entity target, CallbackInfo ci) {
		BetterCombat.afterAttack((Player) (Object) this);
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

	/**
	 * Fitting Armor / Medium: an axe disables your shield for less time. The shield in use and whether it was already
	 * down are noted first; afterwards, a cooldown the hit started is shortened, however long it was set for (other
	 * mods change that line, so Bloom doesn't touch it).
	 */
	@Inject(method = "disableShield", at = @At("HEAD"))
	private void bloomlevelup$shieldBefore(boolean sprinting, CallbackInfo ci, @Share("shield") LocalRef<Item> shield,
			@Share("wasDown") LocalBooleanRef wasDown) {
		Player self = (Player) (Object) this;
		Item item = self.getUseItem().getItem();
		shield.set(item);
		wasDown.set(self.getCooldowns().isOnCooldown(item));
	}

	@Inject(method = "disableShield", at = @At("RETURN"))
	private void bloomlevelup$mediumShield(boolean sprinting, CallbackInfo ci, @Share("shield") LocalRef<Item> shield,
			@Share("wasDown") LocalBooleanRef wasDown) {
		ArmorSkill.shortenShieldDisable((Player) (Object) this, shield.get(), wasDown.get());
	}
}
