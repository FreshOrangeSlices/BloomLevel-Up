package com.freshorangeslices.bloomlevelup.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ProjectileWeaponItem.class)
public interface ProjectileWeaponItemInvoker {
	/** Takes one projectile from the shooter's ammo, the way loading a crossbow does. */
	@Invoker("useAmmo")
	static ItemStack bloomlevelup$useAmmo(ItemStack weapon, ItemStack ammo, LivingEntity shooter, boolean intangible) {
		throw new AssertionError();
	}
}
