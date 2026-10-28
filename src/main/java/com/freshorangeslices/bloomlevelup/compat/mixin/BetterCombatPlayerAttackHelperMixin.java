package com.freshorangeslices.bloomlevelup.compat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.logic.PlayerAttackHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.freshorangeslices.bloomlevelup.compat.BetterCombatMoves;

/**
 * Better Combat picks every swing here, on the client (animation, wind-up, targets) and on the server (damage, sound):
 * with Batter Up!, a main-hand weapon that pokes or slams swings the sword's combo instead (see
 * {@link BetterCombatMoves}). The weapon's other data (two-handed, reach, pose) is read elsewhere and stays its own.
 * The one mixin into Better Combat (its own config, applied only when Better Combat is installed).
 */
@Mixin(value = PlayerAttackHelper.class, remap = false)
abstract class BetterCombatPlayerAttackHelperMixin {
	@WrapOperation(method = "getCurrentAttack", at = @At(value = "INVOKE",
			target = "Lnet/bettercombat/logic/WeaponRegistry;getAttributes(Lnet/minecraft/world/item/ItemStack;)Lnet/bettercombat/api/WeaponAttributes;"))
	private static WeaponAttributes bloomlevelup$batterUpMoves(ItemStack stack, Operation<WeaponAttributes> original, Player player,
			int comboCount) {
		WeaponAttributes attributes = original.call(stack);
		return player != null && stack == player.getMainHandItem() ? BetterCombatMoves.forPlayer(player, stack, attributes) : attributes;
	}
}
