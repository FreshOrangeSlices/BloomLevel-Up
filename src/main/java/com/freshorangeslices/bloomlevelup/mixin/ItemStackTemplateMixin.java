package com.freshorangeslices.bloomlevelup.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import net.minecraft.world.item.ItemStackTemplate;

import com.freshorangeslices.bloomlevelup.skill.misc.StackOverflow;

/** Stack Overflow: container contents (Hoarder's Pocket) save stacks of up to 300 too. */
@Mixin(ItemStackTemplate.class)
abstract class ItemStackTemplateMixin {
	@ModifyArg(method = "lambda$static$0", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ExtraCodecs;intRange(II)Lcom/mojang/serialization/Codec;"),
			index = 1)
	private static int bloomlevelup$savedCountRange(int max) {
		return Math.max(max, StackOverflow.HARD_CAP);
	}
}
