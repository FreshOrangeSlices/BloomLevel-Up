package com.freshorangeslices.bloomlevelup.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** One item cooldown's start and end tick ({@code ItemCooldowns.CooldownInstance} isn't public). */
@Mixin(targets = "net.minecraft.world.item.ItemCooldowns$CooldownInstance")
public interface CooldownInstanceAccessor {
	@Accessor("startTime")
	int bloomlevelup$startTime();

	@Accessor("endTime")
	int bloomlevelup$endTime();
}
