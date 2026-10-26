package com.freshorangeslices.bloomlevelup.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;

import com.freshorangeslices.bloomlevelup.skill.combat.CombatSkill;

/** Rainbow: a bow doesn't use up its arrows (they count as Infinity's: they can't be picked back up). */
@Mixin(ArrowItem.class)
abstract class ArrowItemMixin {
	// isInfinite is Forge's own method, so its name isn't remapped.
	@Inject(method = "isInfinite", at = @At("RETURN"), cancellable = true, remap = false)
	private void bloomlevelup$rainbow(ItemStack arrow, ItemStack bow, Player player, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && CombatSkill.freeAmmo(bow, arrow, player)) {
			cir.setReturnValue(true);
		}
	}
}
