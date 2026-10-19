package com.freshorangeslices.bloomlevelup.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Player.class)
public interface PlayerInvoker {
	/** Vanilla's crit conditions apart from attack strength (falling, not on ground/ladder/water, not sprinting...). */
	@Invoker("canCriticalAttack")
	boolean bloomlevelup$canCriticalAttack(Entity target);
}
