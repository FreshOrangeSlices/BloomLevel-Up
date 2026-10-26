package com.freshorangeslices.bloomlevelup.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.freshorangeslices.bloomlevelup.skill.utility.UtilitySkill;

/**
 * Remembers XP/Yield-relevant blocks a player places, so breaking them again gives no XP or Yield. Only blocks placed
 * from a block item: Forge's place event also fires for stripped logs, tilled dirt and bone-mealed trees.
 */
@Mixin(BlockItem.class)
abstract class BlockItemMixin {
	@Inject(method = "placeBlock", at = @At("RETURN"))
	private void bloomlevelup$trackPlaced(BlockPlaceContext context, BlockState state, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && context.getPlayer() != null && context.getLevel() instanceof ServerLevel level) {
			UtilitySkill.onPlaced(level, context.getClickedPos());
		}
	}
}
