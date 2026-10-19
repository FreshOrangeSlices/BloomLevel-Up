package com.freshorangeslices.bloomlevelup.effect;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;

/** The mod's own status effects. */
public final class BloomEffects {
	public static final Holder<MobEffect> BLEED = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, BloomLevelUp.id("bleed"), new BleedEffect());

	private BloomEffects() {
	}

	/** Loads the class so the effects register during mod init. */
	public static void init() {
	}
}
