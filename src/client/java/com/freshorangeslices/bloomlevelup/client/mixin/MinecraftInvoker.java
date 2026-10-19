package com.freshorangeslices.bloomlevelup.client.mixin;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface MinecraftInvoker {
	/** Vanilla's left-click action (attack the entity in the crosshair, or start mining). */
	@Invoker("startAttack")
	boolean bloomlevelup$startAttack();
}
