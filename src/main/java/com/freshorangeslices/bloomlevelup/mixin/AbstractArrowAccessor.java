package com.freshorangeslices.bloomlevelup.mixin;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.entity.projectile.AbstractArrow;

@Mixin(AbstractArrow.class)
public interface AbstractArrowAccessor {
	@Accessor("inGround")
	boolean bloomlevelup$isInGround();

	/** Entities a piercing arrow already went through (it can't hit them again); null until the first. */
	@Accessor("piercingIgnoreEntityIds")
	IntOpenHashSet bloomlevelup$piercedIds();

	@Accessor("piercingIgnoreEntityIds")
	void bloomlevelup$setPiercedIds(IntOpenHashSet ids);
}
