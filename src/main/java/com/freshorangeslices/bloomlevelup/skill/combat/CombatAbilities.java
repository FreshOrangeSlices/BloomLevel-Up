package com.freshorangeslices.bloomlevelup.skill.combat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import com.freshorangeslices.bloomlevelup.config.BloomConfig;
import com.freshorangeslices.bloomlevelup.data.BloomServices;
import com.freshorangeslices.bloomlevelup.entity.ThrownWeapon;
import com.freshorangeslices.bloomlevelup.mixin.ProjectileWeaponItemInvoker;
import com.freshorangeslices.bloomlevelup.network.BloomPayloads.CombatAction;
import com.freshorangeslices.bloomlevelup.skill.combat.Weapons.Family;
import com.freshorangeslices.bloomlevelup.skill.misc.HoardersPocket;
import com.freshorangeslices.bloomlevelup.skill.misc.RuneEmbedding;
import com.freshorangeslices.bloomlevelup.stats.StatFormulas;

/**
 * The Combat Majors that need input: Charge Attack / Heavy Hands (hold attack), Lightning Summon
 * (sneak + attack with a trident, not aimed at a mob), Catch! (throw), and Assault Rifle (crossbow magazine).
 * The client only reports what the player did; the server checks the Major, weapon and costs.
 */
public final class CombatAbilities {
	private static final Map<UUID, Long> CHARGED_UNTIL = new HashMap<>();

	private CombatAbilities() {
	}

	public static void init() {
		ServerPlayConnectionEvents.DISCONNECT.register((listener, server) -> CHARGED_UNTIL.remove(listener.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> CHARGED_UNTIL.clear());

		// Wind Impact: right-clicking the ground with a mace (the block didn't do anything with the click).
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (!level.isClientSide() && hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer serverPlayer
					&& CombatSkill.windBurst(serverPlayer)) {
				return InteractionResult.SUCCESS;
			}

			return InteractionResult.PASS;
		});
	}

	private static BloomConfig config() {
		return BloomServices.config();
	}

	public static void handle(ServerPlayer player, CombatAction action) {
		if (player.isSpectator() || !player.isAlive()) {
			return;
		}

		switch (action) {
			case CHARGED -> armCharge(player);
			case SUMMON_LIGHTNING -> summonLightning(player);
			case THROW -> throwWeapon(player);
			case OPEN_POCKET -> HoardersPocket.open(player);
			case OPEN_RUNES -> RuneEmbedding.open(player);
		}
	}

	// ------------------------------------------------------------------ Charge Attack / Heavy Hands

	/** Charge Attack on axes (or Copycat), Heavy Hands on every melee weapon. */
	public static boolean canCharge(ServerPlayer player, ItemStack weapon) {
		return CombatSkill.has(player, CombatIds.CHARGE_ATTACK, Family.AXE, weapon)
				|| (CombatSkill.active(player, CombatIds.HEAVY_HANDS) && Weapons.isMeleeWeapon(weapon));
	}

	private static void armCharge(ServerPlayer player) {
		if (canCharge(player, player.getMainHandItem())) {
			CHARGED_UNTIL.put(player.getUUID(), player.level().getGameTime() + 20);
		}
	}

	/** Spends a pending charge on this full-strength swing, if there is one. */
	static boolean consumeCharge(ServerPlayer player, ItemStack weapon) {
		Long until = CHARGED_UNTIL.remove(player.getUUID());
		return until != null && player.level().getGameTime() <= until && canCharge(player, weapon);
	}

	/** Arms a charge directly (game tests). */
	public static void armChargeForTests(ServerPlayer player) {
		armCharge(player);
	}

	// ------------------------------------------------------------------ Lightning Summon

	private static void summonLightning(ServerPlayer player) {
		ItemStack weapon = player.getMainHandItem();

		if (!CombatSkill.has(player, CombatIds.LIGHTNING_SUMMON, Family.TRIDENT, weapon) || player.getCooldowns().isOnCooldown(weapon)
				|| !(player.level() instanceof ServerLevel level)) {
			return;
		}

		BloomConfig config = config();
		int cost = 0;

		// 10% of the weapon's durability per strike; never breaks the weapon.
		if (weapon.isDamageableItem()) {
			cost = Math.max(1, (int) Math.ceil(weapon.getMaxDamage() * config.lightningSummonCostRatio));

			if (weapon.getMaxDamage() - weapon.getDamageValue() <= cost) {
				player.sendSystemMessage(Component.translatable("bloom.combat.lightning_summon.worn").withStyle(ChatFormatting.RED), true);
				return;
			}
		}

		HitResult hit = player.pick(config.lightningSummonRange, 1.0F, false);

		if (hit.getType() == HitResult.Type.MISS) {
			return;
		}

		Vec3 at = hit.getLocation();
		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);

		if (bolt == null) {
			return;
		}

		bolt.setPos(at.x, at.y, at.z);
		bolt.setCause(player);
		level.addFreshEntity(bolt);

		if (cost > 0) {
			weapon.hurtAndBreak(cost, player, EquipmentSlot.MAINHAND);
		}

		player.getCooldowns().addCooldown(weapon, config.lightningSummonCooldownTicks);
	}

	// ------------------------------------------------------------------ Catch!

	/** Weapons Catch! can throw: swords, axes, maces and anything modded in those families (tridents throw anyway). */
	public static boolean throwable(ItemStack stack) {
		if (stack.isEmpty() || Weapons.is(stack, Family.TRIDENT) || Weapons.is(stack, Family.SPEAR)) {
			return false;
		}

		return Weapons.is(stack, Family.SWORD) || Weapons.is(stack, Family.AXE) || Weapons.is(stack, Family.MACE);
	}

	private static void throwWeapon(ServerPlayer player) {
		ItemStack held = player.getMainHandItem();

		if (!CombatSkill.active(player, CombatIds.CATCH) || !throwable(held) || player.getCooldowns().isOnCooldown(held)) {
			return;
		}

		Level level = player.level();

		if (!player.hasInfiniteMaterials()) {
			held.hurtWithoutBreaking(1, player);
		}

		ItemStack thrownStack = player.hasInfiniteMaterials() ? held.copy() : held.copyAndClear();
		ThrownWeapon thrown = ThrownWeapon.create(level, player, thrownStack);
		thrown.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, (float) config().catchThrowSpeed, 1.0F);
		level.addFreshEntity(thrown);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
	}

	/** A thrown weapon hits for the weapon's own damage (plus its enchantments), not a trident's 8. */
	static float thrownDamage(ServerPlayer player, ThrownWeapon thrown, float amount) {
		ItemStack weapon = thrown.getItem();
		double base = weapon.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
				.compute(Attributes.ATTACK_DAMAGE, player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE), EquipmentSlot.MAINHAND);
		double enchantments = Math.max(0.0, amount - TridentItem.BASE_DAMAGE);
		double drop = 0.0;

		// A falling throw: a mace always scales with the drop, other weapons with Death From Above.
		if (Weapons.is(weapon, Family.MACE) || CombatSkill.active(player, CombatIds.DEATH_FROM_ABOVE)) {
			drop = StatFormulas.maceFallBonus(thrown.droppedDistance());
		}

		return (float) (base + enchantments + drop);
	}

	/** A thrown mace that hits the ground triggers Wind Impact. */
	public static void thrownWeaponLanded(ThrownWeapon thrown) {
		if (thrown.level() instanceof ServerLevel level && thrown.getOwner() instanceof ServerPlayer owner
				&& Weapons.is(thrown.getItem(), Family.MACE) && CombatSkill.active(owner, CombatIds.WIND_IMPACT)) {
			CombatSkill.windImpactAt(level, owner, thrown.position());
		}
	}

	// ------------------------------------------------------------------ Assault Rifle

	/** After a normal load, keep loading until the magazine is full or the ammo runs out. */
	public static void fillMagazine(LivingEntity shooter, ItemStack crossbow) {
		if (!(shooter instanceof ServerPlayer player) || !CombatSkill.active(player, CombatIds.ASSAULT_RIFLE)
				|| !Weapons.is(crossbow, Family.CROSSBOW)) {
			return;
		}

		ChargedProjectiles current = crossbow.get(DataComponents.CHARGED_PROJECTILES);

		if (current == null || current.isEmpty()) {
			return;
		}

		List<ItemStack> loaded = new ArrayList<>(current.itemCopies().toList());
		int magazine = config().assaultRifleMagazine;

		while (loaded.size() < magazine) {
			ItemStack ammo = player.getProjectile(crossbow);

			if (ammo.isEmpty()) {
				break;
			}

			ItemStack one = ProjectileWeaponItemInvoker.bloomlevelup$useAmmo(crossbow, ammo, player, false);

			if (one.isEmpty()) {
				break;
			}

			loaded.add(one);
		}

		crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.ofNonEmpty(loaded));
	}

	/** Fires one round of a loaded magazine. Returns null to let vanilla handle the click. */
	public static InteractionResult fireFromMagazine(CrossbowItem item, Level level, Player player, InteractionHand hand) {
		ItemStack crossbow = player.getItemInHand(hand);
		ChargedProjectiles loaded = crossbow.get(DataComponents.CHARGED_PROJECTILES);

		if (loaded == null || loaded.size() <= 1 || !CombatSkill.activeAnySide(player, CombatIds.ASSAULT_RIFLE)) {
			return null;
		}

		if (level.isClientSide()) {
			return InteractionResult.CONSUME; // the server fires and syncs the remaining magazine
		}

		List<ItemStack> rounds = loaded.itemCopies().toList();
		ItemStack round = rounds.get(0);
		crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.ofNonEmpty(List.of(round)));
		item.performShooting(level, player, hand, crossbow, round.is(Items.FIREWORK_ROCKET) ? 1.6F : 3.15F, 1.0F, null);

		if (!crossbow.isEmpty() && rounds.size() > 1) {
			crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.ofNonEmpty(rounds.subList(1, rounds.size())));
		}

		return InteractionResult.CONSUME;
	}

	/** Visual for a spent charge. */
	static void chargedHitEffects(ServerLevel level, LivingEntity target) {
		level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 14, 0.4, 0.4, 0.4, 0.2);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 0.6F);
	}
}
