package com.freshorangeslices.bloomlevelup.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import com.freshorangeslices.bloomlevelup.skill.combat.CombatSkill;
import com.freshorangeslices.bloomlevelup.skill.misc.RuneEmbedding;

@Mixin(EnchantmentHelper.class)
abstract class EnchantmentHelperMixin {
	/**
	 * Every "what level of X does this entity have" answer: Rune Embedding's runes count like the same enchantment on
	 * worn gear (the higher one wins), and Combat / Looting adds virtual Looting levels.
	 */
	@Inject(method = "getEnchantmentLevel(Lnet/minecraft/world/item/enchantment/Enchantment;Lnet/minecraft/world/entity/LivingEntity;)I",
			at = @At("RETURN"), cancellable = true)
	private static void bloomlevelup$levels(Enchantment enchantment, LivingEntity entity, CallbackInfoReturnable<Integer> cir) {
		int level = Math.max(cir.getReturnValueI(), RuneEmbedding.level(enchantment, entity));
		int extra = CombatSkill.extraLooting(enchantment, entity);

		if (level + extra != cir.getReturnValueI()) {
			cir.setReturnValue(level + extra);
		}
	}

	/** Rune Embedding: body runes answer a hit like worn gear would (thorns). */
	@Inject(method = "doPostHurtEffects", at = @At("TAIL"))
	private static void bloomlevelup$bodyRunesHurt(LivingEntity user, Entity attacker, CallbackInfo ci) {
		RuneEmbedding.afterHurt(user, attacker);
	}

	/** Rune Embedding: the bare-hand rune's on-hit enchantments. */
	@Inject(method = "doPostDamageEffects", at = @At("TAIL"))
	private static void bloomlevelup$bareHandRuneHit(LivingEntity user, Entity target, CallbackInfo ci) {
		RuneEmbedding.afterAttack(user, target);
	}
}
