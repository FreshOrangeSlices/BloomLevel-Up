package com.freshorangeslices.bloomlevelup.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.BlockHitResult;

import com.freshorangeslices.bloomlevelup.mixin.ThrownTridentAccessor;
import com.freshorangeslices.bloomlevelup.skill.combat.CombatAbilities;

/**
 * Catch!: any eligible weapon thrown like a trident. It always comes back (as if it had Loyalty III) and
 * hits for the weapon's own damage instead of the trident's.
 */
public class ThrownWeapon extends ThrownTrident implements ItemSupplier {
	private double startY;

	public ThrownWeapon(EntityType<? extends ThrownWeapon> type, Level level) {
		super(type, level);
	}

	public static ThrownWeapon create(Level level, LivingEntity owner, ItemStack weapon) {
		ThrownWeapon thrown = new ThrownWeapon(BloomEntities.THROWN_WEAPON, level);
		thrown.setOwner(owner);
		thrown.setPos(owner.getX(), owner.getEyeY() - 0.1, owner.getZ());
		thrown.setPickupItemStack(weapon);
		thrown.startY = thrown.getY();
		thrown.pickup = owner instanceof Player player && player.hasInfiniteMaterials() ? AbstractArrow.Pickup.CREATIVE_ONLY : AbstractArrow.Pickup.ALLOWED;
		thrown.makeLoyal();
		return thrown;
	}

	private void makeLoyal() {
		this.getEntityData().set(ThrownTridentAccessor.bloomlevelup$loyalty(), (byte) 3);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		makeLoyal();
		startY = this.getY();
	}

	@Override
	public ItemStack getItem() {
		return this.getPickupItemStackOrigin();
	}

	/** How far it has dropped since it was thrown (Death From Above / thrown mace bonus). */
	public double droppedDistance() {
		return Math.max(0.0, startY - this.getY());
	}

	@Override
	protected void onHitBlock(BlockHitResult hit) {
		super.onHitBlock(hit);
		CombatAbilities.thrownWeaponLanded(this);
	}
}
