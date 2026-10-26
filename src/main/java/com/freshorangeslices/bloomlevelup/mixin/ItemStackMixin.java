package com.freshorangeslices.bloomlevelup.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import com.freshorangeslices.bloomlevelup.skill.DurabilitySaves;
import com.freshorangeslices.bloomlevelup.skill.misc.StackOverflow;

@Mixin(ItemStack.class)
abstract class ItemStackMixin {
	@Shadow
	private int count;

	/** Our backup durability saves (Unbreaking rolls each point too, so where in the method they go doesn't matter). */
	@ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private int bloomlevelup$backupDurabilitySave(int amount, int originalAmount, RandomSource random, ServerPlayer player) {
		return amount > 0 && player != null ? DurabilitySaves.adjust((ItemStack) (Object) this, amount, player) : amount;
	}

	/** Stack Overflow: a stack over 127 saves its count as an int (vanilla writes a byte, which would wrap). */
	@Inject(method = "save", at = @At("RETURN"))
	private void bloomlevelup$saveBigCount(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
		if (this.count > Byte.MAX_VALUE) {
			tag.putInt("Count", Math.min(this.count, StackOverflow.HARD_CAP));
		}
	}

	/** ...and reads it back (vanilla reads the count as a byte). */
	@Inject(method = "<init>(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("RETURN"))
	private void bloomlevelup$loadBigCount(CompoundTag tag, CallbackInfo ci) {
		if (tag.contains("Count", Tag.TAG_INT)) {
			int saved = tag.getInt("Count");

			if (saved > Byte.MAX_VALUE) {
				this.count = Math.min(saved, StackOverflow.HARD_CAP);
			}
		}
	}
}
