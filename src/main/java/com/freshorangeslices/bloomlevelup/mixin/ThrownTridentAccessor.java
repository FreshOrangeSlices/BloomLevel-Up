package com.freshorangeslices.bloomlevelup.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ThrownTrident.class)
public interface ThrownTridentAccessor {
	@Accessor("ID_LOYALTY")
	static EntityDataAccessor<Byte> bloomlevelup$loyalty() {
		throw new AssertionError();
	}

	@Accessor("dealtDamage")
	boolean bloomlevelup$dealtDamage();
}
