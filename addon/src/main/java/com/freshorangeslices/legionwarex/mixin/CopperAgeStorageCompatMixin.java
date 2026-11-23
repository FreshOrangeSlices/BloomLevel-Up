package com.freshorangeslices.legionwarex.mixin;

import com.freshorangeslices.legionwarex.world.LateCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Copper Age Backport's Sophisticated Storage compat waits for common setup ({@link LateCompat}): run from Copper
 * Age's constructor, it loads Sophisticated Storage's packet handler before that mod is built and the game doesn't
 * start. Pseudo: applied only where Copper Age is installed.
 */
@Pseudo
@Mixin(targets = "com.github.smallinger.copperagebackport.compat.modules.SophisticatedStorageCompat", remap = false)
public abstract class CopperAgeStorageCompatMixin {
	@Inject(method = "init", at = @At("HEAD"), cancellable = true, remap = false)
	private void legionwarex$waitForSetup(CallbackInfo info) {
		if (LateCompat.hold(this)) {
			info.cancel();
		}
	}
}
