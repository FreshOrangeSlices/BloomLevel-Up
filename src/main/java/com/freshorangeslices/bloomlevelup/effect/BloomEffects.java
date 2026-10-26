package com.freshorangeslices.bloomlevelup.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;

/** The mod's own status effects. */
public final class BloomEffects {
	private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, BloomLevelUp.MOD_ID);

	public static final RegistryObject<MobEffect> BLEED = EFFECTS.register("bleed", BleedEffect::new);

	private BloomEffects() {
	}

	public static void register(IEventBus modBus) {
		EFFECTS.register(modBus);
	}
}
