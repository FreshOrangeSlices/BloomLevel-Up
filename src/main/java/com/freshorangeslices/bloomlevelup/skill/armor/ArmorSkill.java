package com.freshorangeslices.bloomlevelup.skill.armor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.effect.ServerMobEffectEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import com.freshorangeslices.bloomlevelup.config.BloomConfig;
import com.freshorangeslices.bloomlevelup.data.BloomServices;
import com.freshorangeslices.bloomlevelup.mixin.EntityAccessor;
import com.freshorangeslices.bloomlevelup.network.BloomSync;
import com.freshorangeslices.bloomlevelup.player.BloomAttachments;
import com.freshorangeslices.bloomlevelup.player.BloomProfile;
import com.freshorangeslices.bloomlevelup.progression.ProgressionService;
import com.freshorangeslices.bloomlevelup.registry.BloomTags;
import com.freshorangeslices.bloomlevelup.skill.ConsumeContext;
import com.freshorangeslices.bloomlevelup.skill.SkillXp;
import com.freshorangeslices.bloomlevelup.stats.StatFormulas;
import com.freshorangeslices.bloomlevelup.stats.StatFormulas.ArmorStyle;

/**
 * The Armor category: XP from danger, Dodge, the multiplicative defensive layers, and every Armor Major's
 * runtime behaviour. Numbers come from the Armor design doc; tunable ones live in {@code BloomConfig}.
 *
 * <p>Damage resolution for a player, in order:
 * <ol>
 *   <li>{@link #allowDamage} (before vanilla): Castle of Stone invincibility, Reflect, Dodge, Parkour.</li>
 *   <li>Vanilla shield, armor, Resistance and enchantment protection.</li>
 *   <li>{@link #modifyFinalDamage} (mixin, after vanilla): our multiplicative layers, then Absorb.</li>
 *   <li>{@link #afterDamage}: Armor XP and Rebound.</li>
 *   <li>{@link #allowDeath}: Last Breath (and Castle of Stone's trigger).</li>
 * </ol>
 */
public final class ArmorSkill {
	/** Brace!: how long each player has been planted without being braced yet (the braced state itself is a profile counter). */
	private static final Map<UUID, Integer> BRACE_TICKS = new HashMap<>();
	private static boolean addingShortenedEffect;

	private ArmorSkill() {
	}

	public static void init() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(ArmorSkill::allowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(ArmorSkill::afterDamage);
		ServerLivingEntityEvents.ALLOW_DEATH.register(ArmorSkill::allowDeath);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				onDeath(player);
			}
		});
		ServerMobEffectEvents.ALLOW_ADD.register((instance, entity, context) ->
				context.isFromCommand() || !(entity instanceof ServerPlayer player) || allowEffect(instance, player));
		ServerTickEvents.END_SERVER_TICK.register(ArmorSkill::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((listener, server) -> {
			BRACE_TICKS.remove(listener.player.getUUID());
			BloomAttachments.profile(listener.player).setCounter(ArmorIds.BRACED, 0); // logging out isn't moving: no cooldown
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> BRACE_TICKS.clear());
	}

	private static ProgressionService service() {
		return BloomServices.progression();
	}

	private static BloomConfig config() {
		return BloomServices.config();
	}

	private static boolean active(BloomProfile profile, String majorId) {
		return service().isMajorActive(profile, majorId);
	}

	public static boolean isBraced(ServerPlayer player) {
		return BloomAttachments.profile(player).counter(ArmorIds.BRACED) != 0;
	}

	// ------------------------------------------------------------------ before vanilla

	public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		// Creative/spectator players never get here for normal damage (vanilla makes them invulnerable first).
		if (!(entity instanceof ServerPlayer player)) {
			return true;
		}

		BloomProfile profile = BloomAttachments.profile(player);
		long now = player.level().getGameTime();

		// Castle of Stone: a few seconds of true invincibility after Last Breath saves you.
		if (profile.counter(ArmorIds.CASTLE_UNTIL) > now && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}

		// Hits landing inside vanilla's damage cooldown are vanilla's business; don't roll or award XP for them.
		if (((EntityAccessor) player).bloomlevelup$getInvulnerableTime() > 10 && !source.is(DamageTypeTags.BYPASSES_COOLDOWN)) {
			return true;
		}

		RandomSource random = player.getRandom();

		// Reflect: 10% catch, 25% deflect; catch wins when both roll. A cancelled arrow bounces back on its own.
		if (active(profile, ArmorIds.REFLECT) && source.is(DamageTypeTags.IS_PROJECTILE) && source.getDirectEntity() instanceof Projectile projectile) {
			boolean caught = random.nextFloat() < 0.10F;
			boolean deflected = random.nextFloat() < 0.25F;

			if (caught) {
				catchProjectile(player, projectile);
				awardXp(player, amount, 1.0);
				return false;
			}

			if (deflected) {
				awardXp(player, amount, 1.0);
				return false;
			}
		}

		// Dodge: main roll, then Light style's separate 10% backup roll. A dodge is a clean miss.
		if (source.is(BloomTags.DODGEABLE) && !player.isBlocking()) {
			double chance = StatFormulas.dodgeChance(service().statValue(profile, ArmorIds.DODGE), config().dodgeChanceCap);
			boolean dodged = chance > 0.0 && random.nextDouble() < chance;

			if (!dodged && active(profile, ArmorIds.FITTING_ARMOR) && ArmorStyles.styleOf(player) == ArmorStyle.LIGHT) {
				dodged = random.nextDouble() < 0.10;
			}

			if (dodged) {
				awardXp(player, amount, config().armorDodgeXpMultiplier);
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE,
						SoundSource.PLAYERS, 0.8F, 1.4F);
				return false;
			}
		}

		// Parkour: 35% to ignore fall damage entirely.
		if (source.is(DamageTypeTags.IS_FALL) && active(profile, ArmorIds.PARKOUR) && random.nextDouble() < 0.35) {
			awardXp(player, amount, 1.0);
			return false;
		}

		return true;
	}

	private static void catchProjectile(ServerPlayer player, Projectile projectile) {
		if (catchable(projectile) instanceof AbstractArrow arrow) {
			// Keeps the item's full data (enchantments, potion contents, custom names).
			ItemStack caught = arrow.getPickupItemStackOrigin().copy();

			if (!caught.isEmpty()) {
				player.getInventory().placeItemBackInInventory(caught, Prediction.SERVER_ONLY);
			}
		}

		projectile.discard();
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6F, 1.2F);
	}

	/**
	 * What a catch puts in the inventory. Normally collectable projectiles always count. Arrows that can't
	 * normally be picked up (a skeleton's) count too, since catching one is the point; mob-thrown tridents
	 * and Infinity / creative arrows don't, so a catch never duplicates or farms an item.
	 */
	public static Projectile catchable(Projectile projectile) {
		if (!(projectile instanceof AbstractArrow arrow) || arrow.pickup == AbstractArrow.Pickup.CREATIVE_ONLY) {
			return null;
		}

		if (arrow.pickup == AbstractArrow.Pickup.ALLOWED) {
			return arrow;
		}

		return arrow instanceof net.minecraft.world.entity.projectile.arrow.ThrownTrident ? null : arrow;
	}

	// ------------------------------------------------------------------ after vanilla mitigation (mixin)

	/** Fitting Armor / Medium: an axe disables your shield for half as long. */
	public static float shieldDisableSeconds(LivingEntity entity, float seconds) {
		if (entity instanceof ServerPlayer player && seconds > 0.0F && active(BloomAttachments.profile(player), ArmorIds.FITTING_ARMOR)
				&& ArmorStyles.styleOf(player) == ArmorStyle.MEDIUM) {
			return (float) (seconds * config().mediumShieldDisableRatio);
		}

		return seconds;
	}

	/**
	 * Applies the Armor category's percentage layers multiplicatively to damage that already went through
	 * vanilla armor/Resistance/enchantments, then lets Absorb move half of what's left into armor durability.
	 */
	public static float modifyFinalDamage(ServerPlayer player, DamageSource source, float amount) {
		if (!(amount > 0.0F)) {
			return amount;
		}

		BloomProfile profile = BloomAttachments.profile(player);
		boolean resistanceApplies = !source.is(DamageTypeTags.BYPASSES_RESISTANCE);
		List<Double> layers = new ArrayList<>(8);

		if (active(profile, ArmorIds.FITTING_ARMOR)) {
			ArmorStyle style = ArmorStyles.styleOf(player);

			if (style == ArmorStyle.MEDIUM && resistanceApplies) {
				layers.add(0.15);
			} else if (style == ArmorStyle.HEAVY && resistanceApplies) {
				layers.add(0.30);
			} else if (style == ArmorStyle.LIGHT && source.is(DamageTypeTags.IS_FALL)) {
				layers.add(0.12); // Feather Falling I
			}
		}

		if (resistanceApplies && active(profile, ArmorIds.IRON_SKIN)) {
			layers.add(0.20); // Resistance I
		}

		if (resistanceApplies && active(profile, ArmorIds.BRACE) && isBraced(player)) {
			layers.add(0.40); // Resistance II
		}

		if (active(profile, ArmorIds.ELEMENTAL_SKIN) && source.is(BloomTags.ELEMENTAL)) {
			layers.add(0.50);
		}

		if (active(profile, ArmorIds.VIBRANIUM) && isWeaponClass(source, BloomTags.BLADE_DAMAGE, BloomTags.WEAPON_BLADE)) {
			layers.add(0.30);
		}

		if (active(profile, ArmorIds.ADAMANTIUM) && isWeaponClass(source, BloomTags.BLUNT_DAMAGE, BloomTags.WEAPON_BLUNT)) {
			layers.add(0.40);
		}

		if (!source.is(DamageTypeTags.BYPASSES_ARMOR)) {
			double overcap = StatFormulas.armorOvercapReduction(rawArmor(player), config().armorOvercapReductionCap);

			if (overcap > 0.0) {
				layers.add(overcap);
			}
		}

		double[] array = new double[layers.size()];

		for (int i = 0; i < array.length; i++) {
			array[i] = layers.get(i);
		}

		float result = (float) StatFormulas.applyReductions(amount, array);

		if (result > 0.0F && active(profile, ArmorIds.ABSORB)) {
			result = absorb(player, result);
		}

		return result;
	}

	private static boolean isWeaponClass(DamageSource source, net.minecraft.tags.TagKey<net.minecraft.world.damagesource.DamageType> damageTag,
			net.minecraft.tags.TagKey<net.minecraft.world.item.Item> weaponTag) {
		if (source.is(damageTag)) {
			return true;
		}

		if (!source.is(BloomTags.MELEE)) {
			return false;
		}

		ItemStack weapon = source.getWeaponItem();
		return weapon != null && !weapon.isEmpty() && weapon.is(weaponTag);
	}

	/** Armor points before vanilla's clamp, so Defense invested past the cap still counts for overcap. */
	private static double rawArmor(ServerPlayer player) {
		AttributeInstance armor = player.getAttribute(Attributes.ARMOR);

		if (armor == null) {
			return 0.0;
		}

		double total = armor.getBaseValue();

		for (AttributeModifier modifier : armor.getModifiers()) {
			if (modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
				total += modifier.amount();
			}
		}

		return total;
	}

	/** Absorb: half the remaining damage goes into one random worn armor piece's durability, 1:1, rounded down. */
	private static float absorb(ServerPlayer player, float amount) {
		List<EquipmentSlot> candidates = new ArrayList<>(4);

		for (EquipmentSlot slot : ArmorStyles.ARMOR_SLOTS) {
			ItemStack stack = player.getItemBySlot(slot);

			if (!stack.isEmpty() && stack.isDamageableItem()) {
				candidates.add(slot);
			}
		}

		if (candidates.isEmpty()) {
			return amount;
		}

		EquipmentSlot slot = candidates.get(player.getRandom().nextInt(candidates.size()));
		ItemStack stack = player.getItemBySlot(slot);
		float redirected = amount * 0.5F;
		int durability = (int) Math.floor(redirected);

		if (durability > 0) {
			int remaining = stack.getMaxDamage() - stack.getDamageValue();

			if (durability >= remaining) {
				// The piece breaks; whatever it couldn't pay simply disappears.
				ItemStack broken = stack.copy();
				stack.shrink(1);
				player.onEquippedItemBroken(broken, slot);
			} else {
				stack.setDamageValue(stack.getDamageValue() + durability);
			}
		}

		return amount - redirected;
	}

	// ------------------------------------------------------------------ after the hit

	public static void afterDamage(LivingEntity entity, DamageSource source, float baseDamage, float damageTaken, boolean blocked) {
		if (!(entity instanceof ServerPlayer player) || (!(damageTaken > 0.0F) && !blocked)) {
			return;
		}

		awardXp(player, baseDamage, blocked ? config().armorBlockXpMultiplier : 1.0);

		// Rebound: 15% of direct melee hits throw 50% of the incoming (pre-mitigation) damage back.
		if (damageTaken > 0.0F && source.is(BloomTags.MELEE) && source.getEntity() instanceof LivingEntity attacker
				&& attacker != player && attacker.isAlive()
				&& active(BloomAttachments.profile(player), ArmorIds.REBOUND) && player.getRandom().nextDouble() < 0.15) {
			attacker.hurtServer((ServerLevel) player.level(), player.damageSources().thorns(player), baseDamage * 0.5F);
		}
	}

	private static void awardXp(ServerPlayer player, float incoming, double multiplier) {
		double basis = Math.min(incoming, player.getMaxHealth());

		if (basis > 0.0) {
			SkillXp.award(player, SkillXp.ARMOR, basis * config().armorXpPerDamage * multiplier);
		}
	}

	// ------------------------------------------------------------------ death

	public static boolean allowDeath(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer player)) {
			return true;
		}

		BloomProfile profile = BloomAttachments.profile(player);

		if (!active(profile, ArmorIds.LAST_BREATH) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}

		BloomConfig config = config();
		long now = player.level().getGameTime();
		long start = profile.counter(ArmorIds.LAST_BREATH_WINDOW_START);
		long attempts = profile.counter(ArmorIds.LAST_BREATH_ATTEMPTS);

		if (attempts == 0 || now < start || now - start >= config.lastBreathWindowTicks) {
			start = now;
			attempts = 0;
		}

		double chance = StatFormulas.lastBreathChance((int) Math.min(attempts, 64));
		attempts++;
		profile.setCounter(ArmorIds.LAST_BREATH_WINDOW_START, start);
		profile.setCounter(ArmorIds.LAST_BREATH_ATTEMPTS, attempts);
		BloomSync.changed(player);

		if (player.getRandom().nextDouble() >= chance) {
			return true;
		}

		player.setHealth(config.lastBreathHealth);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6F, 1.3F);
		player.sendSystemMessage(Component.translatable("bloom.armor.last_breath", (int) Math.round(Math.min(1.0, chance / 2.0) * 100))
				.withStyle(ChatFormatting.GOLD), true);

		if (active(profile, ArmorIds.CASTLE_OF_STONE) && profile.counter(ArmorIds.CASTLE_SPENT) == 0) {
			profile.setCounter(ArmorIds.CASTLE_UNTIL, now + config.castleOfStoneTicks);
			profile.setCounter(ArmorIds.CASTLE_SPENT, 1);
			player.sendSystemMessage(Component.translatable("bloom.armor.castle_of_stone").withStyle(ChatFormatting.GRAY));
		}

		return false;
	}

	private static void onDeath(ServerPlayer player) {
		BloomProfile profile = BloomAttachments.profile(player);
		profile.setCounter(ArmorIds.LAST_BREATH_ATTEMPTS, 0);
		profile.setCounter(ArmorIds.LAST_BREATH_WINDOW_START, 0);
		profile.setCounter(ArmorIds.CASTLE_UNTIL, 0);
		profile.setCounter(ArmorIds.BRACED, 0);
		BRACE_TICKS.remove(player.getUUID());
		BloomSync.changed(player);
	}

	// ------------------------------------------------------------------ effects

	/** Unyielding (50% to shrug off a harmful effect) and Thick Blood (Poison/Wither last half as long). */
	public static boolean allowEffect(MobEffectInstance instance, ServerPlayer player) {
		if (addingShortenedEffect) {
			return true;
		}

		BloomProfile profile = BloomAttachments.profile(player);
		boolean harmful = instance.getEffect().value().getCategory() == MobEffectCategory.HARMFUL;

		// Effects from food the player chose to eat (rotten flesh, spider eyes...) always land.
		if (harmful && active(profile, ArmorIds.UNYIELDING) && !ConsumeContext.isConsuming(player)
				&& !instance.getEffect().is(BloomTags.UNYIELDING_IGNORED) && player.getRandom().nextDouble() < 0.5) {
			return false;
		}

		if (active(profile, ArmorIds.THICK_BLOOD) && instance.getEffect().is(BloomTags.DAMAGE_OVER_TIME)
				&& !instance.isInfiniteDuration() && instance.getDuration() > 1) {
			addingShortenedEffect = true;

			try {
				player.addEffect(instance.withScaledDuration(0.5F));
			} finally {
				addingShortenedEffect = false;
			}

			return false;
		}

		return true;
	}

	// ------------------------------------------------------------------ per tick

	private static void tick(MinecraftServer server) {
		BloomConfig config = config();

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			BloomProfile profile = BloomAttachments.profile(player);
			UUID id = player.getUUID();

			// Brace!: crouch still or raise a shield, on the ground, and you're braced (at once by default). Only your own
			// movement ends it (walking, jumping, standing up, lowering the shield); knockback doesn't. Once it ends it
			// cools down (10 s) before it can take hold again, so hopping while crouched can't re-brace on every landing.
			// The state lives in synced profile counters so the HUD can show it.
			boolean braced = profile.counter(ArmorIds.BRACED) != 0;

			if (active(profile, ArmorIds.BRACE)) {
				Input input = player.getLastClientInput();
				boolean moving = input.forward() || input.backward() || input.left() || input.right() || input.jump();
				boolean shieldUp = player.isUsingItem() && player.getUseItem().has(DataComponents.BLOCKS_ATTACKS);
				boolean planted = !moving && (input.shift() || shieldUp);
				long now = player.level().getGameTime();

				if (braced) {
					if (!planted) {
						profile.setCounter(ArmorIds.BRACED, 0);
						profile.setCounter(ArmorIds.BRACE_READY_AT, now + config.braceCooldownTicks);
						BloomSync.changed(player);
					}
				} else if (planted && player.onGround() && now >= profile.counter(ArmorIds.BRACE_READY_AT)) {
					if (BRACE_TICKS.merge(id, 1, Integer::sum) >= config.braceDelayTicks) {
						BRACE_TICKS.remove(id);
						profile.setCounter(ArmorIds.BRACED, 1);
						BloomSync.changed(player);
					}
				} else {
					BRACE_TICKS.remove(id);
				}
			} else {
				BRACE_TICKS.remove(id);

				if (braced) { // refunded or switched off while braced
					profile.setCounter(ArmorIds.BRACED, 0);
					BloomSync.changed(player);
				}
			}

			// Castle of Stone recharges by lying in a bed for a few seconds (no night skip needed).
			if (profile.counter(ArmorIds.CASTLE_SPENT) != 0 && player.isSleeping() && player.getSleepTimer() >= config.castleOfStoneRestTicks) {
				profile.setCounter(ArmorIds.CASTLE_SPENT, 0);
				player.sendSystemMessage(Component.translatable("bloom.armor.castle_recharged").withStyle(ChatFormatting.GREEN), true);
				BloomSync.changed(player);
			}
		}
	}
}
