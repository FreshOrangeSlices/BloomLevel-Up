package com.freshorangeslices.bloomlevelup.item;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;

/** Item data components the mod adds. */
public final class BloomComponents {
	/** Become My Fuel: the harmful effect imprinted on a weapon. */
	public static final DataComponentType<FuelImprint> FUEL = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, BloomLevelUp.id("fuel"),
			DataComponentType.<FuelImprint>builder().persistent(FuelImprint.CODEC).networkSynchronized(FuelImprint.STREAM_CODEC).build());

	private BloomComponents() {
	}

	/** Loads the class so the components register during mod init. */
	public static void init() {
	}
}
