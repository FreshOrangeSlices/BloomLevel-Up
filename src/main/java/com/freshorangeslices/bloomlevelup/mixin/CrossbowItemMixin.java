package com.freshorangeslices.bloomlevelup.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import com.freshorangeslices.bloomlevelup.skill.combat.CombatAbilities;
import com.freshorangeslices.bloomlevelup.skill.combat.CombatSkill;

/** Assault Rifle's magazine, and Crossbow Infinity / Rainbow's free rounds. */
@Mixin(CrossbowItem.class)
abstract class CrossbowItemMixin {
	@Inject(method = "tryLoadProjectiles", at = @At("RETURN"))
	private static void bloomlevelup$magazine(LivingEntity shooter, ItemStack crossbow, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) {
			CombatAbilities.fillMagazine(shooter, crossbow);
		}
	}

	@Inject(method = "use", at = @At("HEAD"), cancellable = true)
	private void bloomlevelup$fireOne(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
		InteractionResultHolder<ItemStack> result = CombatAbilities.fireFromMagazine(level, player, hand);

		if (result != null) {
			cir.setReturnValue(result);
		}
	}

	/** A free round loads like a creative player's: the ammo isn't used up. (Its arrow can't be picked up: CombatSkill.) */
	@ModifyVariable(method = "loadProjectile", at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private static boolean bloomlevelup$freeRound(boolean creative, LivingEntity shooter, ItemStack crossbow, ItemStack ammo, boolean hasAmmo,
			boolean originalCreative) {
		return creative || CombatSkill.freeAmmo(crossbow, ammo, shooter);
	}
}
