package com.freshorangeslices.bloomlevelup.client.mixin;

import java.io.File;

import net.minecraft.client.Options;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.freshorangeslices.bloomlevelup.client.PackDefaultsHook;

@Mixin(Options.class)
abstract class OptionsMixin {
	@Shadow
	@Final
	private File optionsFile;

	/**
	 * The Bloom Pack's first-launch setup (its resource packs on, in order), written into options.txt just before the
	 * game first reads it. Forge makes the options before it loads any mod, so this is the earliest point a mod has.
	 * {@code load(boolean)} is Forge's; its "limited" pass only re-reads key bindings once mods are loaded.
	 */
	@Inject(method = "load(Z)V", at = @At("HEAD"), remap = false)
	private void bloomlevelup$packDefaults(boolean limited, CallbackInfo ci) {
		if (!limited) {
			PackDefaultsHook.apply(this.optionsFile.toPath());
		}
	}
}
