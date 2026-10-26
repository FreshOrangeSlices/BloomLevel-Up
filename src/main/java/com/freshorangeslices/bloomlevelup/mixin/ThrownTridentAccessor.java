package com.freshorangeslices.bloomlevelup.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;

@Mixin(ThrownTrident.class)
public interface ThrownTridentAccessor {
	@Accessor("ID_LOYALTY")
	static EntityDataAccessor<Byte> bloomlevelup$loyalty() {
		throw new AssertionError();
	}

	@Accessor("dealtDamage")
	boolean bloomlevelup$dealtDamage();

	/** The thrown stack (a trident's, or Catch!'s weapon). */
	@Accessor("tridentItem")
	ItemStack bloomlevelup$tridentItem();

	@Accessor("tridentItem")
	void bloomlevelup$setTridentItem(ItemStack stack);
}
