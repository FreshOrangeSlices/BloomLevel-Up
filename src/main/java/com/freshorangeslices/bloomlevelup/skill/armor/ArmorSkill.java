package com.freshorangeslices.bloomlevelup.skill.armor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;

import com.freshorangeslices.bloomlevelup.config.BloomConfig;
import com.freshorangeslices.bloomlevelup.data.BloomServices;
import com.freshorangeslices.bloomlevelup.mixin.AbstractArrowInvoker;
import com.freshorangeslices.bloomlevelup.mixin.CooldownInstanceAccessor;
import com.freshorangeslices.bloomlevelup.mixin.ItemCooldownsAccessor;
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
 *   <li>{@link #allowDamage} (Forge's attack event, before vanilla): Castle of Stone invincibility, Reflect, Dodge, Parkour.</li>
 *   <li>Vanilla shield, armor, Resistance and enchantment protection.</li>
 *   <li>{@link #modifyFinalDamage} (mixin, after vanilla): our multiplicative layers, then Absorb.</li>
 *   <li>{@link #afterDamage} (mixin, end of {@code hurt}): Armor XP and Rebound.</li>
 *   <li>{@link #allowDeath} (Forge's death event, cancelled to survive): Last Breath (and Castle of Stone's trigger).</li>
 * </ol>
 */
public final class ArmorSkill {
	/** Brace!: how long each player has been planted without being braced yet (the braced state itself is a profile counter). */
	private static final Map<UUID, Integer> BRACE_TICKS = new HashMap<>();
	/** Brace!: whether each player is pressing a movement key (walk, strafe, jump), as their client reports it. */
	private static final Map<UUID, Boolean> MOVING = new HashMap<>();
	/** A hit in progress on a player, from the start of {@code hurt} to its end (for {@link #afterDamage}). */
	private record Incoming(float amount, float healthBefore, boolean[] blocked) {
	}

	private static final Map<LivingEntity, Incoming> INCOMING = new IdentityHashMap<>();
	private static boolean addingShortenedEffect;

	private ArmorSkill() {
	}

	public static void init() {
		// Dodge, Reflect, Parkour and Castle of Stone: LivingEntityMixin (allowDamage), where Fabric's event sits.
		MinecraftForge.EVENT_BUS.addListener((ShieldBlockEvent event) -> {
			Incoming incoming = INCOMING.get(event.getEntity());

			if (incoming != null) {
				incoming.blocked()[0] = true;
			}
		});
		// Last Breath: LivingEntityMixin (allowDeath), before a totem is used.
		MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, (LivingDeathEvent event) -> {
			if (event.getEntity() instanceof ServerPlayer player) {
				onDeath(player);
			}
		});
		MinecraftForge.EVENT_BUS.addListener((MobEffectEvent.Applicable event) -> {
			if (event.getEntity() instanceof ServerPlayer player && !allowEffect(event.getEffectInstance(), player)) {
				event.setResult(Event.Result.DENY);
			}
		});
		MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) -> {
			if (event.phase == TickEvent.Phase.END) {
				tick(event.getServer());
			}
		});
		MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
			BRACE_TICKS.remove(event.getEntity().getUUID());
			MOVING.remove(event.getEntity().getUUID());
			BloomAttachments.profile(event.getEntity()).setCounter(ArmorIds.BRACED, 0); // logging out isn't moving: no cooldown
		});
		MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
			BRACE_TICKS.clear();
			MOVING.clear();
			INCOMING.clear();
		});
	}

	/** The client says whether its player is pressing a movement key (Brace! ends on your own movement only). */
	public static void movementInput(ServerPlayer player, boolean moving) {
		MOVING.put(player.getUUID(), moving);
	}

	// ------------------------------------------------------------------ around hurt (mixin)

	/** Start of {@code LivingEntity.hurt} on a player: remember the incoming amount and health for {@link #afterDamage}. */
	public static void beginHurt(LivingEntity entity, float amount) {
		if (entity instanceof ServerPlayer) {
			INCOMING.put(entity, new Incoming(amount, entity.getHealth() + entity.getAbsorptionAmount(), new boolean[1]));
		}
	}

	/** End of {@code LivingEntity.hurt}: what the hit actually took (or whether a shield blocked it). */
	public static void endHurt(LivingEntity entity, DamageSource source) {
		Incoming incoming = entity instanceof ServerPlayer ? INCOMING.remove(entity) : null;

		if (incoming != null) {
			float taken = incoming.healthBefore() - (entity.getHealth() + entity.getAbsorptionAmount());
			afterDamage(entity, source, incoming.amount(), Math.max(0.0F, taken), incoming.blocked()[0]);

			// Misc / Ascension: damage while flying on it grounds you until you land.
			if (taken > 0.0F && entity instanceof ServerPlayer player) {
				com.freshorangeslices.bloomlevelup.skill.misc.MiscSkill.afterDamage(player);
			}
		}
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
		// Creative/spectator players never get here for normal damage (Player.hurt turns them away first).
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
		if (player.invulnerableTime > 10 && !source.is(DamageTypeTags.BYPASSES_COOLDOWN)) {
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
			ItemStack caught = ((AbstractArrowInvoker) arrow).bloomlevelup$getPickupItem().copy();

			if (!caught.isEmpty()) {
				player.getInventory().placeItemBackInInventory(caught);
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

		return arrow instanceof ThrownTrident ? null : arrow;
	}

	// ------------------------------------------------------------------ after vanilla mitigation (mixin)

	/**
	 * After {@code Player.disableShield} (mixin): when it just put the shield in use on cooldown, Fitting Armor / Medium
	 * shortens that cooldown, whatever length it was set to (vanilla's 100 ticks, or another mod's).
	 */
	public static void shortenShieldDisable(Player player, Item used, boolean wasDown) {
		if (!(player instanceof ServerPlayer) || wasDown) {
			return;
		}

		ItemCooldowns cooldowns = player.getCooldowns();

		for (Item shield : used == null || used == Items.AIR ? List.of(Items.SHIELD) : List.of(used, Items.SHIELD)) {
			Object instance = ((ItemCooldownsAccessor) cooldowns).bloomlevelup$cooldowns().get(shield);

			if (instance instanceof CooldownInstanceAccessor times) {
				int ticks = times.bloomlevelup$endTime() - times.bloomlevelup$startTime();
				int shorter = shieldDisableTicks(player, ticks);

				if (shorter < ticks) {
					cooldowns.addCooldown(shield, Math.max(1, shorter));
				}

				return;
			}
		}
	}

	/** Fitting Armor / Medium: an axe disables your shield for less time ({@code ticks}: vanilla's 100). */
	public static int shieldDisableTicks(LivingEntity entity, int ticks) {
		if (entity instanceof ServerPlayer player && ticks > 0 && active(BloomAttachments.profile(player), ArmorIds.FITTING_ARMOR)
				&& ArmorStyles.styleOf(player) == ArmorStyle.MEDIUM) {
			return (int) Math.round(ticks * config().mediumShieldDisableRatio);
		}

		return ticks;
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
		List<Layer> layers = layers(player, source, isBraced(player));
		double[] array = new double[layers.size()];

		for (int i = 0; i < array.length; i++) {
			array[i] = layers.get(i).reduction();
		}

		float result = (float) StatFormulas.applyReductions(amount, array);

		if (result > 0.0F && active(profile, ArmorIds.ABSORB)) {
			result = absorb(player, result);
		}

		return result;
	}

	/** One of the Armor category's own damage layers: what it is (a lang key under bloom.sheet.layer.) and how much it takes off. */
	public record Layer(String key, double reduction) {
	}

	/**
	 * The Armor category's percentage layers for a hit from {@code source}, in the order they apply (each multiplies
	 * what's left). {@code braced}: whether Brace! is holding. The stat sheet lists the same layers.
	 */
	public static List<Layer> layers(ServerPlayer player, DamageSource source, boolean braced) {
		return layers(player, source, braced, rawArmor(player));
	}

	/** {@link #layers(ServerPlayer, DamageSource, boolean)} with the armor points (before vanilla's cap) given. */
	public static List<Layer> layers(ServerPlayer player, DamageSource source, boolean braced, double rawArmor) {
		BloomProfile profile = BloomAttachments.profile(player);
		boolean resistanceApplies = !source.is(DamageTypeTags.BYPASSES_RESISTANCE);
		List<Layer> layers = new ArrayList<>(8);

		if (active(profile, ArmorIds.FITTING_ARMOR)) {
			ArmorStyle style = ArmorStyles.styleOf(player);

			if (style == ArmorStyle.MEDIUM && resistanceApplies) {
				layers.add(new Layer("medium", 0.15));
			} else if (style == ArmorStyle.HEAVY && resistanceApplies) {
				layers.add(new Layer("heavy", 0.30));
			} else if (style == ArmorStyle.LIGHT && source.is(DamageTypeTags.IS_FALL)) {
				layers.add(new Layer("light", 0.12)); // Feather Falling I
			}
		}

		if (resistanceApplies && active(profile, ArmorIds.IRON_SKIN)) {
			layers.add(new Layer("iron_skin", 0.20)); // Resistance I
		}

		if (resistanceApplies && active(profile, ArmorIds.BRACE) && braced) {
			layers.add(new Layer("brace", 0.40)); // Resistance II
		}

		if (active(profile, ArmorIds.ELEMENTAL_SKIN) && source.is(BloomTags.ELEMENTAL)) {
			layers.add(new Layer("elemental_skin", 0.50));
		}

		if (active(profile, ArmorIds.VIBRANIUM) && isWeaponClass(source, BloomTags.BLADE_DAMAGE, BloomTags.WEAPON_BLADE, BloomTags.WEAPON_NOT_BLADE)) {
			layers.add(new Layer("vibranium", 0.30));
		}

		if (!source.is(DamageTypeTags.BYPASSES_ARMOR)) {
			double overcap = StatFormulas.armorOvercapReduction(rawArmor, config().armorOvercapReductionCap);

			if (overcap > 0.0) {
				layers.add(new Layer("overcap", overcap));
			}
		}

		return layers;
	}

	/** Armor points before vanilla's 30 cap (Defense invested past it still counts for the overcap layer). */
	public static double armorBeforeCap(ServerPlayer player) {
		return rawArmor(player);
	}

	/** Whether Absorb is on: half of what's left after every layer goes into armor durability. */
	public static boolean absorbs(ServerPlayer player) {
		return active(BloomAttachments.profile(player), ArmorIds.ABSORB);
	}

	private static boolean isWeaponClass(DamageSource source, net.minecraft.tags.TagKey<net.minecraft.world.damagesource.DamageType> damageTag,
			net.minecraft.tags.TagKey<net.minecraft.world.item.Item> weaponTag, net.minecraft.tags.TagKey<net.minecraft.world.item.Item> notTag) {
		if (source.is(damageTag)) {
			return true;
		}

		if (!source.is(BloomTags.MELEE)) {
			return false;
		}

		// 1.20.1 damage sources don't carry the weapon: a melee hit's weapon is what its attacker holds.
		ItemStack weapon = source.getDirectEntity() instanceof LivingEntity attacker && attacker == source.getEntity() ? attacker.getMainHandItem()
				: ItemStack.EMPTY;
		return weapon != null && !weapon.isEmpty() && weapon.is(weaponTag) && !weapon.is(notTag);
	}

	/** Armor points before vanilla's clamp, so Defense invested past the cap still counts for overcap. */
	private static double rawArmor(ServerPlayer player) {
		AttributeInstance armor = player.getAttribute(Attributes.ARMOR);

		if (armor == null) {
			return 0.0;
		}

		double total = armor.getBaseValue();

		for (AttributeModifier modifier : armor.getModifiers()) {
			if (modifier.getOperation() == AttributeModifier.Operation.ADDITION) {
				total += modifier.getAmount();
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
				stack.shrink(1);
				player.broadcastBreakEvent(slot);
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
			attacker.hurt(player.damageSources().thorns(player), baseDamage * 0.5F);
		}
	}

	private static void awardXp(ServerPlayer player, float incoming, double multiplier) {
		double basis = Math.min(incoming, player.getMaxHealth());

		if (basis > 0.0) {
			SkillXp.award(player, SkillXp.ARMOR, basis * config().armorXpPerDamage * multiplier);
		}
	}

	// ------------------------------------------------------------------ death

	public static boolean allowDeath(LivingEntity entity, DamageSource source) {
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
		boolean harmful = instance.getEffect().getCategory() == MobEffectCategory.HARMFUL;

		// Effects from food the player chose to eat (rotten flesh, spider eyes...) always land.
		if (harmful && active(profile, ArmorIds.UNYIELDING) && !ConsumeContext.isConsuming(player)
				&& !BloomTags.is(instance.getEffect(), BloomTags.UNYIELDING_IGNORED) && player.getRandom().nextDouble() < 0.5) {
			return false;
		}

		if (active(profile, ArmorIds.THICK_BLOOD) && BloomTags.is(instance.getEffect(), BloomTags.DAMAGE_OVER_TIME)
				&& !instance.isInfiniteDuration() && instance.getDuration() > 1) {
			addingShortenedEffect = true;

			try {
				player.addEffect(new MobEffectInstance(instance.getEffect(), Math.max(1, instance.getDuration() / 2), instance.getAmplifier(),
						instance.isAmbient(), instance.isVisible(), instance.showIcon()));
			} finally {
				addingShortenedEffect = false;
			}

			return false;
		}

		return true;
	}

	// ------------------------------------------------------------------ per tick

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			tickPlayer(player);
		}
	}

	/** One player's per-tick Armor checks (Brace!, Castle of Stone's rest). Public for game tests. */
	public static void tickPlayer(ServerPlayer player) {
		BloomConfig config = config();
		BloomProfile profile = BloomAttachments.profile(player);
		UUID id = player.getUUID();

		// Brace!: crouch still or raise a shield, on the ground, and you're braced (at once by default). Only your own
		// movement ends it (walking, jumping, standing up, lowering the shield); knockback doesn't. Once it ends it
		// cools down (10 s) before it can take hold again, so hopping while crouched can't re-brace on every landing.
		// The state lives in synced profile counters so the HUD can show it.
		boolean braced = profile.counter(ArmorIds.BRACED) != 0;

		if (active(profile, ArmorIds.BRACE)) {
			boolean moving = MOVING.getOrDefault(id, false);
			boolean shieldUp = player.isUsingItem() && player.getUseItem().canPerformAction(ToolActions.SHIELD_BLOCK);
			boolean planted = !moving && (player.isShiftKeyDown() || shieldUp);
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
