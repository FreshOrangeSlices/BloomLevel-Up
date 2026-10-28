package com.freshorangeslices.bloomlevelup.client.mixin;

import net.minecraft.client.gui.screens.Screen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.freshorangeslices.bloomlevelup.client.debug.TooltipDump;

/** Shift and Ctrl read as held while the tooltip dump reads a weapon's tooltip that way (a pack tool, off in play). */
@Mixin(Screen.class)
abstract class ScreenModifierKeysMixin {
	@Inject(method = "hasShiftDown", at = @At("HEAD"), cancellable = true)
	private static void bloomlevelup$tooltipDumpShift(CallbackInfoReturnable<Boolean> cir) {
		if (TooltipDump.shiftDown) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "hasControlDown", at = @At("HEAD"), cancellable = true)
	private static void bloomlevelup$tooltipDumpControl(CallbackInfoReturnable<Boolean> cir) {
		if (TooltipDump.controlDown) {
			cir.setReturnValue(true);
		}
	}
}
