package com.freshorangeslices.bloomlevelup.skill.combat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;
import com.freshorangeslices.bloomlevelup.compat.BetterCombat;
import com.freshorangeslices.bloomlevelup.config.BloomConfig;
import com.freshorangeslices.bloomlevelup.data.BloomServices;
import com.freshorangeslices.bloomlevelup.effect.BloomEffects;
import com.freshorangeslices.bloomlevelup.entity.ThrownWeapon;
import com.freshorangeslices.bloomlevelup.mixin.AbstractArrowAccessor;
import com.freshorangeslices.bloomlevelup.network.BloomNetwork;
import com.freshorangeslices.bloomlevelup.network.BloomPayloads;
import com.freshorangeslices.bloomlevelup.player.BloomAttachments;
import com.freshorangeslices.bloomlevelup.player.BloomProfile;
import com.freshorangeslices.bloomlevelup.progression.ProgressionService;
import com.freshorangeslices.bloomlevelup.skill.SideBridge;
import com.freshorangeslices.bloomlevelup.skill.SkillXp;
import com.freshorangeslices.bloomlevelup.skill.combat.Weapons.Family;
import com.freshorangeslices.bloomlevelup.skill.misc.MiscSkill;
import com.freshorangeslices.bloomlevelup.stats.StatFormulas;

/**
 * Combat: XP from real damage dealt, the five Combat stats that aren't plain attributes (Critical,
 * Penetration, Looting), and the on-hit / passive Combat Majors.
 *
 * <p>Every hit flows through three hooks, so XP, kill credit, Execute and Overkill share one "real damage"
 * truth (Combat Major Interactions, sections 4 and 11):
 * <ol>
 *   <li>{@link #modifyIncoming} at the start of {@code hurt} (mixin): classifies the hit (primary swing,
 *   sweep, Aura pulse, projectile...) and adds the bonuses that count as real damage (crit, First Strike,
 *   Predator, Stored Impact...). Armor and Resistance still apply afterwards.</li>
 *   <li>{@link #afterRealDamage} at the end of {@code actuallyHurt}: health actually lost. Awards XP and runs
 *   on-hit effects; Execute finishes the target here so vanilla's own death path gives kill credit.</li>
 *   <li>{@link #onDeath} (Forge's death event, after anything that might cancel it): kill bonus, Bloodlust, Overkill.</li>
 * </ol>
 * AoE follow-ups (Batter Up!, Aura Sword, Lightning Shock, Overkill) run from a queue at the end of the tick,
 * never inside another entity's damage call.
 */
public final class CombatSkill {
	private static final TagKey<EntityType<?>> BOSSES = TagKey.create(Registries.ENTITY_TYPE, BloomLevelUp.id("bosses"));
	private static final TagKey<EntityType<?>> NO_COMBAT_XP = TagKey.create(Registries.ENTITY_TYPE, BloomLevelUp.id("no_combat_xp"));

	/** What kind of hit this is, which decides which Majors may follow it. */
	public enum HitKind {
		/** The swing's own target. */
		PRIMARY,
		/** Vanilla sword sweep or Batter Up! sweep. */
		SWEEP,
		/** Aura Sword pulse. */
		AURA,
		/** Arrow, thrown trident, other projectile. */
		PROJECTILE,
		/** A direct hit that wasn't a swing. */
		KINETIC,
		/** The mod's own secondary damage (Lightning Shock, Overkill). Never feeds weapon Majors again. */
		SECONDARY;

		boolean weaponHit() {
			return this != SECONDARY;
		}
	}

	/** One swing, recorded before vanilla resets the attack cooldown. */
	private static final class PendingAttack {
		final Entity target;
		final float strength;
		final boolean crit;
		final double fallDistance;
		final boolean sprinting;
		final boolean onGround;

		PendingAttack(Entity target, float strength, boolean crit, double fallDistance, boolean sprinting, boolean onGround) {
			this.target = target;
			this.strength = strength;
			this.crit = crit;
			this.fallDistance = fallDistance;
			this.sprinting = sprinting;
			this.onGround = onGround;
		}
	}

	/** A hit in progress on one target, between the two damage hooks. */
	private record Hit(ServerPlayer player, DamageSource source, HitKind kind, ItemStack weapon, float amount, float vanillaAmount,
			PendingAttack swing) {
	}

	/** The last hit a player landed on a target, for kill bonus / Overkill. */
	private record LastHit(UUID player, HitKind kind, boolean executed, double excess, boolean overkillSource) {
	}

	private record Stack(UUID target, int stacks, long lastTick) {
	}

	private record Deferred(long dueTick, Runnable task) {
	}

	private static final Map<UUID, PendingAttack> PENDING = new HashMap<>();
	private static final Map<LivingEntity, Hit> ACTIVE = new IdentityHashMap<>();
	private static final Map<LivingEntity, Float> HEALTH_BEFORE = new IdentityHashMap<>();
	private static final Map<LivingEntity, Float> MITIGATED = new IdentityHashMap<>();
	private static final Map<LivingEntity, LastHit> LAST_HIT = new WeakHashMap<>();
	private static final Map<LivingEntity, Map<UUID, Double>> DAMAGE_XP = new WeakHashMap<>();
	private static final Map<UUID, Stack> COMBO = new HashMap<>();
	private static final Map<UUID, Stack> PREDATOR = new HashMap<>();
	private static final Map<UUID, Stack> BLOODLUST = new HashMap<>();
	private static final Map<UUID, Integer> AURA_COUNT = new HashMap<>();
	private static final Map<UUID, Long> LIGHTNING_READY_AT = new HashMap<>();
	private static final Set<AbstractArrow> STRAIGHT_ARROWS = Collections.newSetFromMap(new WeakHashMap<>());
	/** The bow or crossbow each player's arrow (or the trident itself) came from: 1.20.1 arrows don't remember it. */
	private static final Map<Projectile, ItemStack> FIRED_WITH = new WeakHashMap<>();
	private static final List<Deferred> DEFERRED = new ArrayList<>();
	/** Set while the mod itself swings at extra targets, so those hits are classified correctly. */
	private static HitKind secondarySwing;
	private static long gameTick;

	private CombatSkill() {
	}

	public static void init() {
		MinecraftForge.EVENT_BUS.addListener((AttackEntityEvent event) -> onAttack(event.getEntity(), event.getTarget()));
		// After Last Breath and anything else that might cancel the death.
		MinecraftForge.EVENT_BUS.addListener(EventPriority.LOW, (LivingDeathEvent event) -> onDeath(event.getEntity(), event.getSource()));
		MinecraftForge.EVENT_BUS.addListener((EntityJoinLevelEvent event) -> {
			if (event.getLevel() instanceof ServerLevel level) {
				onEntityLoad(event.getEntity(), level);
			}
		});
		MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) -> {
			if (event.phase == TickEvent.Phase.END) {
				endTick(event.getServer());
			}
		});
		MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> forget(event.getEntity().getUUID()));
		MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> clearAll());
	}

	private static void forget(UUID id) {
		PENDING.remove(id);
		COMBO.remove(id);
		PREDATOR.remove(id);
		BLOODLUST.remove(id);
		AURA_COUNT.remove(id);
		LIGHTNING_READY_AT.remove(id);
	}

	private static void clearAll() {
		PENDING.clear();
		ACTIVE.clear();
		HEALTH_BEFORE.clear();
		MITIGATED.clear();
		LAST_HIT.clear();
		DAMAGE_XP.clear();
		COMBO.clear();
		PREDATOR.clear();
		BLOODLUST.clear();
		AURA_COUNT.clear();
		LIGHTNING_READY_AT.clear();
		STRAIGHT_ARROWS.clear();
		FIRED_WITH.clear();
		DEFERRED.clear();
		secondarySwing = null;
	}

	// ------------------------------------------------------------------ queries

	private static ProgressionService service() {
		return BloomServices.progression();
	}

	private static BloomConfig config() {
		return BloomServices.config();
	}

	public static boolean active(ServerPlayer player, String majorId) {
		return service().isMajorActive(BloomAttachments.profile(player), majorId);
	}

	/** Works on both sides: the server reads the profile, the client its synced copy. */
	public static boolean activeAnySide(Player player, String majorId) {
		return player instanceof ServerPlayer serverPlayer ? active(serverPlayer, majorId) : SideBridge.clientMajorActive(player, majorId);
	}

	private static int stat(ServerPlayer player, String statId) {
		return service().statValue(BloomAttachments.profile(player), statId);
	}

	/** The weapon Major Copycat is spreading right now, if any. */
	public static Optional<String> copied(ServerPlayer player) {
		return service().activeCopySource(BloomAttachments.profile(player), CombatIds.COPYCAT);
	}

	/**
	 * Whether a weapon Major applies to this weapon: it's on and the weapon is in its family, or Copycat is
	 * currently spreading exactly this Major (Copycat keeps all of the source's costs and drawbacks).
	 */
	public static boolean has(ServerPlayer player, String majorId, Family family, ItemStack weapon) {
		if (!active(player, majorId)) {
			return false;
		}

		return Weapons.is(weapon, family) || copied(player).map(majorId::equals).orElse(false);
	}

	/** {@link #has} on either side: the client reads its synced profile (local player only). */
	public static boolean hasAnySide(Player player, String majorId, Family family, ItemStack weapon) {
		if (player instanceof ServerPlayer serverPlayer) {
			return has(serverPlayer, majorId, family, weapon);
		}

		return SideBridge.clientMajorActive(player, majorId) && (Weapons.is(weapon, family) || majorId.equals(SideBridge.clientCopySource(player)));
	}

	private static boolean pvpAllowed(LivingEntity target) {
		return !(target instanceof Player) || config().combatPvp;
	}

	/** Living things a player's area effects may hit: not themselves, allies, their pets or armor stands. */
	private static boolean validVictim(ServerPlayer player, LivingEntity entity) {
		if (entity == player || !entity.isAlive() || entity.isAlliedTo(player) || entity instanceof ArmorStand) {
			return false;
		}

		if (entity instanceof OwnableEntity ownable && ownable.getOwner() == player) {
			return false;
		}

		if (entity instanceof Player other) {
			return config().combatPvp && !other.isSpectator() && !other.isCreative();
		}

		return true;
	}

	private static boolean isBoss(LivingEntity entity) {
		return entity.getType().is(BOSSES);
	}

	// ------------------------------------------------------------------ the swing (Forge's attack event: start of Player.attack)

	private static void onAttack(Player player, Entity target) {
		if (player.level().isClientSide() || !(player instanceof ServerPlayer serverPlayer) || player.isSpectator()) {
			return;
		}

		// Better Combat: Full Sweep / Batter Up! cancel its penalty for hitting several mobs (only inside its swings).
		BetterCombat.beforeAttack(serverPlayer);
		float strength = serverPlayer.getAttackStrengthScale(0.5F);
		boolean crit = strength > 0.9F && canCriticalAttack(serverPlayer, target);
		PENDING.put(serverPlayer.getUUID(), new PendingAttack(target, strength, crit, serverPlayer.fallDistance, serverPlayer.isSprinting(),
				serverPlayer.onGround()));
	}

	/** Vanilla 1.20.1's crit conditions apart from attack strength (Player.attack works them out inline). */
	public static boolean canCriticalAttack(Player player, Entity target) {
		return player.fallDistance > 0.0F && !player.onGround() && !player.onClimbable() && !player.isInWater()
				&& !player.hasEffect(MobEffects.BLINDNESS) && !player.isPassenger() && target instanceof LivingEntity && !player.isSprinting();
	}

	// ------------------------------------------------------------------ hook 1: incoming damage (mixin, hurt HEAD)

	public static float modifyIncoming(LivingEntity target, DamageSource source, float amount) {
		if (!(target.level() instanceof ServerLevel level) || !(source.getEntity() instanceof ServerPlayer player) || player == target
				|| !(amount > 0.0F)) {
			return amount;
		}

		Entity direct = source.getDirectEntity();
		HitKind kind;
		ItemStack weapon;
		PendingAttack swing = null;

		if (BloomDamage.isSecondary(source)) {
			kind = HitKind.SECONDARY;
			weapon = player.getMainHandItem();
		} else if (direct == player) {
			weapon = player.getMainHandItem();

			if (secondarySwing != null) {
				kind = secondarySwing;
			} else {
				PendingAttack pending = PENDING.get(player.getUUID());

				if (pending != null && pending.target == target) {
					kind = HitKind.PRIMARY;
					swing = pending;
				} else if (pending != null && source.is(DamageTypes.PLAYER_ATTACK)) {
					kind = HitKind.SWEEP; // vanilla sword sweep inside the same swing
					swing = pending;
				} else {
					kind = HitKind.KINETIC;
				}
			}
		} else if (direct instanceof Projectile projectile) {
			kind = HitKind.PROJECTILE;
			weapon = firedWith(projectile);
		} else {
			return amount;
		}

		float result = amount;

		if (direct instanceof ThrownWeapon thrown) {
			result = CombatAbilities.thrownDamage(player, thrown, result);

			if (thrown.isCharged()) {
				CombatAbilities.chargedHitEffects(level, target);
			}
		}

		if (kind.weaponHit() && pvpAllowed(target)) {
			result = applyBonuses(level, player, target, kind, weapon, swing, direct, result);
		}

		ACTIVE.put(target, new Hit(player, source, kind, weapon, result, amount, swing));
		return result;
	}

	private static float applyBonuses(ServerLevel level, ServerPlayer player, LivingEntity target, HitKind kind, ItemStack weapon,
			PendingAttack swing, Entity direct, float amount) {
		BloomConfig config = config();
		BloomProfile profile = BloomAttachments.profile(player);
		double add = 0.0;
		double multiplier = 1.0;
		long now = level.getGameTime();

		if (kind == HitKind.PRIMARY && swing != null) {
			boolean fullStrength = swing.strength > 0.9F;
			double critMultiplier = StatFormulas.criticalMultiplier(stat(player, CombatIds.CRITICAL));

			// Charge Attack / Heavy Hands: a held charge doubles a full-strength swing (and it can still crit).
			if (fullStrength && CombatAbilities.consumeCharge(player, weapon)) {
				multiplier *= config.chargeAttackMultiplier;
				CombatAbilities.chargedHitEffects(level, target);
				CombatAbilities.chargedSwingWear(player, weapon);

				if (BetterCombat.installed()) {
					BetterCombat.slamShockwave(level, target); // the client played a slam for it
				}
			}

			if (swing.crit) {
				multiplier *= critMultiplier / 1.5; // vanilla already applied its 1.5x
			} else if (fullStrength && has(player, CombatIds.GUARANTEED_CRITICAL, Family.AXE, weapon)) {
				multiplier *= critMultiplier;
				player.crit(target);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 1.0F);
			}

			// Death From Above: mace-style fall scaling for every melee weapon (no fall-damage safety).
			if (active(player, CombatIds.DEATH_FROM_ABOVE) && Weapons.isMeleeWeapon(weapon)) {
				add += StatFormulas.maceFallBonus(swing.fallDistance);
			}

			// Combo: player-state stacks from clean hits, lost after a few seconds without attacking.
			if (has(player, CombatIds.COMBO, Family.UNARMED, weapon)) {
				Stack combo = COMBO.get(player.getUUID());
				int stacks = combo != null && now - combo.lastTick() <= config.comboResetTicks ? combo.stacks() : 0;
				add += StatFormulas.comboBonus(stacks, config.comboPerHit, config.comboCap);

				if (fullStrength) {
					COMBO.put(player.getUUID(), new Stack(null, stacks + 1, now));
					int shown = Math.min(stacks + 1, (int) Math.ceil(config.comboCap / Math.max(1.0E-6, config.comboPerHit)));
					sendMeter(player, BloomPayloads.Meter.COMBO, shown,
							(float) StatFormulas.comboBonus(stacks + 1, config.comboPerHit, config.comboCap), config.comboResetTicks);
				}
			}
		}

		if (kind == HitKind.PROJECTILE && direct instanceof AbstractArrow arrow) {
			if (has(player, CombatIds.REINFORCED_SHOT, Family.CROSSBOW, weapon)) {
				add += config.reinforcedShotDamage;
			}

			// Marksman: properly drawn (crit) bow shots gain damage with distance.
			boolean drawn = arrow.isCritArrow() || !Weapons.is(weapon, Family.BOW);

			if (drawn && has(player, CombatIds.MARKSMAN, Family.BOW, weapon)) {
				multiplier *= 1.0 + StatFormulas.marksmanBonus(player.distanceTo(target), config.marksmanStartDistance,
						config.marksmanFullDistance, config.marksmanMaxBonus);
			}
		}

		// First Strike: the opening hit on an untouched target.
		if (active(player, CombatIds.FIRST_STRIKE) && target.getHealth() >= target.getMaxHealth()) {
			multiplier *= 1.0 + config.firstStrikeBonus;
		}

		// Predator: keep hitting the same target to ramp up (single-target hits only). With Better Combat, whose swings
		// hit several mobs at once, any mob keeps the stacks going.
		if (active(player, CombatIds.PREDATOR) && (kind == HitKind.PRIMARY || kind == HitKind.PROJECTILE)) {
			Stack predator = PREDATOR.get(player.getUUID());
			boolean same = predator != null && (BetterCombat.installed() || target.getUUID().equals(predator.target()))
					&& now - predator.lastTick() <= config.predatorResetTicks;
			int stacks = same ? predator.stacks() : 0;
			multiplier *= StatFormulas.stackMultiplier(stacks, config.predatorPerStack, config.predatorMaxStacks);
			int next = Math.min(stacks + 1, config.predatorMaxStacks);
			PREDATOR.put(player.getUUID(), new Stack(target.getUUID(), next, now));
			sendMeter(player, BloomPayloads.Meter.PREDATOR, next,
					(float) (StatFormulas.stackMultiplier(next, config.predatorPerStack, config.predatorMaxStacks) - 1.0), config.predatorResetTicks);
		}

		// Bloodlust: momentum from recent kills.
		if (active(player, CombatIds.BLOODLUST)) {
			Stack bloodlust = BLOODLUST.get(player.getUUID());

			if (bloodlust != null && now <= bloodlust.lastTick()) {
				multiplier *= StatFormulas.stackMultiplier(bloodlust.stacks(), config.bloodlustPerStack, config.bloodlustMaxStacks);
			}
		}

		return (float) ((amount + add) * multiplier);
	}

	// ------------------------------------------------------------------ hook 2: real damage (mixins, actuallyHurt HEAD/RETURN)

	public static void beforeRealDamage(LivingEntity target) {
		HEALTH_BEFORE.put(target, target.getHealth() + target.getAbsorptionAmount());
		MITIGATED.remove(target);
	}

	/** The damage left after armor, enchantments and the Armor category's layers (getDamageAfterMagicAbsorb). */
	public static void recordMitigated(LivingEntity target, float amount) {
		if (HEALTH_BEFORE.containsKey(target)) {
			MITIGATED.put(target, amount);
		}
	}

	public static void afterRealDamage(LivingEntity target, ServerLevel level, DamageSource source) {
		Float before = HEALTH_BEFORE.remove(target);
		Float mitigated = MITIGATED.remove(target);

		if (before == null) {
			return;
		}

		float real = before - (target.getHealth() + target.getAbsorptionAmount());

		Hit hit = ACTIVE.get(target);

		if (hit == null || hit.source() != source) {
			return;
		}

		ACTIVE.remove(target);
		ServerPlayer player = hit.player();

		if (real > 0.0F) {
			awardDamageXp(player, target, real, before);
		}

		double excess = mitigated == null ? 0.0 : Math.max(0.0, mitigated - before);
		boolean overkillSource = source.is(BloomDamage.OVERKILL);
		boolean executed = false;

		if (pvpAllowed(target) && real > 0.0F) {
			executed = onHitEffects(level, player, target, hit, real, before);
		}

		LAST_HIT.put(target, new LastHit(player.getUUID(), hit.kind(), executed, excess, overkillSource));
	}

	private static void awardDamageXp(ServerPlayer player, LivingEntity target, float real, float healthBefore) {
		if (target.getType().is(NO_COMBAT_XP) || (target instanceof Player && !config().combatPvpXp)
				|| (target instanceof OwnableEntity ownable && ownable.getOwner() == player)) {
			return;
		}

		double multiplier = config().combatEntityXpMultipliers.getOrDefault(BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString(), 1.0);
		double xp = StatFormulas.combatHitXp(real, healthBefore, config().combatXpPerDamage, multiplier);

		if (xp > 0.0) {
			SkillXp.award(player, SkillXp.COMBAT, xp);
			DAMAGE_XP.computeIfAbsent(target, key -> new HashMap<>()).merge(player.getUUID(), xp, Double::sum);
		}
	}

	/** Returns whether Execute finished the target. {@code before}: its health (and absorption) before this hit. */
	private static boolean onHitEffects(ServerLevel level, ServerPlayer player, LivingEntity target, Hit hit, float real, float before) {
		BloomConfig config = config();
		ItemStack weapon = hit.weapon();
		HitKind kind = hit.kind();
		ThreadLocalRandom random = ThreadLocalRandom.current();

		if (kind.weaponHit()) {
			// Armor Break: real damage only (Execute never adds fake damage to it).
			if (has(player, CombatIds.ARMOR_BREAK, Family.AXE, weapon)) {
				int wear = (int) Math.ceil(real * config.armorBreakRatio);

				for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
					ItemStack armor = target.getItemBySlot(slot);

					if (wear > 0 && !armor.isEmpty() && armor.isDamageableItem()) {
						armor.hurtAndBreak(wear, target, entity -> entity.broadcastBreakEvent(slot));
					}
				}
			}

			// Slash: fully charged sword hits (and the hits that inherit it, its sweep) can Bleed. Refreshes, never stacks.
			boolean charged = kind != HitKind.PRIMARY || (hit.swing() != null && hit.swing().strength > 0.9F);

			if (charged && target.isAlive() && has(player, CombatIds.SLASH, Family.SWORD, weapon) && random.nextDouble() < config.slashChance) {
				// No potion swirl: Bleed shows its own blood drips (BleedEffect).
				target.addEffect(new MobEffectInstance(BloomEffects.BLEED.get(), config.bleedTicks, 0, false, false, true), player);
			}

			// Disarm: primary hits only.
			if (kind == HitKind.PRIMARY && target.isAlive() && has(player, CombatIds.DISARM, Family.UNARMED, weapon)
					&& random.nextDouble() < config.disarmChance) {
				defer(0, () -> disarm(level, target));
			}

			// Misc / Become My Fuel: the weapon's imprinted effect lands on every hit (it belongs to the weapon).
			MiscSkill.applyFuel(player, target, weapon);

			// Lightning Shock: one chain at most every 2 seconds, whatever the hit was.
			if (has(player, CombatIds.LIGHTNING_SHOCK, Family.TRIDENT, weapon)
					&& level.getGameTime() >= LIGHTNING_READY_AT.getOrDefault(player.getUUID(), 0L)) {
				float shock = (float) (hit.amount() * config.lightningShockRatio);

				if (lightningChain(level, player, target, shock)) {
					LIGHTNING_READY_AT.put(player.getUUID(), level.getGameTime() + config.lightningShockCooldownTicks);
				}
			}

			if (kind == HitKind.PRIMARY && hit.swing() != null && Weapons.isMeleeWeapon(weapon)) {
				PendingAttack swing = hit.swing();

				// Batter Up!: everything that isn't a sword sweeps too (swords already do), under vanilla's sweep rules.
				// Not in a Better Combat swing: that already hits everything in the arc (and Batter Up! cancels its
				// crowd penalty instead, BetterCombat.beforeAttack).
				if (active(player, CombatIds.BATTER_UP) && !Weapons.is(weapon, Family.SWORD) && !BetterCombat.inSwing(player)
						&& swing.strength > 0.9F && !swing.crit
						&& !swing.sprinting && swing.onGround) {
					float sweep = (float) (1.0 + sweepRatio(player, weapon) * hit.vanillaAmount());
					defer(0, () -> sweep(level, player, target, sweep));
				}

				// Aura Sword: every 5th clean hit sends three sweep waves rolling outward (about 1, 2 and 3 blocks).
				if (active(player, CombatIds.AURA_SWORD)) {
					int count = AURA_COUNT.merge(player.getUUID(), 1, Integer::sum);

					if (count >= config.auraSwordHits) {
						AURA_COUNT.put(player.getUUID(), 0);
						Set<LivingEntity> struck = Collections.newSetFromMap(new IdentityHashMap<>());

						for (int wave = 0; wave < 3; wave++) {
							double inner = config.auraSwordRadius * wave / 3.0;
							double outer = config.auraSwordRadius * (wave + 1) / 3.0;
							defer(wave * config.auraSwordPulseGapTicks, () -> auraWave(level, player, inner, outer, struck));
						}
					}
				}
			}
		}

		// Execute: a finish rule, not damage. Setting health to zero lets vanilla's death path credit the kill.
		// Only a target that was already under the line when this hit landed: the hit that takes it under the line
		// doesn't execute, the next one does. Overkill's splash may push a different target under the line, so
		// secondary hits can execute too (on the next hit).
		if (target.isAlive() && target.getHealth() > 0.0F && has(player, CombatIds.EXECUTE, Family.SWORD, weapon)) {
			double threshold = isBoss(target) ? config.executeBossThreshold : config.executeThreshold;

			if (before <= target.getMaxHealth() * threshold) {
				target.setHealth(0.0F);
				level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, target.getX(), target.getY(0.5), target.getZ(), 8, 0.3, 0.3, 0.3, 0.1);
				return true;
			}
		}

		return false;
	}

	// ------------------------------------------------------------------ hook 3: death (Forge's death event)

	private static void onDeath(LivingEntity entity, DamageSource source) {
		if (entity.level().isClientSide()) {
			return;
		}

		LastHit last = LAST_HIT.remove(entity);
		Map<UUID, Double> xp = DAMAGE_XP.remove(entity);

		if (!(source.getEntity() instanceof ServerPlayer killer) || last == null || !killer.getUUID().equals(last.player())
				|| !(entity.level() instanceof ServerLevel level)) {
			return; // died to something else later: no kill bonus
		}

		// Kill bonus: half of the XP this kill took.
		if (xp != null) {
			double earned = xp.getOrDefault(killer.getUUID(), 0.0);
			SkillXp.award(killer, SkillXp.COMBAT, earned * config().combatKillBonus);
		}

		BloomConfig config = config();

		if (active(killer, CombatIds.BLOODLUST) && config.bloodlustMaxStacks > 0) {
			Stack bloodlust = BLOODLUST.get(killer.getUUID());
			long now = level.getGameTime();
			int stacks = bloodlust != null && now <= bloodlust.lastTick() ? bloodlust.stacks() : 0;
			stacks = Math.min(stacks + 1, config.bloodlustMaxStacks);
			BLOODLUST.put(killer.getUUID(), new Stack(null, stacks, now + config.bloodlustTicks));
			sendMeter(killer, BloomPayloads.Meter.BLOODLUST, stacks,
					(float) (StatFormulas.stackMultiplier(stacks, config.bloodlustPerStack, config.bloodlustMaxStacks) - 1.0), config.bloodlustTicks);
		}

		// Overkill: real excess damage from a normal kill splashes onward. Execute kills and Overkill's own kills don't.
		if (active(killer, CombatIds.OVERKILL) && !last.executed() && !last.overkillSource() && last.excess() > 0.0) {
			Vec3 at = entity.position();
			double excess = last.excess();
			defer(0, () -> overkill(level, killer, entity, at, excess));
		}
	}

	// ------------------------------------------------------------------ deferred area effects

	static void defer(int delayTicks, Runnable task) {
		DEFERRED.add(new Deferred(gameTick + Math.max(0, delayTicks), task));
	}

	static List<LivingEntity> victimsAround(ServerLevel level, ServerPlayer player, AABB box) {
		List<LivingEntity> out = new ArrayList<>();

		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box)) {
			if (validVictim(player, entity)) {
				out.add(entity);
			}
		}

		return out;
	}

	/** A player swing at an extra target, classified as {@code kind} so the right Majors follow it. */
	private static boolean swingAt(ServerLevel level, ServerPlayer player, LivingEntity victim, float damage, HitKind kind) {
		HitKind previous = secondarySwing;
		secondarySwing = kind;

		try {
			return victim.hurt(player.damageSources().playerAttack(player), damage);
		} finally {
			secondarySwing = previous;
		}
	}

	private static void sweep(ServerLevel level, ServerPlayer player, LivingEntity primary, float damage) {
		if (player.isRemoved() || player.level() != level) {
			return;
		}

		// Vanilla sweeps reach 3 blocks from you.
		double reach = 3.0;

		for (LivingEntity victim : victimsAround(level, player, primary.getBoundingBox().inflate(1.0, 0.25, 1.0))) {
			if (victim != primary && player.distanceToSqr(victim) < reach * reach && swingAt(level, player, victim, damage, HitKind.SWEEP)) {
				shove(victim, -Math.sin(Math.toRadians(player.getYRot())), Math.cos(Math.toRadians(player.getYRot())), 0.4);
			}
		}

		double dx = -Math.sin(Math.toRadians(player.getYRot()));
		double dz = Math.cos(Math.toRadians(player.getYRot()));

		level.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX() + dx, player.getY(0.5), player.getZ() + dz, 0, dx, 0.0, dz, 0.0);

		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 1.0F);
	}

	/** Players move client-side, so a server push only reaches them as an explicit velocity packet. */
	static void syncVelocity(LivingEntity victim) {
		if (victim instanceof ServerPlayer pushed) {
			pushed.connection.send(new ClientboundSetEntityMotionPacket(pushed));
		}
	}

	/** A small horizontal knockback (vanilla sweep strength 0.4) plus a little lift. */
	private static void shove(LivingEntity victim, double dirX, double dirZ, double strength) {
		Vec3 flat = new Vec3(dirX, 0.0, dirZ);

		if (flat.lengthSqr() < 1.0E-6) {
			return;
		}

		Vec3 push = flat.normalize().scale(strength);
		victim.push(push.x, 0.1, push.z);
		syncVelocity(victim);
	}

	/** One Aura Sword wave: a ring of sweeps out to {@code outer}; each target is hit by at most one wave. */
	private static void auraWave(ServerLevel level, ServerPlayer player, double inner, double outer, Set<LivingEntity> struck) {
		if (player.isRemoved() || !player.isAlive() || player.level() != level) {
			return;
		}

		float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
		double reach = outer + 0.6; // hitboxes: a mob whose edge is inside the ring counts

		for (LivingEntity victim : victimsAround(level, player, player.getBoundingBox().inflate(reach, 1.0, reach))) {
			if (player.distanceToSqr(victim) <= reach * reach && !struck.contains(victim) && swingAt(level, player, victim, damage, HitKind.AURA)) {
				struck.add(victim);
				Vec3 away = victim.position().subtract(player.position());
				shove(victim, away.x, away.z, 0.4);
			}
		}

		double ring = Math.max(0.8, outer); // the wave's edge: 1, 2, then 3 blocks out
		int points = Math.max(8, (int) Math.round(ring * 8.0));

		for (int i = 0; i < points; i++) {
			double angle = Math.PI * 2.0 * i / points;
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX() + Math.cos(angle) * ring, player.getY(0.5),
					player.getZ() + Math.sin(angle) * ring, 1, 0.0, 0.0, 0.0, 0.0);
		}

		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F,
				(float) Math.max(0.5, 1.2 - 0.15 * outer));
	}

	/** A stacking meter on the player's HUD (Bloodlust right of the hotbar, Predator and Combo left of it). */
	private static void sendMeter(ServerPlayer player, int kind, int stacks, float bonus, int ticks) {
		BloomNetwork.send(player, new BloomPayloads.Meter(kind, stacks, bonus, ticks, ticks));
	}

	/**
	 * Lightning Shock: sparks leave the struck target together (3 by default), each to a different nearby
	 * enemy, nearest first. Every spark then keeps jumping to the nearest enemy it hasn't shocked yet, 3 jumps
	 * in all; with few enemies around it bounces back between them (never the same one twice in a row). Each
	 * jump draws a streak between the two and lands a moment later (0.1 s). Needs at least one other enemy
	 * near the target; with fewer enemies than sparks, fewer sparks fly. Returns whether it fired.
	 */
	public static boolean lightningChain(ServerLevel level, ServerPlayer player, LivingEntity target, float damage) {
		BloomConfig config = config();
		double radius = config.lightningShockRadius;
		List<LivingEntity> enemies = victimsAround(level, player, target.getBoundingBox().inflate(radius));
		enemies.removeIf(entity -> entity != target && entity.distanceToSqr(target) > radius * radius);

		if (!enemies.contains(target)) {
			enemies.add(target);
		}

		List<LivingEntity> others = new ArrayList<>(enemies);
		others.remove(target);
		others.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(target)));

		if (others.isEmpty() || !(damage > 0.0F)) {
			return false;
		}

		int sparks = Math.min(config.lightningShockSparks, others.size());
		int gap = Math.max(1, config.lightningShockBounceTicks);

		for (int spark = 0; spark < sparks; spark++) {
			List<LivingEntity> path = new ArrayList<>();
			Set<LivingEntity> shocked = Collections.newSetFromMap(new IdentityHashMap<>());
			LivingEntity at = others.get(spark);
			path.add(at);
			shocked.add(at);

			for (int jump = 1; jump < config.lightningShockJumps; jump++) {
				LivingEntity from = at;
				LivingEntity next = nearest(enemies, from, entity -> entity != from && entity != target && !shocked.contains(entity));

				if (next == null) {
					next = nearest(enemies, from, entity -> entity != from);
				}

				if (next == null) {
					break;
				}

				path.add(next);
				shocked.add(next);
				at = next;
			}

			scheduleSpark(level, player, target, path, damage, gap);
		}

		return true;
	}

	/** One spark's jumps: a growing streak from the last one shocked to the next, then the shock itself. */
	private static void scheduleSpark(ServerLevel level, ServerPlayer player, LivingEntity target, List<LivingEntity> path, float damage, int gap) {
		for (int i = 0; i < path.size(); i++) {
			LivingEntity source = i == 0 ? target : path.get(i - 1);
			LivingEntity victim = path.get(i);
			Vec3 sourceStart = center(source);
			int start = i * gap;

			for (int t = 1; t < gap; t++) {
				double progress = t / (double) gap;
				defer(start + t, () -> {
					Vec3 a = source.isAlive() ? center(source) : sourceStart;
					Vec3 b = center(victim);
					sparkLine(level, a, a.add(b.subtract(a).scale(progress)));
				});
			}

			defer(start + gap, () -> {
				if (!victim.isAlive() || victim.level() != level) {
					return;
				}

				Vec3 a = source.isAlive() ? center(source) : sourceStart;
				Vec3 to = center(victim);
				sparkLine(level, a, to);
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, to.x, to.y, to.z, 16, 0.3, 0.45, 0.3, 0.25);
				level.sendParticles(ParticleTypes.END_ROD, to.x, to.y, to.z, 3, 0.2, 0.3, 0.2, 0.05);
				level.playSound(null, to.x, to.y, to.z, SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 0.25F, 1.9F);
				victim.hurt(BloomDamage.lightningShock(level, player), damage);
			});
		}
	}

	private static Vec3 center(LivingEntity entity) {
		return entity.position().add(0.0, entity.getBbHeight() * 0.6, 0.0);
	}

	private static LivingEntity nearest(List<LivingEntity> candidates, LivingEntity from, java.util.function.Predicate<LivingEntity> allowed) {
		LivingEntity best = null;
		double bestDistance = Double.MAX_VALUE;

		for (LivingEntity candidate : candidates) {
			double distance = candidate.distanceToSqr(from);

			if (allowed.test(candidate) && candidate.isAlive() && distance < bestDistance) {
				best = candidate;
				bestDistance = distance;
			}
		}

		return best;
	}

	private static void sparkLine(ServerLevel level, Vec3 from, Vec3 to) {
		Vec3 step = to.subtract(from);
		int points = Math.max(2, (int) (step.length() * 4.0));

		for (int i = 0; i <= points; i++) {
			Vec3 at = from.add(step.scale(i / (double) points));
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 1, 0.03, 0.03, 0.03, 0.0);

			if (i % 3 == 0) {
				level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}
	}

	private static void overkill(ServerLevel level, ServerPlayer player, LivingEntity dead, Vec3 at, double excess) {
		BloomConfig config = config();
		double radius = config.overkillRadius;
		List<LivingEntity> targets = victimsAround(level, player, new AABB(at, at).inflate(radius));
		targets.remove(dead);
		targets.removeIf(entity -> entity.position().distanceToSqr(at) > radius * radius);
		targets.sort(Comparator.comparingDouble(entity -> entity.position().distanceToSqr(at)));

		// Shows the splash range: a ring of crit sparks at its edge.
		int ringPoints = Math.max(12, (int) Math.round(radius * 8.0));

		for (int i = 0; i < ringPoints; i++) {
			double angle = Math.PI * 2.0 * i / ringPoints;
			level.sendParticles(ParticleTypes.CRIT, at.x + Math.cos(angle) * radius, at.y + 0.3, at.z + Math.sin(angle) * radius, 1, 0.0, 0.05, 0.0, 0.0);
		}

		if (targets.isEmpty()) {
			return;
		}

		List<LivingEntity> hit = targets.subList(0, Math.min(config.overkillMaxTargets, targets.size()));
		float each = (float) (excess * config.overkillRatio / hit.size());

		for (LivingEntity victim : hit) {
			sparkLine(level, at.add(0.0, 0.5, 0.0), victim.position().add(0.0, victim.getBbHeight() * 0.5, 0.0));
			victim.hurt(BloomDamage.overkill(level, player), each);
		}

		level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, at.x, at.y + 0.5, at.z, 10, 0.4, 0.4, 0.4, 0.2);
	}

	private static void disarm(ServerLevel level, LivingEntity target) {
		if (!target.isAlive()) {
			return;
		}

		if (target instanceof ServerPlayer victim) {
			if (!victim.getMainHandItem().isEmpty()) {
				victim.drop(true);
			}

			return;
		}

		if (target instanceof Mob mob) {
			ItemStack held = mob.getMainHandItem();

			if (held.isEmpty()) {
				return;
			}

			ItemStack dropped = held.copy();
			mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			ItemEntity item = mob.spawnAtLocation(dropped);

			if (item != null) {
				item.setPickUpDelay(60);
			}

			level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.6F, 1.4F);
		}
	}

	// ------------------------------------------------------------------ stacking meters (the stat sheet reads them)

	/** Predator stacks the next hit on the same target would use right now (0 once they've run out). */
	public static int predatorStacksNow(ServerPlayer player) {
		Stack predator = PREDATOR.get(player.getUUID());
		long now = player.level().getGameTime();
		return predator != null && now - predator.lastTick() <= config().predatorResetTicks ? predator.stacks() : 0;
	}

	/** Combo stacks the next punch would use right now. */
	public static int comboStacksNow(ServerPlayer player) {
		Stack combo = COMBO.get(player.getUUID());
		long now = player.level().getGameTime();
		return combo != null && now - combo.lastTick() <= config().comboResetTicks ? combo.stacks() : 0;
	}

	/** Bloodlust stacks right now. */
	public static int bloodlustStacksNow(ServerPlayer player) {
		Stack bloodlust = BLOODLUST.get(player.getUUID());
		return bloodlust != null && player.level().getGameTime() <= bloodlust.lastTick() ? bloodlust.stacks() : 0;
	}

	// ------------------------------------------------------------------ projectiles (Forge's join event)

	/** The bow, crossbow or trident a projectile came from (empty when unknown). */
	public static ItemStack firedWith(Projectile projectile) {
		ItemStack weapon = FIRED_WITH.get(projectile);

		if (weapon != null) {
			return weapon;
		}

		if (projectile instanceof ThrownTrident trident && !(projectile instanceof ThrownWeapon)) {
			return ((com.freshorangeslices.bloomlevelup.mixin.ThrownTridentAccessor) trident).bloomlevelup$tridentItem();
		}

		return ItemStack.EMPTY;
	}

	/**
	 * What a player just shot with: the item they were drawing (a bow), else the projectile weapon in hand (a
	 * crossbow fires from the click, main hand first).
	 */
	private static ItemStack shootingWeapon(ServerPlayer player) {
		ItemStack using = player.getUseItem();

		if (!using.isEmpty() && using.getItem() instanceof ProjectileWeaponItem) {
			return using;
		}

		for (ItemStack held : new ItemStack[] {player.getMainHandItem(), player.getOffhandItem()}) {
			if (held.getItem() instanceof ProjectileWeaponItem) {
				return held;
			}
		}

		return ItemStack.EMPTY;
	}

	private static void onEntityLoad(Entity entity, ServerLevel level) {
		if (!(entity instanceof AbstractArrow arrow) || !(arrow.getOwner() instanceof ServerPlayer player)) {
			return;
		}

		ItemStack weapon = firedWith(arrow);

		if (weapon.isEmpty() && !(arrow instanceof ThrownTrident) && arrow.tickCount == 0) {
			weapon = shootingWeapon(player).copy();
			FIRED_WITH.put(arrow, weapon);

			// Crossbow Infinity / Rainbow: the round was free, so it can't be picked back up (as with Infinity).
			if (weapon.getItem() instanceof CrossbowItem && arrow.pickup == AbstractArrow.Pickup.ALLOWED
					&& freeAmmo(weapon, ((com.freshorangeslices.bloomlevelup.mixin.AbstractArrowInvoker) arrow).bloomlevelup$getPickupItem(), player)) {
				arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
			}
		}

		if (has(player, CombatIds.PIERCING_SHOT, Family.BOW, weapon) && arrow.getPierceLevel() < config().piercingShotLevel) {
			arrow.setPierceLevel((byte) config().piercingShotLevel);
		}

		if (has(player, CombatIds.STRAIGHT_SHOT, Family.BOW, weapon)) {
			arrow.setNoGravity(true);
			STRAIGHT_ARROWS.add(arrow);
		}

		// Guaranteed Critical spread by Copycat: arrows count as fully drawn crits.
		if (copied(player).map(CombatIds.GUARANTEED_CRITICAL::equals).orElse(false)) {
			arrow.setCritArrow(true);
		}

		// Catch!: a thrown trident always comes back, as if it had Loyalty III.
		if (arrow instanceof ThrownTrident trident && !(arrow instanceof ThrownWeapon) && active(player, CombatIds.CATCH)) {
			net.minecraft.network.syncher.EntityDataAccessor<Byte> loyalty = com.freshorangeslices.bloomlevelup.mixin.ThrownTridentAccessor.bloomlevelup$loyalty();

			if (trident.getEntityData().get(loyalty) < 3) {
				trident.getEntityData().set(loyalty, (byte) 3);
			}
		}
	}

	// ------------------------------------------------------------------ hooks used by other mixins

	/** Penetration: the share of the target's armor the attacker ignores. */
	public static float penetrate(DamageSource source, LivingEntity target, float armor) {
		if (!(armor > 0.0F) || !(source.getEntity() instanceof ServerPlayer player) || player == target || !pvpAllowed(target)
				|| source.is(DamageTypeTags.BYPASSES_ARMOR)) {
			return armor;
		}

		return (float) (armor * (1.0 - StatFormulas.penetration(stat(player, CombatIds.PENETRATION))));
	}

	/** Looting: virtual Looting levels added to the player's own when loot asks for Looting. */
	public static int extraLooting(Enchantment enchantment, LivingEntity entity) {
		if (!(entity instanceof ServerPlayer player) || enchantment != Enchantments.MOB_LOOTING) {
			return 0;
		}

		double virtual = StatFormulas.virtualLooting(stat(player, CombatIds.LOOTING));
		return StatFormulas.rollsFromChance(virtual, ThreadLocalRandom.current());
	}

	/** Crossbow Infinity: plain arrows aren't used up (and can't be picked back up). Fireworks/tipped still are. */
	public static boolean freeAmmo(ItemStack weapon, ItemStack ammo, LivingEntity shooter) {
		if (!(shooter instanceof ServerPlayer player)) {
			return false;
		}

		if (Weapons.is(weapon, Family.CROSSBOW) && ammo.is(Items.ARROW) && active(player, CombatIds.CROSSBOW_INFINITY)) {
			return true;
		}

		// Misc / Rainbow: every kind of ammo, on bows and crossbows.
		return MiscSkill.rainbowFreeAmmo(weapon, ammo, player);
	}

	/**
	 * Unrestricted Riptide: sneak while releasing a trident to riptide without the enchantment, anywhere. 1.20.1
	 * spins by Riptide level, so the configured strength is used as a level (3 = Riptide III).
	 */
	public static int riptideLevel(ItemStack stack, LivingEntity entity, int original) {
		if (original > 0 || !(entity instanceof Player player) || !player.isShiftKeyDown() || !activeAnySide(player, CombatIds.UNRESTRICTED_RIPTIDE)) {
			return original;
		}

		return Math.max(1, (int) Math.round(SideBridge.config(player).unrestrictedRiptideStrength));
	}

	/**
	 * Full Sweep: a sword's sweep hits for the whole blow (vanilla's sweep at Sweeping Edge's limit); otherwise the
	 * sweep ratio Sweeping Edge gives. Used by the Player.attack mixin and by Batter Up!'s own sweep.
	 */
	public static float sweepRatio(Player player, ItemStack weapon) {
		if (player instanceof ServerPlayer serverPlayer && has(serverPlayer, CombatIds.FULL_SWEEP, Family.SWORD, weapon)) {
			return 1.0F;
		}

		return EnchantmentHelper.getSweepingDamageRatio(player);
	}

	/** Unrestricted Riptide: no water or rain needed. */
	public static boolean riptideAnywhere(Player player) {
		return activeAnySide(player, CombatIds.UNRESTRICTED_RIPTIDE);
	}

	// ------------------------------------------------------------------ ticking

	private static void endTick(MinecraftServer server) {
		gameTick++;
		runDue();
		cleanupTick();

	}

	private static void runDue() {
		if (!DEFERRED.isEmpty()) {
			List<Deferred> due = new ArrayList<>();

			for (Iterator<Deferred> it = DEFERRED.iterator(); it.hasNext();) {
				Deferred deferred = it.next();

				if (deferred.dueTick() <= gameTick) {
					due.add(deferred);
					it.remove();
				}
			}

			for (Deferred deferred : due) {
				try {
					deferred.task().run();
				} catch (RuntimeException e) {
					BloomLevelUp.LOGGER.error("Bloom Combat effect failed", e);
				}
			}
		}
	}

	private static void cleanupTick() {
		PENDING.clear();
		ACTIVE.clear();
		HEALTH_BEFORE.clear();
		MITIGATED.clear();

		for (Iterator<AbstractArrow> it = STRAIGHT_ARROWS.iterator(); it.hasNext();) {
			AbstractArrow arrow = it.next();

			boolean thrown = arrow instanceof ThrownTrident;

			if (arrow.isRemoved() || ((AbstractArrowAccessor) arrow).bloomlevelup$isInGround()) {
				it.remove();
			} else if (thrown && ((com.freshorangeslices.bloomlevelup.mixin.ThrownTridentAccessor) arrow).bloomlevelup$dealtDamage()) {
				// A thrown trident or weapon (Straight Shot through Copycat) that hit something falls or flies home normally.
				arrow.setNoGravity(false);
				it.remove();
			} else if (arrow.tickCount > config().straightShotMaxTicks) {
				// Arrows that never land vanish; thrown weapons never do (they'd be lost), they just start to fall.
				if (thrown) {
					arrow.setNoGravity(false);
				} else {
					arrow.discard();
				}

				it.remove();
			}
		}
	}

	/** Runs every queued follow-up now, as the end of the tick would (game tests). */
	public static void runQueuedForTests() {
		gameTick += 1000;
		runDue();
	}

	/** Records a swing as the attack event would, with explicit swing state (game tests: fake players never tick). */
	public static void recordSwingForTests(ServerPlayer player, Entity target, float strength, boolean crit, boolean onGround) {
		PENDING.put(player.getUUID(), new PendingAttack(target, strength, crit, player.fallDistance, false, onGround));
	}

	/** Forgets this tick's swings and hits (game tests). */
	public static void endSwingForTests() {
		cleanupTick();
	}
}
