package com.freshorangeslices.bloompets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Combat pets' skills (docs/design/PETS.md, Combat pets' skills): the plugin's mcMMO-style tree, unlocked by the pet's
 * own level. Each combat kind has its own names for the shared skills, plus a starting and a special skill of its own;
 * the Wolf's are new (FreshOrangeSlices, 2026-11-05). Skills work while the pet is in its owner's party and awake.
 * Gore (a chance of double damage) is half the pet's level in %, up to 50% at 100 (strong on purpose: pets are for
 * players who'd rather not fight). The numbers are the plugin's.
 */
public final class PetSkills {
	/** Iron Colossus's extra health, a permanent modifier so the health it gives survives a reload. */
	private static final UUID COLOSSUS = UUID.fromString("6c1f7a52-6a8e-4f0e-9f4c-b10b9e7d0c01");
	private static final ResourceLocation BLEED = new ResourceLocation("bloomlevelup", "bleed");
	private static final double GORE_MAX = 50.0;
	private static final int PACK_BOND_TICKS = 60;
	private static final int HOWL_EVERY = 600;
	private static final int GUARDIAN_RANGE = 32;
	private static final double SOOTHE_RADIUS = 6.0;
	/** Per player: the last thing they hit, and when (Pack Bond). */
	private static final Map<UUID, Object[]> OWNER_HITS = new HashMap<>();
	/** Per pet: its landed hits (Momentum) and the target its next landing hit rams (Ram). */
	private static final Map<Entity, int[]> HITS = new WeakHashMap<>();
	private static final Map<Entity, Entity> RAM_NEXT = new WeakHashMap<>();
	/** Things to do once the hit that caused them is over (a launch, after the hit's own knockback). */
	private static final List<Runnable> LATER = new ArrayList<>();

	private PetSkills() {
	}

	/** The combat kinds with a skill tree (the Vex comes later, with its gear). */
	public enum Kind {
		WOLF, IRON_GOLEM, POLAR_BEAR, GOAT, PANDA;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		public static Kind byId(String id) {
			for (Kind kind : values()) {
				if (kind.id().equals(id)) {
					return kind;
				}
			}

			return null;
		}
	}

	/** The skill slots, in the screen's order (left column top to bottom, then the right), and the level each opens at. */
	public enum Slot {
		FIRST(1), AWARENESS(10), GORE(15), SPECIAL(20), FAST_FOOD(20), THICK_FUR(25), HOLY_HOUND(35), SHOCK_PROOF(50), CLAWS(75),
		/** Level 100: a gold name and a sparkle, a second wind once each time it comes out. Every Bloom pet has it. */
		MAX(100);

		public final int level;

		Slot(int level) {
			this.level = level;
		}

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	public static String nameKey(Kind kind, Slot slot) {
		return slot == Slot.MAX || kind == null ? "bloompets.skill.max" : "bloompets.skill." + kind.id() + "." + slot.id();
	}

	/** The pet's skill tree, or null (non-combat pets, and modded companions until their Spirit comes). */
	public static Kind kind(Entity pet) {
		Species species = Pets.species(pet);
		return species == null || species.skills() == null ? null : Kind.byId(species.skills());
	}

	// The numbers, also shown in the screen.

	public static double goreChance(int level) {
		return Math.min(GORE_MAX, Math.max(0, level) / 2.0);
	}

	public static double soothChance(int level) {
		double t = Math.max(0.0, Math.min(1.0, (level - Slot.SPECIAL.level) / (double) (PetLevels.MAX - Slot.SPECIAL.level)));
		return 15.0 + 45.0 * t;
	}

	public static int soothHeal(int level) {
		return Math.min(9, 3 + Math.max(0, level - Slot.SPECIAL.level) / 10);
	}

	public static double colossusHealth(int level) {
		return 0.5 * level;
	}

	public static boolean crushHigh(int level) {
		return level >= 50;
	}

	/** The pet's level if its skills work right now (in the party or at its post, awake, not sulking), else -1. */
	static int skillLevel(Entity pet) {
		if (!(pet instanceof LivingEntity) || pet.level().isClientSide() || Pets.kind(pet) != Pets.Kind.BLOOM
				|| !Pets.inParty(pet) && !PetPosts.posted(pet) || Fainting.fainted(pet) || PetMood.sad(pet) || Pets.owner(pet) == null) {
			return -1;
		}

		return PetLevels.level(pet);
	}

	private static boolean has(int level, Slot slot) {
		return level >= slot.level;
	}

	private static boolean roll(LivingEntity pet, double percent) {
		return percent > 0 && pet.getRandom().nextDouble() * 100.0 < percent;
	}

	private static ServerPlayer owner(LivingEntity pet) {
		return pet.getServer() == null || Pets.owner(pet) == null ? null : pet.getServer().getPlayerList().getPlayer(Pets.owner(pet));
	}

	/** Iron Colossus's health, while the golem is in the party (on joining or leaving, levelling, loading). */
	public static void refresh(LivingEntity pet) {
		AttributeInstance health = pet.getAttribute(Attributes.MAX_HEALTH);

		if (health == null) {
			return;
		}

		double bonus = kind(pet) == Kind.IRON_GOLEM && Pets.inParty(pet) && !PetMood.sad(pet) ? colossusHealth(PetLevels.level(pet)) : 0.0;
		AttributeModifier old = health.getModifier(COLOSSUS);

		if (old != null && old.getAmount() == bonus) {
			return;
		}

		if (old != null) {
			health.removeModifier(COLOSSUS);
		}

		if (bonus > 0) {
			health.addPermanentModifier(new AttributeModifier(COLOSSUS, "Bloom Pets: Iron Colossus", bonus, AttributeModifier.Operation.ADDITION));
		}

		if (pet.getHealth() > pet.getMaxHealth()) {
			pet.setHealth(pet.getMaxHealth());
		}
	}

	/** Level 100: its name turns gold. */
	public static void goldName(LivingEntity pet) {
		Component name = pet.getCustomName();
		String text = name == null ? pet.getName().getString() : name.getString();

		if (name == null || name.getStyle().getColor() == null || !name.getStyle().getColor().equals(net.minecraft.network.chat.TextColor.fromLegacyFormat(ChatFormatting.GOLD))) {
			pet.setCustomName(Component.literal(text).withStyle(ChatFormatting.GOLD));
		}
	}

	/** Once a second for a loaded pet: Alpha Howl in a fight, a level-100 pet's sparkle. */
	static void second(LivingEntity pet) {
		if (!(pet.level() instanceof ServerLevel level)) {
			return;
		}

		if (PetLevels.level(pet) >= PetLevels.MAX && Pets.kind(pet) == Pets.Kind.BLOOM && pet.tickCount % 40 < 20) {
			level.sendParticles(ParticleTypes.WAX_OFF, pet.getX(), pet.getY() + pet.getBbHeight() + 0.2, pet.getZ(), 2,
					pet.getBbWidth() * 0.5, 0.2, pet.getBbWidth() * 0.5, 0.0);
		}

		int skill = skillLevel(pet);

		if (kind(pet) == Kind.WOLF && has(skill, Slot.SPECIAL) && pet instanceof Mob mob && mob.getTarget() != null && mob.getTarget().isAlive()) {
			CompoundTag data = Pets.data(pet);
			long now = level.getServer().overworld().getGameTime();

			if (now >= data.getLong("howlAt")) {
				data.putLong("howlAt", now + HOWL_EVERY);
				howl(mob, level);
			}
		}
	}

	/** Alpha Howl: its owner and their pets nearby get Strength I for 6 seconds; enemies near it are slowed. */
	private static void howl(Mob wolf, ServerLevel level) {
		level.playSound(null, wolf.blockPosition(), SoundEvents.WOLF_HOWL, SoundSource.NEUTRAL, 1.2F, 1.0F);
		level.sendParticles(ParticleTypes.NOTE, wolf.getX(), wolf.getY() + wolf.getBbHeight() + 0.4, wolf.getZ(), 4, 0.3, 0.2, 0.3, 0.0);

		for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, wolf.getBoundingBox().inflate(16.0))) {
			if (near.getUUID().equals(Pets.owner(wolf)) || PetXp.sameSide(wolf, near)) {
				near.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 120, 0), wolf);
			} else if (near instanceof Enemy && near.distanceToSqr(wolf) <= 6.0 * 6.0) {
				near.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 0), wolf);
			}
		}
	}

	// Attacking.

	/**
	 * A pet's own blow before armor: Wolfsteel Fangs and the rest (+2), Gore (double, with {@code roll} under its
	 * chance: 0 to 100), Pack Bond (+20%), Momentum (+30% on every third hit). Public for the game tests.
	 */
	public static float attack(LivingEntity pet, LivingEntity target, int level, float amount, double roll) {
		Kind kind = kind(pet);
		float damage = amount;

		if (has(level, Slot.CLAWS)) {
			damage += 2.0F;
		}

		if (has(level, Slot.GORE) && roll < goreChance(level)) {
			damage += amount;

			if (pet.level() instanceof ServerLevel server) {
				server.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 12,
						target.getBbWidth() * 0.5, target.getBbHeight() * 0.3, target.getBbWidth() * 0.5, 0.2);
			}

			// Rending Bite: the wound Bleeds (Bloom's Bleed, when Bloom is there).
			MobEffect bleed = kind == Kind.WOLF ? ForgeRegistries.MOB_EFFECTS.getValue(BLEED) : null;

			if (bleed != null) {
				target.addEffect(new MobEffectInstance(bleed, 80, 0, false, false, true), pet);
			}
		}

		if (kind == Kind.WOLF && has(level, Slot.FIRST)) {
			Object[] hit = OWNER_HITS.get(Pets.owner(pet));

			if (hit != null && target.getUUID().equals(hit[0]) && pet.level().getGameTime() - (Long) hit[1] <= PACK_BOND_TICKS) {
				damage *= 1.2F;
			}
		}

		if (kind == Kind.GOAT && has(level, Slot.FIRST) && (HITS.computeIfAbsent(pet, k -> new int[1])[0] + 1) % 3 == 0) {
			damage *= 1.3F;
			pet.level().playSound(null, pet.blockPosition(), SoundEvents.GOAT_RAM_IMPACT, SoundSource.NEUTRAL, 0.8F, 1.0F);

			if (has(level, Slot.SPECIAL)) {
				RAM_NEXT.put(pet, target);
			}
		}

		return damage;
	}

	@SubscribeEvent
	public static void onHurt(LivingHurtEvent event) {
		LivingEntity target = event.getEntity();

		if (target.level().isClientSide()) {
			return;
		}

		DamageSource source = event.getSource();

		if (source.getEntity() instanceof Player player) {
			OWNER_HITS.put(player.getUUID(), new Object[] {target.getUUID(), target.level().getGameTime()});
		}

		// The pet taking it: its defences.
		int guard = skillLevel(target);

		if (guard >= 0) {
			event.setAmount(defend(target, guard, source, event.getAmount()));
		}

		// The pet dealing it, with its own bite or blow (not a sweep or spark its weapon tree sends out).
		if (!PetTrees.splashing() && source.getDirectEntity() instanceof LivingEntity pet && source.getEntity() == pet && pet != target) {
			int level = skillLevel(pet);

			if (level >= 0 && kind(pet) != null) {
				event.setAmount(attack(pet, target, level, event.getAmount(), pet.getRandom().nextDouble() * 100.0));
			}
		}
	}

	/** Thick Fur (half from blows and arrows; the golem's from fire too) and Shock Proof (a sixth from blasts and lightning). */
	private static float defend(LivingEntity pet, int level, DamageSource source, float amount) {
		boolean blow = source.is(DamageTypeTags.IS_PROJECTILE) || source.getEntity() != null && !source.is(DamageTypeTags.IS_EXPLOSION)
				&& !source.is(DamageTypeTags.IS_FIRE) && !source.is(DamageTypes.MAGIC) && !source.is(DamageTypes.INDIRECT_MAGIC)
				&& !source.is(DamageTypes.THORNS) && !source.is(DamageTypes.LIGHTNING_BOLT);
		boolean fire = source.is(DamageTypeTags.IS_FIRE);

		if (has(level, Slot.THICK_FUR) && (blow || fire && kind(pet) == Kind.IRON_GOLEM)) {
			amount /= 2.0F;
		}

		if (has(level, Slot.SHOCK_PROOF) && (source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypes.LIGHTNING_BOLT))) {
			amount /= 6.0F;
		}

		return amount;
	}

	/** After a pet's blow landed: Fast Food, Momentum's count, Crushing Presence, Ram, Soothing Presence. */
	@SubscribeEvent(priority = EventPriority.LOW)
	public static void onDamage(LivingDamageEvent event) {
		LivingEntity target = event.getEntity();

		if (PetTrees.splashing() || target.level().isClientSide() || !(event.getSource().getDirectEntity() instanceof LivingEntity pet)
				|| event.getSource().getEntity() != pet || pet == target) {
			return;
		}

		int level = skillLevel(pet);
		Kind kind = kind(pet);
		float dealt = Math.min(event.getAmount(), target.getHealth());

		if (level < 0 || kind == null || dealt <= 0) {
			return;
		}

		if (kind == Kind.GOAT && has(level, Slot.FIRST)) {
			HITS.computeIfAbsent(pet, k -> new int[1])[0]++;
		}

		if (has(level, Slot.FAST_FOOD) && pet.getHealth() < pet.getMaxHealth() && roll(pet, 50.0)) {
			pet.heal(dealt);
			((ServerLevel) pet.level()).sendParticles(ParticleTypes.HAPPY_VILLAGER, pet.getX(), pet.getY() + pet.getBbHeight() + 0.2, pet.getZ(),
					4, 0.3, 0.2, 0.3, 0.0);
		}

		switch (kind) {
			case POLAR_BEAR -> {
				if (has(level, Slot.SPECIAL)) {
					boolean high = crushHigh(level);
					target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, high ? 3 : 1), pet);
					target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, high ? 1 : 0), pet);
				}
			}
			case GOAT -> {
				if (RAM_NEXT.remove(pet) == target) {
					// After the hit's own knockback, or that would flatten the launch.
					LATER.add(() -> launch(pet, target));
				}
			}
			case PANDA -> {
				if (has(level, Slot.SPECIAL) && roll(pet, soothChance(level))) {
					soothe(pet, target, soothHeal(level));
				}
			}
			default -> {
			}
		}
	}

	/** Ram: the third hit throws the target high into the air (about 6 blocks). */
	private static void launch(LivingEntity goat, LivingEntity target) {
		if (!target.isAlive()) {
			return;
		}

		Vec3 away = new Vec3(target.getX() - goat.getX(), 0.0, target.getZ() - goat.getZ());
		away = away.lengthSqr() > 1.0E-4 ? away.normalize().scale(0.4) : Vec3.ZERO;
		target.setDeltaMovement(away.x, 1.1, away.z);
		target.hurtMarked = true;
		((ServerLevel) target.level()).sendParticles(ParticleTypes.CLOUD, target.getX(), target.getY(), target.getZ(), 10, 0.3, 0.1, 0.3, 0.05);
	}

	/** Soothing Presence: heals the panda, its owner and friends nearby, and hurts the undead monsters around it. */
	private static void soothe(LivingEntity panda, LivingEntity target, float amount) {
		ServerLevel level = (ServerLevel) panda.level();
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, panda.getX(), panda.getY() + 1.0, panda.getZ(), 24, SOOTHE_RADIUS / 2, 0.6,
				SOOTHE_RADIUS / 2, 0.0);
		level.playSound(null, panda.blockPosition(), SoundEvents.PANDA_EAT, SoundSource.NEUTRAL, 0.8F, 1.2F);
		heal(panda, amount);

		for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, panda.getBoundingBox().inflate(SOOTHE_RADIUS, SOOTHE_RADIUS / 2 + 1,
				SOOTHE_RADIUS))) {
			if (near == panda || near == target || !near.isAlive()) {
				continue;
			}

			if (near.getMobType() == MobType.UNDEAD) {
				// Undead monsters only: not someone's skeleton horse or a named mob.
				if (near instanceof Enemy && !near.hasCustomName() && !(near instanceof OwnableEntity owned && owned.getOwnerUUID() != null)) {
					near.hurt(level.damageSources().indirectMagic(panda, panda), amount);
				}
			} else if (near instanceof Player || PetXp.sameSide(panda, near)) {
				heal(near, amount);
			}
		}
	}

	private static void heal(LivingEntity entity, float amount) {
		if (amount > 0 && entity.getHealth() < entity.getMaxHealth()) {
			entity.heal(amount);
			((ServerLevel) entity.level()).sendParticles(ParticleTypes.HEART, entity.getX(), entity.getY() + entity.getBbHeight() + 0.2,
					entity.getZ(), 1, 0.2, 0.1, 0.2, 0.0);
		}
	}

	@SubscribeEvent
	public static void onServerTick(TickEvent.ServerTickEvent event) {
		if (event.phase == TickEvent.Phase.END && !LATER.isEmpty()) {
			List<Runnable> now = new ArrayList<>(LATER);
			LATER.clear();
			now.forEach(Runnable::run);
		}
	}

	// Getting hurt: the skills that turn a hit away.

	/** Awareness (fire, lava, cacti, falls), Thick Fur shaking off burning, Holy Hound (magic, poison, wither heal). */
	@SubscribeEvent
	public static void onAttacked(LivingAttackEvent event) {
		LivingEntity pet = event.getEntity();
		int level = skillLevel(pet);

		if (level < 0 || event.isCanceled()) {
			return;
		}

		DamageSource source = event.getSource();

		if (has(level, Slot.AWARENESS) && source.is(DamageTypeTags.IS_FALL)) {
			event.setCanceled(true);
		} else if (has(level, Slot.AWARENESS) && (source.is(DamageTypes.IN_FIRE) || source.is(DamageTypes.LAVA)
				|| source.is(DamageTypes.HOT_FLOOR) || source.is(DamageTypes.CACTUS))) {
			event.setCanceled(true);
			pet.clearFire();
			escape(pet);
		} else if (has(level, Slot.HOLY_HOUND) && (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC) || source.is(DamageTypes.WITHER))) {
			event.setCanceled(true);
			heal(pet, event.getAmount());
		} else if (has(level, Slot.THICK_FUR) && source.is(DamageTypes.ON_FIRE)) {
			pet.clearFire();
		}
	}

	/** Out of the danger, back to its owner's side (at most once a second). */
	private static void escape(LivingEntity pet) {
		ServerPlayer owner = owner(pet);
		CompoundTag data = Pets.data(pet);
		long now = pet.level().getGameTime();

		if (owner != null && owner.level() == pet.level() && now - data.getLong("escapedAt") >= 20) {
			data.putLong("escapedAt", now);
			Calling.bring(pet, owner);
		}
	}

	/** Unyielding: the polar bear can't be knocked back... */
	@SubscribeEvent
	public static void onKnockback(LivingKnockBackEvent event) {
		if (kind(event.getEntity()) == Kind.POLAR_BEAR && has(skillLevel(event.getEntity()), Slot.FIRST)) {
			event.setCanceled(true);
		}
	}

	/** ...or slowed (and polar bears don't freeze anyway). */
	@SubscribeEvent
	public static void onEffect(MobEffectEvent.Applicable event) {
		if (event.getEffectInstance().getEffect() == MobEffects.MOVEMENT_SLOWDOWN && kind(event.getEntity()) == Kind.POLAR_BEAR
				&& has(skillLevel(event.getEntity()), Slot.FIRST)) {
			event.setResult(Event.Result.DENY);
		}
	}

	/** Bamboo Blessing: when the panda wins a fight, it and its owner (within 32 blocks) get Regeneration I for 8 seconds. */
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onKill(LivingDeathEvent event) {
		if (event.isCanceled() || !(event.getSource().getEntity() instanceof LivingEntity pet) || kind(pet) != Kind.PANDA
				|| !has(skillLevel(pet), Slot.FIRST)) {
			return;
		}

		pet.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 160, 0));
		ServerPlayer owner = owner(pet);

		if (owner != null && owner.level() == pet.level() && owner.distanceToSqr(pet) < 32.0 * 32.0) {
			owner.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 160, 0));
		}
	}

	/**
	 * Guardian Intervention: once a Minecraft day, while its golem (level 20+) follows them, a blow that would kill a
	 * player leaves them at half a heart with Resistance II for 20 seconds. A totem in their hand goes first (the game
	 * uses it before this); the void, {@code /kill} and the world border can't be stopped. Public for the game tests.
	 */
	@SubscribeEvent(priority = EventPriority.LOW)
	public static void onOwnerDeath(LivingDeathEvent event) {
		DamageSource source = event.getSource();

		if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player) || source.is(DamageTypes.FELL_OUT_OF_WORLD)
				|| source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.OUTSIDE_BORDER)) {
			return;
		}

		PetRegistry.Record record = PetRegistry.get(player.server).partyPet(player.getUUID());
		Entity golem = record == null ? null : Pets.loaded(player.server, record.pet);

		if (!(golem instanceof LivingEntity living) || kind(golem) != Kind.IRON_GOLEM || !has(skillLevel(golem), Slot.SPECIAL)
				|| golem.level() != player.level() || golem.distanceToSqr(player) > GUARDIAN_RANGE * GUARDIAN_RANGE) {
			return;
		}

		long day = player.server.overworld().getDayTime() / 24000L;
		CompoundTag kept = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);

		if (kept.contains("bloompets.guardianDay") && kept.getLong("bloompets.guardianDay") == day) {
			return;
		}

		kept.putLong("bloompets.guardianDay", day);
		player.getPersistentData().put(Player.PERSISTED_NBT_TAG, kept);
		event.setCanceled(true);
		player.setHealth(1.0F);
		player.clearFire();
		player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 400, 1));
		ServerLevel level = player.serverLevel();
		level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.4, 0.6, 0.4, 0.3);
		level.playSound(null, player.blockPosition(), SoundEvents.IRON_GOLEM_REPAIR, SoundSource.PLAYERS, 1.0F, 0.8F);
		player.sendSystemMessage(Component.translatable("bloompets.guardian", living.getName()).withStyle(ChatFormatting.GOLD));
	}

	/** An icon for each skill, for the screen (items, so they need no art of their own). */
	public static net.minecraft.world.item.Item icon(Kind kind, Slot slot) {
		return switch (slot) {
			case FIRST -> kind == null ? Items.BARRIER : switch (kind) {
				case WOLF -> Items.BONE;
				case IRON_GOLEM -> Items.IRON_BLOCK;
				case POLAR_BEAR -> Items.SHIELD;
				case GOAT -> Items.SUGAR;
				case PANDA -> Items.BAMBOO;
			};
			case AWARENESS -> Items.COMPASS;
			case GORE -> Items.IRON_SWORD;
			case SPECIAL -> kind == null ? Items.BARRIER : switch (kind) {
				case WOLF -> Items.GOAT_HORN;
				case IRON_GOLEM -> Items.TOTEM_OF_UNDYING;
				case POLAR_BEAR -> Items.ANVIL;
				case GOAT -> Items.PISTON;
				case PANDA -> Items.GLISTERING_MELON_SLICE;
			};
			case FAST_FOOD -> Items.COOKED_BEEF;
			case THICK_FUR -> Items.LEATHER_CHESTPLATE;
			case HOLY_HOUND -> Items.GOLDEN_APPLE;
			case SHOCK_PROOF -> Items.LIGHTNING_ROD;
			case CLAWS -> Items.FLINT;
			case MAX -> Items.NETHER_STAR;
		};
	}
}
