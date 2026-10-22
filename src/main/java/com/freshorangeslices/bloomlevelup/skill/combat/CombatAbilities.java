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
 * The Combat Majors that need input: Charge Attack / Heavy Hands (hold the weapon-skill key), Lightning
 * Summon, Catch! (throw), Wind Impact, Assault Rifle (crossbow magazine), and Master of Arms' combos of them
 * (Charged Throw, Charged Burst, Storm Call, Gale Throw). The client only reports what the player did; the
 * server checks the Majors, weapon, charge and costs.
 */
public final class CombatAbilities {
	private static final Map<UUID, Long> CHARGED_UNTIL = new HashMap<>();
	private static final Map<UUID, Long> RIPTIDE_HINT_AT = new HashMap<>();

	private CombatAbilities() {
	}

	public static void init() {
		ServerPlayConnectionEvents.DISCONNECT.register((listener, server) -> {
			CHARGED_UNTIL.remove(listener.player.getUUID());
			RIPTIDE_HINT_AT.remove(listener.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			CHARGED_UNTIL.clear();
			RIPTIDE_HINT_AT.clear();
		});

		// Unrestricted Riptide's hint when a trident is raised.
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
				riptideHint(serverPlayer, player.getItemInHand(hand));
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
			case WIND_BURST -> CombatSkill.windBurst(player);
			case CHARGED_THROW -> chargedThrow(player);
			case CHARGED_BURST -> chargedBurst(player);
			case STORM_CALL -> stormCall(player);
			case OPEN_POCKET -> HoardersPocket.open(player);
			case OPEN_RUNES -> RuneEmbedding.open(player);
		}
	}

	/**
	 * Unrestricted Riptide is a sneak-release: when a trident without Riptide is raised without sneaking, say so
	 * on the action bar (at most every 5 minutes).
	 */
	private static void riptideHint(ServerPlayer player, ItemStack stack) {
		if (!Weapons.is(stack, Family.TRIDENT) || player.isShiftKeyDown() || !CombatSkill.active(player, CombatIds.UNRESTRICTED_RIPTIDE)
				|| net.minecraft.world.item.enchantment.EnchantmentHelper.getTridentSpinAttackStrength(stack, player) > 0.0F) {
			return;
		}

		long now = player.level().getGameTime();
		Long last = RIPTIDE_HINT_AT.get(player.getUUID());

		if (last == null || now - last >= 6000L || now < last) {
			RIPTIDE_HINT_AT.put(player.getUUID(), now);
			player.sendSystemMessage(Component.translatable("bloom.combat.riptide_hint").withStyle(ChatFormatting.GRAY), true);
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

	/**
	 * A charged swing wears the weapon by {@code chargedSwingDurabilityCost} in all: the hit's own wear (1 for a
	 * sword, 2 for an axe) plus whatever is left of that.
	 */
	static void chargedSwingWear(ServerPlayer player, ItemStack weapon) {
		if (player.hasInfiniteMaterials() || !weapon.isDamageableItem()) {
			return;
		}

		net.minecraft.world.item.component.Weapon stats = weapon.get(DataComponents.WEAPON);
		int extra = config().chargedSwingDurabilityCost - (stats == null ? 0 : stats.itemDamagePerAttack());

		if (extra > 0) {
			weapon.hurtAndBreak(extra, player, EquipmentSlot.MAINHAND);
		}
	}

	/** Arms a charge directly (game tests). */
	public static void armChargeForTests(ServerPlayer player) {
		armCharge(player);
	}

	// ------------------------------------------------------------------ Master of Arms

	/** Master of Arms owned (and on): the clashing weapon skills combine. */
	public static boolean masterOfArms(ServerPlayer player) {
		return CombatSkill.active(player, CombatIds.MASTER_OF_ARMS);
	}

	/**
	 * A combo's charge: Master of Arms, a charge Major for this weapon, and a swing that has recovered (the
	 * client only sends a charged combo after a full hold, so this just stops a client from skipping it).
	 */
	private static boolean chargedCombo(ServerPlayer player, ItemStack weapon) {
		return masterOfArms(player) && canCharge(player, weapon) && player.getAttackStrengthScale(0.5F) >= 0.9F;
	}

	/**
	 * Charged Throw: Charge Attack / Heavy Hands + Catch!. Double damage, faster, and it goes through mobs. A
	 * charged mace throw with Wind Impact is a Gale Throw (see {@link #thrownWeaponLanded}).
	 */
	private static void chargedThrow(ServerPlayer player) {
		if (chargedCombo(player, player.getMainHandItem()) && throwWeapon(player, true)) {
			player.resetAttackStrengthTicker();
		}
	}

	/** Charged Burst: Charge Attack / Heavy Hands + Wind Impact. A bigger launch and push. */
	private static void chargedBurst(ServerPlayer player) {
		if (chargedCombo(player, player.getMainHandItem()) && CombatSkill.windBurst(player, true)) {
			player.resetAttackStrengthTicker();
		}
	}

	/** Storm Call: Heavy Hands + Lightning Summon. Several bolts around where you aim. */
	private static void stormCall(ServerPlayer player) {
		if (chargedCombo(player, player.getMainHandItem()) && lightning(player, true)) {
			player.resetAttackStrengthTicker();
		}
	}

	/**
	 * Gale Throw: a Charged Throw of a mace with Wind Impact (Heavy Hands + Catch! + Wind Impact). Where it lands
	 * it first pulls mobs in from around, then bursts bigger than a normal Wind Impact and throws them out.
	 */
	private static void galeLanding(ServerLevel level, ServerPlayer owner, Vec3 at) {
		BloomConfig config = config();
		double radius = config.galeThrowPullRadius;

		for (LivingEntity victim : CombatSkill.victimsAround(level, owner, new net.minecraft.world.phys.AABB(at, at).inflate(radius))) {
			Vec3 toward = at.subtract(victim.position());
			Vec3 flat = new Vec3(toward.x, 0.0, toward.z);
			double distance = flat.length();

			if (distance > radius || distance < 0.75) {
				continue;
			}

			// Stronger the farther out, so everything ends up near the middle.
			Vec3 pull = flat.normalize().scale(config.galeThrowPullStrength * Math.min(1.0, distance / 3.0));
			victim.push(pull.x, 0.25, pull.z);
			CombatSkill.syncVelocity(victim);
		}

		// The inhale: a ring of gusts at the edge, drawn in.
		int points = Math.max(12, (int) Math.round(radius * 4.0));

		for (int i = 0; i < points; i++) {
			double angle = 2.0 * Math.PI * i / points;
			double x = at.x + Math.cos(angle) * radius;
			double z = at.z + Math.sin(angle) * radius;
			level.sendParticles(ParticleTypes.GUST, x, at.y + 0.4, z, 1, 0.0, 0.0, 0.0, 0.0);
		}

		level.playSound(null, at.x, at.y, at.z, SoundEvents.BREEZE_INHALE, SoundSource.PLAYERS, 1.5F, 0.8F);

		CombatSkill.defer(config.galeThrowPullTicks, () -> {
			if (owner.level() != level) {
				return;
			}

			CombatSkill.windBlast(level, at, config.galeThrowBurstScale);
			CombatSkill.windImpactAt(level, owner, at, config.galeThrowBurstScale, config.galeThrowBurstScale);
		});
	}

	// ------------------------------------------------------------------ Lightning Summon

	private static void summonLightning(ServerPlayer player) {
		lightning(player, false);
	}

	/**
	 * Lightning where the player aims: one bolt, or a Storm Call's ring of bolts around the aim point (which
	 * costs and cools down {@code stormCallCostMultiplier} times as much). Returns whether it struck.
	 */
	private static boolean lightning(ServerPlayer player, boolean storm) {
		ItemStack weapon = player.getMainHandItem();

		if (!CombatSkill.has(player, CombatIds.LIGHTNING_SUMMON, Family.TRIDENT, weapon) || player.getCooldowns().isOnCooldown(weapon)
				|| !(player.level() instanceof ServerLevel level)) {
			return false;
		}

		BloomConfig config = config();
		double scale = storm ? config.stormCallCostMultiplier : 1.0;
		int cost = 0;

		// 10% of the weapon's durability per strike; never breaks the weapon.
		if (weapon.isDamageableItem()) {
			cost = Math.max(1, (int) Math.ceil(weapon.getMaxDamage() * config.lightningSummonCostRatio * scale));

			if (weapon.getMaxDamage() - weapon.getDamageValue() <= cost) {
				player.sendSystemMessage(Component.translatable("bloom.combat.lightning_summon.worn").withStyle(ChatFormatting.RED), true);
				return false;
			}
		}

		HitResult hit = player.pick(config.lightningSummonRange, 1.0F, false);

		if (hit.getType() == HitResult.Type.MISS) {
			return false;
		}

		Vec3 at = hit.getLocation();

		if (storm) {
			// Evenly around the aim point, a random turn each time, one after another.
			double turn = player.getRandom().nextDouble() * 2.0 * Math.PI;

			for (int i = 0; i < config.stormCallBolts; i++) {
				double angle = turn + 2.0 * Math.PI * i / config.stormCallBolts;
				Vec3 point = at.add(Math.cos(angle) * config.stormCallRadius, 0.0, Math.sin(angle) * config.stormCallRadius);
				CombatSkill.defer(i * config.stormCallGapTicks, () -> strike(level, player, point));
			}
		} else {
			strike(level, player, at);
		}

		if (cost > 0) {
			weapon.hurtAndBreak(cost, player, EquipmentSlot.MAINHAND);
		}

		player.getCooldowns().addCooldown(weapon, (int) Math.round(config.lightningSummonCooldownTicks * scale));
		return true;
	}

	private static void strike(ServerLevel level, ServerPlayer player, Vec3 at) {
		if (player.level() != level) {
			return;
		}

		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);

		if (bolt != null) {
			bolt.setPos(at.x, at.y, at.z);
			bolt.setCause(player);
			level.addFreshEntity(bolt);
		}
	}

	// ------------------------------------------------------------------ Catch!

	/**
	 * Weapons Catch! can throw: swords, axes, maces, spears and anything modded in those families. Tridents
	 * throw on their own (Catch! makes them come back instead, see CombatSkill).
	 */
	public static boolean throwable(ItemStack stack) {
		if (stack.isEmpty() || Weapons.is(stack, Family.TRIDENT)) {
			return false;
		}

		return Weapons.is(stack, Family.SWORD) || Weapons.is(stack, Family.AXE) || Weapons.is(stack, Family.MACE)
				|| Weapons.is(stack, Family.SPEAR);
	}

	private static void throwWeapon(ServerPlayer player) {
		throwWeapon(player, false);
	}

	/** Throws the held weapon; {@code charged} makes it a Charged Throw. Returns whether it was thrown. */
	private static boolean throwWeapon(ServerPlayer player, boolean charged) {
		ItemStack held = player.getMainHandItem();

		if (!CombatSkill.active(player, CombatIds.CATCH) || !throwable(held) || player.getCooldowns().isOnCooldown(held)) {
			return false;
		}

		BloomConfig config = config();

		Level level = player.level();
		int wear = charged ? config.chargedThrowDurabilityCost : config.catchThrowDurabilityCost;

		// A throw never breaks the weapon mid-air: one too worn to pay for it isn't thrown.
		if (!player.hasInfiniteMaterials() && held.isDamageableItem() && wear > 0) {
			if (held.getMaxDamage() - held.getDamageValue() <= wear) {
				player.sendSystemMessage(Component.translatable("bloom.combat.catch.worn").withStyle(ChatFormatting.RED), true);
				return false;
			}

			held.hurtWithoutBreaking(wear, player);
		}

		ItemStack thrownStack = player.hasInfiniteMaterials() ? held.copy() : held.copyAndClear();
		ThrownWeapon thrown = ThrownWeapon.create(level, player, thrownStack);
		double speed = config.catchThrowSpeed;

		if (charged) {
			thrown.charge((float) config.chargeAttackMultiplier, config.chargedThrowPierce);
			speed *= config.chargedThrowSpeedMultiplier;
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 0.6F);
		}

		thrown.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, (float) speed, 1.0F);
		level.addFreshEntity(thrown);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
		return true;
	}

	/** Throws the held weapon now; a charged throw skips the swing-recovery check (game tests: fake players never tick). */
	public static boolean throwForTests(ServerPlayer player, boolean charged) {
		if (charged) {
			return masterOfArms(player) && canCharge(player, player.getMainHandItem()) && throwWeapon(player, true);
		}

		return throwWeapon(player, false);
	}

	/** Calls lightning now (game tests). */
	public static boolean lightningForTests(ServerPlayer player, boolean storm) {
		return lightning(player, storm);
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

		return (float) ((base + enchantments + drop) * thrown.damageMultiplier());
	}

	/** A thrown mace that hits the ground triggers Wind Impact; a charged one (Master of Arms) is a Gale Throw. */
	public static void thrownWeaponLanded(ThrownWeapon thrown) {
		if (thrown.level() instanceof ServerLevel level && thrown.getOwner() instanceof ServerPlayer owner
				&& Weapons.is(thrown.getItem(), Family.MACE) && CombatSkill.active(owner, CombatIds.WIND_IMPACT)) {
			if (thrown.isCharged() && masterOfArms(owner)) {
				galeLanding(level, owner, thrown.position());
			} else {
				CombatSkill.windImpactAt(level, owner, thrown.position());
			}
		}
	}

	/** A thrown mace landing at a point (game tests). */
	public static void galeLandingForTests(ServerLevel level, ServerPlayer owner, Vec3 at) {
		galeLanding(level, owner, at);
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
