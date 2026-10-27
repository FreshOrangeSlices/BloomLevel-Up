package com.freshorangeslices.bloomlevelup.mixin;

import java.util.Map;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads how long an item's cooldown was set for (Fitting Armor / Medium's shorter shield disable). */
@Mixin(ItemCooldowns.class)
public interface ItemCooldownsAccessor {
	@Accessor("cooldowns")
	Map<Item, Object> bloomlevelup$cooldowns();
}
