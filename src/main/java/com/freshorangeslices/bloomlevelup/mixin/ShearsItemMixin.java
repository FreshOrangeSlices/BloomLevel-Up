package com.freshorangeslices.bloomlevelup.mixin;

import java.util.List;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.IForgeShearable;

import com.freshorangeslices.bloomlevelup.skill.utility.UtilitySkill;

/** Yield on what a mob drops when sheared (1.20.1 shears drop through Forge's shearable hook, not a loot table). */
@Mixin(ShearsItem.class)
abstract class ShearsItemMixin {
	@WrapOperation(method = "interactLivingEntity", at = @At(value = "INVOKE", remap = false,
			target = "Lnet/minecraftforge/common/IForgeShearable;onSheared(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;I)Ljava/util/List;"))
	private List<ItemStack> bloomlevelup$yield(IForgeShearable target, Player player, ItemStack shears, Level level, BlockPos pos, int fortune,
			Operation<List<ItemStack>> original) {
		return UtilitySkill.shearingDrops(player, original.call(target, player, shears, level, pos, fortune));
	}
}
