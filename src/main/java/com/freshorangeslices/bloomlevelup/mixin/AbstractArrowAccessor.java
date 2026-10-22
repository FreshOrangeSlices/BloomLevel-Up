package com.freshorangeslices.bloomlevelup.mixin;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import net.minecraft.world.entity.projectile.arrow.AbstractArrow;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractArrow.class)
public interface AbstractArrowAccessor {
	@Invoker("setPierceLevel")
	void bloomlevelup$setPierceLevel(byte level);

	@Invoker("isInGround")
	boolean bloomlevelup$isInGround();

	/** Entities a piercing arrow already went through (it can't hit them again); null until the first. */
	@Accessor("piercingIgnoreEntityIds")
	IntOpenHashSet bloomlevelup$piercedIds();

	@Accessor("piercingIgnoreEntityIds")
	void bloomlevelup$setPiercedIds(IntOpenHashSet ids);
}
