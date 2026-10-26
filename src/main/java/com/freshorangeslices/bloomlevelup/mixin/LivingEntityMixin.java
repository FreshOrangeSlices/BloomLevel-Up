package com.freshorangeslices.bloomlevelup.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import com.freshorangeslices.bloomlevelup.data.BloomServices;
import com.freshorangeslices.bloomlevelup.player.BloomAttachments;
import com.freshorangeslices.bloomlevelup.skill.ConsumeContext;
import com.freshorangeslices.bloomlevelup.skill.armor.ArmorIds;
import com.freshorangeslices.bloomlevelup.skill.armor.ArmorSkill;
import com.freshorangeslices.bloomlevelup.skill.armor.ArmorStyles;
import com.freshorangeslices.bloomlevelup.skill.combat.CombatSkill;
import com.freshorangeslices.bloomlevelup.skill.misc.MiscSkill;
import com.freshorangeslices.bloomlevelup.skill.misc.RuneEmbedding;

@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
	private static final net.minecraft.tags.TagKey<net.minecraft.world.damagesource.DamageType> NO_KNOCKBACK = net.minecraft.tags.TagKey.create(
			net.minecraft.core.registries.Registries.DAMAGE_TYPE, new net.minecraft.resources.ResourceLocation("minecraft", "no_knockback"));

	/** Combat: classify the hit and add the bonuses that count as real damage, before any mitigation. */
	@ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float bloomlevelup$combatBonuses(float amount, DamageSource source, float originalAmount) {
		return CombatSkill.modifyIncoming((LivingEntity) (Object) this, source, amount);
	}

	/** Armor: remember the hit coming in, so its end can tell what it took (Armor XP, Rebound, Ascension). */
	@Inject(method = "hurt", at = @At("HEAD"))
	private void bloomlevelup$beginHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		if (((LivingEntity) (Object) this).level() instanceof ServerLevel) {
			ArmorSkill.beginHurt((LivingEntity) (Object) this, amount);
		}
	}

	@Inject(method = "hurt", at = @At("RETURN"))
	private void bloomlevelup$endHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		ArmorSkill.endHurt((LivingEntity) (Object) this, source);
	}

	/**
	 * Armor: Castle of Stone, Reflect, Dodge and Parkour may stop the hit. Where Fabric's allow-damage event sits: after
	 * the invulnerability, death and fire-resistance checks (and, for players, after creative mode and difficulty
	 * scaling in Player.hurt). Forge's attack event fires before all of those for players.
	 */
	@Inject(method = "hurt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isSleeping()Z", ordinal = 0), cancellable = true)
	private void bloomlevelup$allowDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;

		if (self instanceof ServerPlayer && !ArmorSkill.allowDamage(self, source, amount)) {
			ArmorSkill.endHurt(self, source);
			cir.setReturnValue(false);
		}
	}

	/** Armor / Last Breath: before a Totem of Undying is used (Fabric's allow-death event sits there). */
	@Inject(method = "checkTotemDeathProtection", at = @At("HEAD"), cancellable = true)
	private void bloomlevelup$lastBreath(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
		if (!ArmorSkill.allowDeath((LivingEntity) (Object) this, source)) {
			cir.setReturnValue(true);
		}
	}

	/**
	 * Bloom's own side damage (Lightning Shock, Overkill, Bleed) never knocks back: the {@code minecraft:no_knockback}
	 * damage type tag, which 1.20.1's hurt doesn't check yet.
	 */
	@WrapOperation(method = "hurt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;knockback(DDD)V"))
	private void bloomlevelup$noKnockback(LivingEntity self, double strength, double x, double z, Operation<Void> original,
			@Local(argsOnly = true) DamageSource source) {
		if (!source.is(NO_KNOCKBACK)) {
			original.call(self, strength, x, z);
		}
	}

	/** After vanilla armor, Resistance and enchantment protection: apply the Armor category's own layers. */
	@Inject(method = "getDamageAfterMagicAbsorb", at = @At("RETURN"), cancellable = true)
	private void bloomlevelup$armorLayers(DamageSource source, float damage, CallbackInfoReturnable<Float> cir) {
		if ((Object) this instanceof ServerPlayer player) {
			cir.setReturnValue(ArmorSkill.modifyFinalDamage(player, source, cir.getReturnValueF()));
		}

		CombatSkill.recordMitigated((LivingEntity) (Object) this, cir.getReturnValueF());
	}

	/** Rune Embedding: body runes protect like the same enchantments on worn armor. */
	@WrapOperation(method = "getDamageAfterMagicAbsorb", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;getDamageProtection(Ljava/lang/Iterable;Lnet/minecraft/world/damagesource/DamageSource;)I"))
	private int bloomlevelup$runeProtection(Iterable<ItemStack> armor, DamageSource source, Operation<Integer> original) {
		return original.call(armor, source) + RuneEmbedding.protection((LivingEntity) (Object) this, source);
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

	/** Combat: measure the health actually lost (XP, Execute, Armor Break, Overkill...). */
	@Inject(method = "actuallyHurt", at = @At("HEAD"))
	private void bloomlevelup$beforeHurt(DamageSource source, float amount, CallbackInfo ci) {
		CombatSkill.beforeRealDamage((LivingEntity) (Object) this);
	}

	@Inject(method = "actuallyHurt", at = @At("RETURN"))
	private void bloomlevelup$afterHurt(DamageSource source, float amount, CallbackInfo ci) {
		if (((LivingEntity) (Object) this).level() instanceof ServerLevel level) {
			CombatSkill.afterRealDamage((LivingEntity) (Object) this, level, source);
		}
	}

	/** Combat / Penetration: the attacker ignores part of the armor. */
	@WrapOperation(method = "getDamageAfterArmorAbsorb", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/damagesource/CombatRules;getDamageAfterAbsorb(FFF)F"))
	private float bloomlevelup$penetration(float damage, float armor, float toughness, Operation<Float> original,
			@Local(argsOnly = true) DamageSource source) {
		return original.call(damage, CombatSkill.penetrate(source, (LivingEntity) (Object) this, armor), toughness);
	}

	/** Titan Hide's drawback: double knockback while fighting with no armor on. */
	@ModifyVariable(method = "knockback", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private double bloomlevelup$titanHideKnockback(double strength) {
		if ((Object) this instanceof ServerPlayer player
				&& BloomServices.progression().isMajorActive(BloomAttachments.profile(player), ArmorIds.TITAN_HIDE)
				&& ArmorStyles.allArmorSlotsEmpty(player)) {
			return strength * 2.0;
		}

		return strength;
	}

	/** Eating and drinking: what is being consumed, while it is (food effects, milk, Protein!, Become My Fuel). */
	@Inject(method = "completeUsingItem", at = @At("HEAD"))
	private void bloomlevelup$beginConsume(CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;

		if (self instanceof ServerPlayer player && self.isUsingItem() && !self.getUseItem().isEmpty()) {
			ConsumeContext.begin(player, self.getUseItem(), self.getUsedItemHand() == InteractionHand.OFF_HAND);
		}
	}

	@Inject(method = "completeUsingItem", at = @At("RETURN"))
	private void bloomlevelup$endConsume(CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer player) {
			ConsumeContext.end(player);
		}
	}
}
