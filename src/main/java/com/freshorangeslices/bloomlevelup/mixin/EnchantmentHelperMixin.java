package com.freshorangeslices.bloomlevelup.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.freshorangeslices.bloomlevelup.skill.combat.CombatSkill;
import com.freshorangeslices.bloomlevelup.skill.misc.RuneEmbedding;

@Mixin(EnchantmentHelper.class)
abstract class EnchantmentHelperMixin {
	/** Combat / Looting: virtual Looting levels wherever loot asks the attacker for Looting. */
	@Inject(method = "getEnchantmentLevel(Lnet/minecraft/core/Holder;Lnet/minecraft/world/entity/LivingEntity;)I", at = @At("RETURN"), cancellable = true)
	private static void bloomlevelup$looting(Holder<Enchantment> enchantment, LivingEntity entity, CallbackInfoReturnable<Integer> cir) {
		// Misc / Rune Embedding: a rune counts like the same enchantment on worn gear (the higher one wins).
		int level = Math.max(cir.getReturnValueI(), RuneEmbedding.level(enchantment, entity));
		int extra = CombatSkill.extraLooting(enchantment, entity);

		if (level + extra != cir.getReturnValueI()) {
			cir.setReturnValue(level + extra);
		}
	}

	/** Misc / Rune Embedding: body runes are visited as if worn (protection, thorns, feather falling...). */
	@Inject(method = "runIterationOnEquipment", at = @At("TAIL"))
	private static void bloomlevelup$bodyRunes(LivingEntity entity, EnchantmentHelper.EnchantmentInSlotVisitor visitor, CallbackInfo ci) {
		RuneEmbedding.visitBody(entity, visitor);
	}

	/** Unrestricted Riptide: riptide without the enchantment (while sneaking). */
	@Inject(method = "getTridentSpinAttackStrength", at = @At("RETURN"), cancellable = true)
	private static void bloomlevelup$riptide(ItemStack stack, LivingEntity entity, CallbackInfoReturnable<Float> cir) {
		float original = cir.getReturnValueF();
		float strength = CombatSkill.riptideStrength(stack, entity, original);

		if (strength != original) {
			cir.setReturnValue(strength);
		}
	}
}
