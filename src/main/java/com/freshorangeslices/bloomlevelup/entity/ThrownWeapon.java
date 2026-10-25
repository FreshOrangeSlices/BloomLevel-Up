package com.freshorangeslices.bloomlevelup.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;

import com.freshorangeslices.bloomlevelup.mixin.ThrownTridentAccessor;
import com.freshorangeslices.bloomlevelup.skill.combat.CombatAbilities;

/**
 * Catch!: any eligible weapon thrown like a trident. It always comes back (as if it had Loyalty III) and
 * hits for the weapon's own damage instead of the trident's. A Charged Throw (Master of Arms) hits harder
 * and pierces.
 */
public class ThrownWeapon extends ThrownTrident implements ItemSupplier {
	/** The thrown item, synced so the client draws the real weapon (its own copy otherwise says "trident"). */
	private static final EntityDataAccessor<ItemStack> DATA_ITEM = SynchedEntityData.defineId(ThrownWeapon.class, EntityDataSerializers.ITEM_STACK);
	private double startY;
	/** Damage multiplier of a Charged Throw (1 for a normal throw). */
	private float damageMultiplier = 1.0F;

	public ThrownWeapon(EntityType<? extends ThrownWeapon> type, Level level) {
		super(type, level);
	}

	public static ThrownWeapon create(Level level, LivingEntity owner, ItemStack weapon) {
		ThrownWeapon thrown = new ThrownWeapon(BloomEntities.THROWN_WEAPON, level);
		thrown.setOwner(owner);
		thrown.setPos(owner.getX(), owner.getEyeY() - 0.1, owner.getZ());
		thrown.setPickupItemStack(weapon);
		thrown.getEntityData().set(DATA_ITEM, weapon.copy());
		thrown.startY = thrown.getY();
		thrown.pickup = owner instanceof Player player && player.hasInfiniteMaterials() ? AbstractArrow.Pickup.CREATIVE_ONLY : AbstractArrow.Pickup.ALLOWED;
		thrown.makeLoyal();
		return thrown;
	}

	/** Makes this a Charged Throw: more damage, and it goes through mobs (as many as {@code pierce}). */
	public void charge(float multiplier, int pierce) {
		this.damageMultiplier = Math.max(1.0F, multiplier);

		if (pierce > this.getPierceLevel()) {
			((com.freshorangeslices.bloomlevelup.mixin.AbstractArrowAccessor) this).bloomlevelup$setPierceLevel((byte) Math.min(127, pierce));
		}
	}

	public boolean isCharged() {
		return damageMultiplier > 1.0F;
	}

	public float damageMultiplier() {
		return damageMultiplier;
	}

	private void makeLoyal() {
		this.getEntityData().set(ThrownTridentAccessor.bloomlevelup$loyalty(), (byte) 3);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_ITEM, ItemStack.EMPTY);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putFloat("bloom_damage_multiplier", damageMultiplier);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		damageMultiplier = Math.max(1.0F, input.getFloatOr("bloom_damage_multiplier", 1.0F));
		makeLoyal();
		startY = this.getY();
		this.getEntityData().set(DATA_ITEM, this.getPickupItemStackOrigin().copy());
	}

	/** The thrown weapon: the server's real stack, the client's synced copy. */
	@Override
	public ItemStack getItem() {
		if (this.level().isClientSide()) {
			ItemStack synced = this.getEntityData().get(DATA_ITEM);
			return synced.isEmpty() ? this.getPickupItemStackOrigin() : synced;
		}

		return this.getPickupItemStackOrigin();
	}

	/** The copy of the weapon the client draws. */
	public ItemStack syncedItem() {
		return this.getEntityData().get(DATA_ITEM);
	}

	/** Whether it's stuck in a block (the renderer stops its spin). */
	public boolean stuck() {
		return this.isInGround();
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
