package com.freshorangeslices.bloomlevelup.mixin;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.freshorangeslices.bloomlevelup.skill.combat.CombatAbilities;

/** Assault Rifle: load a full magazine, then fire it one shot per click (hold to keep firing). */
@Mixin(CrossbowItem.class)
abstract class CrossbowItemMixin {
	@Inject(method = "tryLoadProjectiles", at = @At("RETURN"))
	private static void bloomlevelup$magazine(LivingEntity shooter, ItemStack crossbow, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) {
			CombatAbilities.fillMagazine(shooter, crossbow);
		}
	}

	@Inject(method = "use", at = @At("HEAD"), cancellable = true)
	private void bloomlevelup$fireOne(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		InteractionResult result = CombatAbilities.fireFromMagazine((CrossbowItem) (Object) this, level, player, hand);

		if (result != null) {
			cir.setReturnValue(result);
		}
	}
}
