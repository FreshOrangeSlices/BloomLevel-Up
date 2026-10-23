package com.freshorangeslices.bloomlevelup.mixin;

import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Entity.class)
public interface EntityAccessor {
	/** Vanilla's damage cooldown (private since 26.x). */
	@Accessor("invulnerableTime")
	int bloomlevelup$getInvulnerableTime();
}
