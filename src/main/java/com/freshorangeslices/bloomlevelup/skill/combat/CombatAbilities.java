package com.freshorangeslices.bloomlevelup.skill.combat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

import com.freshorangeslices.bloomlevelup.compat.BetterCombat;
import com.freshorangeslices.bloomlevelup.config.BloomConfig;
import com.freshorangeslices.bloomlevelup.data.BloomServices;
import com.freshorangeslices.bloomlevelup.entity.ThrownWeapon;
import com.freshorangeslices.bloomlevelup.registry.BloomTags;
import com.freshorangeslices.bloomlevelup.network.BloomPayloads;
import com.freshorangeslices.bloomlevelup.network.BloomPayloads.CombatAction;
import com.freshorangeslices.bloomlevelup.skill.combat.Weapons.Family;
import com.freshorangeslices.bloomlevelup.skill.misc.HoardersPocket;
import com.freshorangeslices.bloomlevelup.skill.misc.RuneEmbedding;
import com.freshorangeslices.bloomlevelup.stats.StatFormulas;

/**
 * The Combat Majors that need input: Charge Attack / Heavy Hands (hold the weapon-skill key), Lightning
 * Summon, Catch! (throw), Assault Rifle (crossbow magazine), and Master of Arms' combos of them (Charged Throw,
 * Storm Call). The client only reports what the player did; the
 * server checks the Majors, weapon, charge and costs.
 */
public final class CombatAbilities {
	private static final Map<UUID, Long> CHARGED_UNTIL = new HashMap<>();
	private static final Map<UUID, Long> RIPTIDE_HINT_AT = new HashMap<>();
	/**
	 * Bloom's own skill cooldowns (game time when each is ready again, by meter kind), kept apart from the item
	 * cooldowns other mods put on the weapon (the RPG Series' weapon skills), so neither blocks the other.
	 */
	private static final Map<UUID, Map<Integer, Long>> READY_AT = new HashMap<>();

	private CombatAbilities() {
	}

	public static void init() {
		MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
			CHARGED_UNTIL.remove(event.getEntity().getUUID());
			RIPTIDE_HINT_AT.remove(event.getEntity().getUUID());
			READY_AT.remove(event.getEntity().getUUID());
		});
		MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
			CHARGED_UNTIL.clear();
			RIPTIDE_HINT_AT.clear();
			READY_AT.clear();
		});

		// Unrestricted Riptide's hint when a trident is raised.
		MinecraftForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickItem event) -> {
			if (event.getEntity() instanceof ServerPlayer serverPlayer) {
				riptideHint(serverPlayer, event.getItemStack());
			}
		});
	}

	private static BloomConfig config() {
		return BloomServices.config();
	}

	/** Whether one of Bloom's skills (a {@link BloomPayloads.Meter} kind) is still cooling down. */
	public static boolean coolingDown(ServerPlayer player, int kind) {
		Long ready = READY_AT.getOrDefault(player.getUUID(), Map.of()).get(kind);
		return ready != null && player.level().getGameTime() < ready;
	}

	/** Starts one of Bloom's skill cooldowns and shows it on the player's HUD. */
	public static void coolDown(ServerPlayer player, int kind, int ticks) {
		if (ticks <= 0) {
			return;
		}

		READY_AT.computeIfAbsent(player.getUUID(), key -> new HashMap<>()).put(kind, player.level().getGameTime() + ticks);
		CombatSkill.sendMeter(player, kind, 1, 0.0F, ticks);
	}

	/** Ends every Bloom skill cooldown (game tests). */
	public static void clearCooldownsForTests(ServerPlayer player) {
		READY_AT.remove(player.getUUID());
	}

	public static void handle(ServerPlayer player, CombatAction action) {
		if (player.isSpectator() || !player.isAlive()) {
			return;
		}

		switch (action) {
			case CHARGED -> armCharge(player);
			case SUMMON_LIGHTNING -> summonLightning(player);
			case THROW -> throwWeapon(player);
			case CHARGED_THROW -> chargedThrow(player);
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
				|| EnchantmentHelper.getRiptide(stack) > 0) {
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
			// Better Combat's swing lands after its wind-up (up to about a second for heavy weapons): wait for it.
			CHARGED_UNTIL.put(player.getUUID(), player.level().getGameTime() + (BetterCombat.installed() ? 40 : 20));
		}
	}

	/** Whether a charge is armed right now (the next full-strength swing within a second spends it). */
	public static boolean chargeArmed(ServerPlayer player) {
		Long until = CHARGED_UNTIL.get(player.getUUID());
		return until != null && player.level().getGameTime() <= until;
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
		if (player.getAbilities().instabuild || !weapon.isDamageableItem()) {
			return;
		}

		// What the hit itself wears in 1.20.1: swords and tridents 1, axes and other tools 2.
		int ownWear = weapon.getItem() instanceof SwordItem || weapon.getItem() instanceof TridentItem ? 1
				: weapon.getItem() instanceof DiggerItem ? 2 : 0;
		int extra = config().chargedSwingDurabilityCost - ownWear;

		if (extra > 0) {
			weapon.hurtAndBreak(extra, player, p -> p.broadcastBreakEvent(EquipmentSlot.MAINHAND));
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

	/** Charged Throw: Charge Attack / Heavy Hands + Catch!. Double damage, faster, and it goes through mobs. */
	private static void chargedThrow(ServerPlayer player) {
		if (chargedCombo(player, player.getMainHandItem()) && throwWeapon(player, true)) {
			player.resetAttackStrengthTicker();
		}
	}

	/** Storm Call: Heavy Hands + Lightning Summon. Several bolts around where you aim. */
	private static void stormCall(ServerPlayer player) {
		if (chargedCombo(player, player.getMainHandItem()) && lightning(player, true)) {
			player.resetAttackStrengthTicker();
		}
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

		if (!CombatSkill.has(player, CombatIds.LIGHTNING_SUMMON, Family.TRIDENT, weapon) || coolingDown(player, BloomPayloads.Meter.LIGHTNING_SUMMON)
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
			weapon.hurtAndBreak(cost, player, p -> p.broadcastBreakEvent(EquipmentSlot.MAINHAND));
		}

		coolDown(player, BloomPayloads.Meter.LIGHTNING_SUMMON, (int) Math.round(config.lightningSummonCooldownTicks * scale));
		return true;
	}

	private static void strike(ServerLevel level, ServerPlayer player, Vec3 at) {
		if (player.level() != level) {
			return;
		}

		LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);

		if (bolt != null) {
			bolt.setPos(at.x, at.y, at.z);
			bolt.setCause(player);
			level.addFreshEntity(bolt);
		}
	}

	// ------------------------------------------------------------------ Catch!

	/**
	 * Weapons Catch! can throw: swords, axes and anything modded in those families, and daggers and knives
	 * ({@code bloomlevelup:weapon/dagger}). Tridents throw on their own (Catch! makes them come back instead, see
	 * CombatSkill).
	 */
	public static boolean throwable(ItemStack stack) {
		if (stack.isEmpty() || Weapons.is(stack, Family.TRIDENT)) {
			return false;
		}

		return Weapons.is(stack, Family.SWORD) || Weapons.is(stack, Family.AXE) || stack.is(BloomTags.WEAPON_DAGGER);
	}

	/** A thrown dagger or knife hits harder (FreshOrangeSlices: +50%); anything else 1. */
	public static double throwMultiplier(ItemStack weapon) {
		return weapon.is(BloomTags.WEAPON_DAGGER) ? 1.0 + config().daggerThrowBonus : 1.0;
	}

	private static void throwWeapon(ServerPlayer player) {
		throwWeapon(player, false);
	}

	/** Throws the held weapon; {@code charged} makes it a Charged Throw. Returns whether it was thrown. */
	private static boolean throwWeapon(ServerPlayer player, boolean charged) {
		ItemStack held = player.getMainHandItem();

		// Not gated by the weapon's item cooldown: that's other mods' (the RPG Series' weapon skills), kept apart from Bloom's.
		if (!CombatSkill.active(player, CombatIds.CATCH) || !throwable(held)) {
			return false;
		}

		BloomConfig config = config();

		Level level = player.level();
		int wear = charged ? config.chargedThrowDurabilityCost : config.catchThrowDurabilityCost;

		// A throw never breaks the weapon mid-air: one too worn to pay for it isn't thrown.
		boolean creative = player.getAbilities().instabuild;

		if (!creative && held.isDamageableItem() && wear > 0) {
			if (held.getMaxDamage() - held.getDamageValue() <= wear) {
				player.sendSystemMessage(Component.translatable("bloom.combat.catch.worn").withStyle(ChatFormatting.RED), true);
				return false;
			}

			// Through Unbreaking and Bloom's durability saves, like any other wear (never breaks: checked above).
			held.hurt(wear, player.getRandom(), player);
		}

		ItemStack thrownStack = held.copy();

		if (!creative) {
			held.setCount(0);
		}
		ThrownWeapon thrown = ThrownWeapon.create(level, player, thrownStack);
		double speed = config.catchThrowSpeed;

		if (charged) {
			thrown.charge((float) config.chargeAttackMultiplier, config.chargedThrowPierce);
			speed *= config.chargedThrowSpeedMultiplier;
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 0.6F);
		}

		thrown.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, (float) speed, 1.0F);
		level.addFreshEntity(thrown);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1.0F, 1.0F);
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
		double base = player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);

		// The weapon's own attack damage, as its main-hand modifiers add it (the flat ones; 1.20.1 weapons use only those).
		for (AttributeModifier modifier : weapon.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE)) {
			if (modifier.getOperation() == AttributeModifier.Operation.ADDITION) {
				base += modifier.getAmount();
			}
		}

		// A trident's hit is 8 plus its enchantment bonus: keep the bonus.
		double enchantments = Math.max(0.0, amount - 8.0);
		double drop = 0.0;

		// A falling throw scales with the drop with Death From Above.
		if (CombatSkill.active(player, CombatIds.DEATH_FROM_ABOVE)) {
			drop = StatFormulas.maceFallBonus(thrown.droppedDistance());
		}

		return (float) ((base + enchantments + drop) * thrown.damageMultiplier() * throwMultiplier(weapon));
	}

	// ------------------------------------------------------------------ Assault Rifle

	/** The rounds loaded in a crossbow (1.20.1 keeps them in its NBT, {@code ChargedProjectiles}). */
	public static List<ItemStack> loaded(ItemStack crossbow) {
		List<ItemStack> out = new ArrayList<>();
		CompoundTag tag = crossbow.getTag();

		if (tag != null && tag.contains("ChargedProjectiles", Tag.TAG_LIST)) {
			ListTag list = tag.getList("ChargedProjectiles", Tag.TAG_COMPOUND);

			for (int i = 0; i < list.size(); i++) {
				out.add(ItemStack.of(list.getCompound(i)));
			}
		}

		return out;
	}

	/** Replaces the loaded rounds (and the crossbow's "charged" flag to match). */
	public static void setLoaded(ItemStack crossbow, List<ItemStack> rounds) {
		ListTag list = new ListTag();

		for (ItemStack round : rounds) {
			list.add(round.save(new CompoundTag()));
		}

		CompoundTag tag = crossbow.getOrCreateTag();
		tag.put("ChargedProjectiles", list);
		CrossbowItem.setCharged(crossbow, !rounds.isEmpty());
	}

	/** After a normal load, keep loading until the magazine is full or the ammo runs out. */
	public static void fillMagazine(LivingEntity shooter, ItemStack crossbow) {
		if (!(shooter instanceof ServerPlayer player) || !CombatSkill.active(player, CombatIds.ASSAULT_RIFLE)
				|| !Weapons.is(crossbow, Family.CROSSBOW)) {
			return;
		}

		List<ItemStack> loaded = loaded(crossbow);

		if (loaded.isEmpty()) {
			return;
		}

		int magazine = config().assaultRifleMagazine;
		boolean creative = player.getAbilities().instabuild;

		while (loaded.size() < magazine) {
			ItemStack ammo = player.getProjectile(crossbow);

			if (ammo.isEmpty()) {
				break;
			}

			// Free rounds (creative, Crossbow Infinity's arrows, Rainbow) aren't taken from the stack.
			ItemStack one = creative || CombatSkill.freeAmmo(crossbow, ammo, player) ? ammo.copyWithCount(1) : ammo.split(1);

			if (one.isEmpty()) {
				break;
			}

			loaded.add(one);
		}

		setLoaded(crossbow, loaded);
	}

	/** Fires one round of a loaded magazine. Returns null to let vanilla handle the click. */
	public static InteractionResultHolder<ItemStack> fireFromMagazine(Level level, Player player, InteractionHand hand) {
		ItemStack crossbow = player.getItemInHand(hand);

		if (!CrossbowItem.isCharged(crossbow) || !CombatSkill.activeAnySide(player, CombatIds.ASSAULT_RIFLE)) {
			return null;
		}

		List<ItemStack> rounds = loaded(crossbow);

		if (rounds.size() <= 1) {
			return null;
		}

		if (level.isClientSide()) {
			return InteractionResultHolder.consume(crossbow); // the server fires and syncs the remaining magazine
		}

		ItemStack round = rounds.get(0);
		setLoaded(crossbow, List.of(round));
		CrossbowItem.performShooting(level, player, hand, crossbow, round.is(Items.FIREWORK_ROCKET) ? 1.6F : 3.15F, 1.0F);

		if (!crossbow.isEmpty()) {
			setLoaded(crossbow, rounds.subList(1, rounds.size()));
		}

		return InteractionResultHolder.consume(crossbow);
	}

	/** Visual for a spent charge. */
	static void chargedHitEffects(ServerLevel level, LivingEntity target) {
		level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 14, 0.4, 0.4, 0.4, 0.2);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 0.6F);
	}
}
