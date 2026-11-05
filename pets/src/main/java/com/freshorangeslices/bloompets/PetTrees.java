package com.freshorangeslices.bloompets;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * One and the Same (docs/design/PETS.md): with the Bloom Major on, the player picks a weapon tree for each combat pet
 * and companion in the pet screen's Bloom view. The pet's own level opens its tree's four effects (levels 10, 25, 50,
 * 75; the same table for everyone), which work beside its own skills while it's in the party or at its post (a
 * companion: always). The pick stays: a reset puts a Bloom pet back to level 1, and makes a companion wait 5 hours
 * before picking again. With the Major off (or gone) every tree rests, nothing lost. Bloom's effects, on a pet's own
 * melee hits; a pet's Execute finishes nothing, it hits 30% harder on a boss under half health and anything else under
 * 70%; the cooldown abilities (Charge Attack, Leap, Lightning Summon) come every 25 seconds and switch off one by one.
 */
public final class PetTrees {
	private static final long COOLDOWN = 500;
	private static final long COMPANION_WAIT = 5L * 60 * 60 * 20;
	/** Per pet: its stacks and timers (Predator, Combo, Bloodlust, Aura Sword's count, the cooldown abilities). */
	private static final Map<Entity, State> STATES = new WeakHashMap<>();
	/** Damage this class deals itself (a sweep, a spark, a splash): none of it sets off more. */
	private static int splashing;

	private PetTrees() {
	}

	/** The four trees. */
	public enum Tree {
		SWORD(Items.IRON_SWORD), AXE(Items.IRON_AXE), TRIDENT(Items.TRIDENT), UNARMED(Items.PLAYER_HEAD);

		public final Item icon;

		Tree(Item icon) {
			this.icon = icon;
		}

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		public List<Effect> effects() {
			return java.util.Arrays.stream(Effect.values()).filter(effect -> effect.tree.equals(id())).toList();
		}

		public static Tree byId(String id) {
			for (Tree tree : values()) {
				if (tree.id().equals(id)) {
					return tree;
				}
			}

			return null;
		}
	}

	/** Each tree's four effects, the level each opens at, Bloom's icon for it, and whether it's a cooldown ability. */
	public enum Effect {
		SLASH("sword", 10, "slash", false), FULL_SWEEP("sword", 25, "full_sweep", false), EXECUTE("sword", 50, "execute", false),
		AURA_SWORD("sword", 75, "aura_sword", false),
		ARMOR_BREAK("axe", 10, "armor_break", false), GUARANTEED_CRITICAL("axe", 25, "guaranteed_critical", false),
		CHARGE_ATTACK("axe", 50, "charge_attack", true), LEAP("axe", 75, "death_from_above", true),
		LIGHTNING_SHOCK("trident", 10, "lightning_shock", false), PREDATOR("trident", 25, "predator", false),
		LIGHTNING_SUMMON("trident", 50, "lightning_summon", true), OVERKILL("trident", 75, "overkill", false),
		BRAWLER("unarmed", 10, "brawler", false), COMBO("unarmed", 25, "combo", false), BLOODLUST("unarmed", 50, "bloodlust", false),
		DISARM("unarmed", 75, "disarm", false);

		public final String tree;
		public final int level;
		/** Bloom's icon (textures/icon/major/combat/&lt;icon&gt;.png). */
		public final String icon;
		public final boolean cooldown;

		Effect(String tree, int level, String icon, boolean cooldown) {
			this.tree = tree;
			this.level = level;
			this.icon = icon;
			this.cooldown = cooldown;
		}

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		public static Effect byId(String id) {
			for (Effect effect : values()) {
				if (effect.id().equals(id)) {
					return effect;
				}
			}

			return null;
		}
	}

	private static final class State {
		UUID preyId;
		int prey;
		long preyAt;
		int combo;
		long comboAt;
		int lust;
		long lustUntil;
		int hits;
		long shockAt;
		boolean charged;
		long chargeAt;
		long leapAt;
		long leapHitUntil;
		long summonAt;
	}

	private static State state(Entity pet) {
		return STATES.computeIfAbsent(pet, k -> new State());
	}

	public static boolean splashing() {
		return splashing > 0;
	}

	// The pick.

	/** Combat Bloom pets and modded companions can follow a tree (non-combat pets have their bonus and job). */
	public static boolean eligible(Entity pet) {
		Pets.Kind kind = Pets.kind(pet);
		Species species = Pets.species(pet);
		return kind == Pets.Kind.MODDED || kind == Pets.Kind.BLOOM && species != null && species.combat();
	}

	public static Tree tree(Entity pet) {
		CompoundTag data = Pets.dataOrNull(pet);
		return data == null ? null : Tree.byId(data.getString("tree"));
	}

	public static Set<String> abilitiesOff(Entity pet) {
		CompoundTag data = Pets.dataOrNull(pet);
		Set<String> off = new HashSet<>();

		if (data != null) {
			for (String id : data.getString("abilitiesOff").split(",")) {
				if (!id.isEmpty()) {
					off.add(id);
				}
			}
		}

		return off;
	}

	private static boolean off(Entity pet, Effect effect) {
		return abilitiesOff(pet).contains(effect.id());
	}

	/** Brings the cached "has One and the Same" up to date for a player (online, or a test's fake one). */
	public static void remember(ServerPlayer player) {
		PetRegistry.get(player.server).setOneAndTheSame(player.getUUID(), BloomBridge.oneAndTheSame(player));
	}

	/** Whether the pet's owner has One and the Same on (as last seen while they were online). */
	static boolean ownerHas(Entity pet) {
		UUID owner = Pets.owner(pet);
		return owner != null && pet.getServer() != null && PetRegistry.get(pet.getServer()).oneAndTheSame(owner);
	}

	/** The pet's level if its tree works right now (picked, the Major on, the pet in its party or at its post, awake), else -1. */
	public static int treeLevel(Entity pet) {
		if (!(pet instanceof LivingEntity) || pet.level().isClientSide() || tree(pet) == null || !eligible(pet) || !ownerHas(pet)) {
			return -1;
		}

		if (Pets.kind(pet) == Pets.Kind.BLOOM && (!Pets.inParty(pet) && !PetPosts.posted(pet) || Fainting.fainted(pet) || PetMood.sad(pet))) {
			return -1;
		}

		return PetLevels.level(pet);
	}

	private static boolean has(int level, Effect effect, Tree tree) {
		return level >= effect.level && effect.tree.equals(tree.id());
	}

	private static long now(Entity pet) {
		return pet.getServer() != null ? pet.getServer().overworld().getGameTime() : pet.level().getGameTime();
	}

	/** The player picks the pet's tree (it has to be loaded: the pet carries it). Returns what to tell them. */
	public static Component pick(ServerPlayer player, PetRegistry.Record record, String treeId) {
		Tree tree = Tree.byId(treeId);
		Entity pet = Pets.loaded(player.server, record.pet);

		if (tree == null) {
			return null;
		}

		if (!BloomBridge.oneAndTheSame(player)) {
			return Component.translatable("bloompets.tree.needs");
		}

		if (!(pet instanceof LivingEntity)) {
			return Component.translatable("bloompets.post.away", record.name);
		}

		if (!eligible(pet)) {
			return Component.translatable("bloompets.tree.combat", record.name);
		}

		if (tree(pet) != null) {
			return Component.translatable("bloompets.tree.already", record.name);
		}

		long wait = Pets.data(pet).getLong("treeWait") - now(pet);

		if (wait > 0) {
			return Component.translatable("bloompets.tree.wait", record.name, clock(wait));
		}

		Pets.data(pet).putString("tree", tree.id());
		remember(player);
		PetEvents.observe(pet);
		player.playNotifySound(SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.8F, 1.0F);
		return Component.translatable("bloompets.tree.picked", record.name, Component.translatable("bloompets.tree." + tree.id()))
				.withStyle(ChatFormatting.GOLD);
	}

	/** The player resets the pet's tree: a Bloom pet goes back to level 1; a companion can't pick again for 5 hours. */
	public static Component reset(ServerPlayer player, PetRegistry.Record record) {
		Entity pet = Pets.loaded(player.server, record.pet);

		if (!(pet instanceof LivingEntity living)) {
			return Component.translatable("bloompets.post.away", record.name);
		}

		if (tree(pet) == null) {
			return null;
		}

		CompoundTag data = Pets.data(pet);
		data.remove("tree");
		data.remove("abilitiesOff");
		STATES.remove(pet);

		if (Pets.kind(pet) == Pets.Kind.MODDED) {
			data.putLong("treeWait", now(pet) + COMPANION_WAIT);
			PetEvents.observe(pet);
			return Component.translatable("bloompets.tree.reset.companion", record.name);
		}

		PetLevels.setLevel(living, 1);
		PetEvents.observe(pet);
		return Component.translatable("bloompets.tree.reset", record.name);
	}

	/** Switches one of the pet's cooldown abilities off, or back on. */
	public static Component toggle(ServerPlayer player, PetRegistry.Record record, String effectId) {
		Effect effect = Effect.byId(effectId);
		Entity pet = Pets.loaded(player.server, record.pet);

		if (effect == null || !effect.cooldown || !(pet instanceof LivingEntity)) {
			return null;
		}

		Set<String> off = abilitiesOff(pet);
		boolean nowOff = off.add(effect.id()) || !off.remove(effect.id());
		Pets.data(pet).putString("abilitiesOff", String.join(",", off));
		PetEvents.observe(pet);
		return Component.translatable(nowOff ? "bloompets.tree.ability.off" : "bloompets.tree.ability.on", record.name,
				Component.translatable("bloompets.effect." + effect.id()));
	}

	private static String clock(long ticks) {
		long seconds = (ticks + 19) / 20;
		return String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
	}

	// The effects.

	/** Someone the pet's tree may splash onto: a monster, or what it's fighting; never a player, nor its owner's side. */
	private static boolean foe(LivingEntity pet, LivingEntity other) {
		return other != pet && other.isAlive() && !(other instanceof Player) && !PetXp.sameSide(pet, other)
				&& (other instanceof Enemy || pet instanceof Mob mob && mob.getTarget() == other);
	}

	private static void splash(LivingEntity pet, LivingEntity victim, float amount) {
		if (amount <= 0 || !victim.isAlive()) {
			return;
		}

		splashing++;

		try {
			victim.hurt(pet.damageSources().mobAttack(pet), amount);
		} finally {
			splashing--;
		}
	}

	private static List<LivingEntity> foesNear(LivingEntity pet, Entity around, double radius, LivingEntity except) {
		List<LivingEntity> out = new ArrayList<>(pet.level().getEntitiesOfClass(LivingEntity.class, around.getBoundingBox().inflate(radius),
				other -> other != except && foe(pet, other) && other.distanceToSqr(around) <= radius * radius));
		out.sort(Comparator.comparingDouble(other -> other.distanceToSqr(around)));
		return out;
	}

	/**
	 * A pet's own blow before armor, with its tree's effects: Execute, Guaranteed Critical, a charged hit, a leap's
	 * landing, Predator, Brawler, Combo, Bloodlust. Public for the game tests.
	 */
	public static float attack(LivingEntity pet, LivingEntity target, int level, float amount) {
		Tree tree = tree(pet);

		if (tree == null || level < 0) {
			return amount;
		}

		State st = state(pet);
		long now = now(pet);
		float damage = amount;

		switch (tree) {
			case SWORD -> {
				if (has(level, Effect.EXECUTE, tree) && target.getHealth() < target.getMaxHealth() * (BloomBridge.boss(target) ? 0.5F : 0.7F)) {
					damage *= 1.3F;
				}
			}
			case AXE -> {
				if (has(level, Effect.GUARANTEED_CRITICAL, tree)) {
					damage *= 1.35F;
					particles(target, ParticleTypes.CRIT, 8);
				}

				if (has(level, Effect.CHARGE_ATTACK, tree) && st.charged) {
					damage *= 2.0F;
					st.charged = false;
					st.chargeAt = now + COOLDOWN;
					pet.level().playSound(null, pet.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.NEUTRAL, 1.0F, 0.8F);
				}

				if (has(level, Effect.LEAP, tree) && now < st.leapHitUntil) {
					damage *= 1.5F;
					st.leapHitUntil = 0;
				}
			}
			case TRIDENT -> {
				if (has(level, Effect.PREDATOR, tree)) {
					st.prey = target.getUUID().equals(st.preyId) && now - st.preyAt <= 100 ? Math.min(5, st.prey + 1) : 0;
					st.preyId = target.getUUID();
					st.preyAt = now;
					damage *= 1.0F + 0.1F * st.prey;
				}
			}
			case UNARMED -> {
				if (has(level, Effect.BRAWLER, tree)) {
					damage += 3.0F;
				}

				if (has(level, Effect.COMBO, tree)) {
					if (now - st.comboAt > 60) {
						st.combo = 0;
					}

					damage += 0.25F * st.combo;
					st.combo = Math.min(20, st.combo + 1);
					st.comboAt = now;
				}

				if (has(level, Effect.BLOODLUST, tree) && now < st.lustUntil) {
					damage *= 1.0F + 0.08F * st.lust;
				}
			}
		}

		return damage;
	}

	@SubscribeEvent
	public static void onHurt(LivingHurtEvent event) {
		LivingEntity target = event.getEntity();

		if (splashing() || target.level().isClientSide() || !(event.getSource().getDirectEntity() instanceof LivingEntity pet)
				|| event.getSource().getEntity() != pet || pet == target) {
			return;
		}

		int level = treeLevel(pet);

		if (level >= 0) {
			event.setAmount(attack(pet, target, level, event.getAmount()));
		}
	}

	/** After a pet's blow landed: Slash, Full Sweep, Aura Sword, Armor Break, Lightning Shock, Overkill, Disarm. */
	@SubscribeEvent(priority = EventPriority.LOW)
	public static void onDamage(LivingDamageEvent event) {
		LivingEntity target = event.getEntity();

		if (splashing() || target.level().isClientSide() || !(event.getSource().getDirectEntity() instanceof LivingEntity pet)
				|| event.getSource().getEntity() != pet || pet == target) {
			return;
		}

		int level = treeLevel(pet);
		Tree tree = tree(pet);
		float dealt = Math.min(event.getAmount(), target.getHealth());

		if (level < 0 || tree == null || dealt <= 0) {
			return;
		}

		State st = state(pet);
		long now = now(pet);

		switch (tree) {
			case SWORD -> {
				MobEffect bleed = BloomBridge.bleed();

				if (has(level, Effect.SLASH, tree) && bleed != null && pet.getRandom().nextFloat() < 0.5F) {
					target.addEffect(new MobEffectInstance(bleed, 60, 0, false, false, true), pet);
				}

				if (has(level, Effect.FULL_SWEEP, tree)) {
					particles(target, ParticleTypes.SWEEP_ATTACK, 1);

					for (LivingEntity near : foesNear(pet, target, 1.5, target)) {
						splash(pet, near, 1.0F + dealt);
					}
				}

				if (has(level, Effect.AURA_SWORD, tree) && ++st.hits % 5 == 0) {
					aura(pet, dealt);
				}
			}
			case AXE -> {
				if (has(level, Effect.ARMOR_BREAK, tree)) {
					int wear = (int) Math.ceil(dealt / 2.0F);

					for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
						ItemStack armor = target.getItemBySlot(slot);

						if (armor.isDamageableItem()) {
							armor.hurtAndBreak(wear, target, broken -> broken.broadcastBreakEvent(slot));
						}
					}
				}
			}
			case TRIDENT -> {
				if (has(level, Effect.LIGHTNING_SHOCK, tree) && now >= st.shockAt) {
					List<LivingEntity> foes = foesNear(pet, target, 6.0, target);

					if (!foes.isEmpty()) {
						st.shockAt = now + 40;

						for (LivingEntity near : foes.subList(0, Math.min(3, foes.size()))) {
							spark(target, near);
							near.invulnerableTime = 0;
							splash(pet, near, dealt * 0.5F);
						}
					}
				}

				// Overkill: what the killing blow had beyond the target's health splashes onto the nearest enemies.
				float excess = event.getAmount() - target.getHealth();

				if (has(level, Effect.OVERKILL, tree) && excess > 0) {
					List<LivingEntity> foes = foesNear(pet, target, 4.0, target);
					List<LivingEntity> hit = foes.subList(0, Math.min(3, foes.size()));

					for (LivingEntity near : hit) {
						splash(pet, near, excess * 0.6F / hit.size());
					}

					particles(target, ParticleTypes.CRIT, 20);
				}
			}
			case UNARMED -> {
				if (has(level, Effect.DISARM, tree) && target instanceof Mob mob && !mob.getMainHandItem().isEmpty()
						&& pet.getRandom().nextFloat() < 0.3F) {
					mob.spawnAtLocation(mob.getMainHandItem().copy());
					mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
					pet.level().playSound(null, target.blockPosition(), SoundEvents.ITEM_BREAK, SoundSource.HOSTILE, 0.6F, 1.4F);
				}
			}
		}
	}

	/** Aura Sword: a sweep rolls out around the pet, 4 blocks, at its hit's strength. */
	private static void aura(LivingEntity pet, float strength) {
		if (pet.level() instanceof ServerLevel level) {
			for (int i = 0; i < 16; i++) {
				double angle = Math.PI * 2 * i / 16;
				level.sendParticles(ParticleTypes.SWEEP_ATTACK, pet.getX() + Math.cos(angle) * 3.0, pet.getY() + 0.5,
						pet.getZ() + Math.sin(angle) * 3.0, 1, 0.0, 0.0, 0.0, 0.0);
			}

			level.playSound(null, pet.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.NEUTRAL, 1.0F, 0.9F);
		}

		for (LivingEntity near : foesNear(pet, pet, 4.0, null)) {
			splash(pet, near, strength);
		}
	}

	/** Bloodlust: each kill, +8% damage for 10 seconds, up to 5 stacks. */
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onKill(LivingDeathEvent event) {
		if (event.isCanceled() || !(event.getSource().getEntity() instanceof LivingEntity pet) || tree(pet) != Tree.UNARMED) {
			return;
		}

		int level = treeLevel(pet);

		if (has(level, Effect.BLOODLUST, Tree.UNARMED)) {
			State st = state(pet);
			st.lust = Math.min(5, now(pet) < st.lustUntil ? st.lust + 1 : 1);
			st.lustUntil = now(pet) + 200;
		}
	}

	/** Once a second for a loaded pet: its cooldown abilities (Charge Attack readies, Leap, Lightning Summon). */
	static void second(LivingEntity pet) {
		int level = treeLevel(pet);
		Tree tree = tree(pet);

		if (level < 0 || tree == null) {
			return;
		}

		State st = state(pet);
		long now = now(pet);
		LivingEntity target = pet instanceof Mob mob ? mob.getTarget() : null;
		boolean fighting = target != null && target.isAlive() && target.level() == pet.level();

		if (has(level, Effect.CHARGE_ATTACK, tree) && !off(pet, Effect.CHARGE_ATTACK)) {
			if (!st.charged && now >= st.chargeAt) {
				st.charged = true;
			}

			if (st.charged) {
				particles(pet, ParticleTypes.ELECTRIC_SPARK, 3);
			}
		}

		if (fighting && has(level, Effect.LEAP, tree) && !off(pet, Effect.LEAP) && now >= st.leapAt && pet.onGround()) {
			double distance = pet.distanceTo(target);

			if (distance >= 4.0 && distance <= 12.0) {
				Vec3 way = target.position().subtract(pet.position()).multiply(1.0, 0.0, 1.0).normalize();
				pet.setDeltaMovement(way.x * Math.min(1.6, distance * 0.16), 0.65, way.z * Math.min(1.6, distance * 0.16));
				pet.hurtMarked = true;
				st.leapAt = now + COOLDOWN;
				st.leapHitUntil = now + 60;
				pet.level().playSound(null, pet.blockPosition(), SoundEvents.GOAT_LONG_JUMP, SoundSource.NEUTRAL, 1.0F, 1.0F);
			}
		}

		if (fighting && has(level, Effect.LIGHTNING_SUMMON, tree) && !off(pet, Effect.LIGHTNING_SUMMON) && now >= st.summonAt
				&& pet.level() instanceof ServerLevel server) {
			st.summonAt = now + COOLDOWN;
			// Only the look of lightning: the damage goes to its target alone (never its owner, their pets, or the house).
			LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(server);

			if (bolt != null) {
				bolt.moveTo(target.getX(), target.getY(), target.getZ());
				bolt.setVisualOnly(true);
				server.addFreshEntity(bolt);
			}

			target.invulnerableTime = 0;
			splash(pet, target, 6.0F);
			target.setSecondsOnFire(3);
		}
	}

	private static void spark(Entity from, Entity to) {
		if (!(from.level() instanceof ServerLevel level)) {
			return;
		}

		Vec3 a = from.position().add(0.0, from.getBbHeight() * 0.5, 0.0);
		Vec3 b = to.position().add(0.0, to.getBbHeight() * 0.5, 0.0);

		for (int i = 0; i <= 8; i++) {
			Vec3 at = a.lerp(b, i / 8.0);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 1, 0.05, 0.05, 0.05, 0.0);
		}
	}

	private static void particles(Entity at, net.minecraft.core.particles.SimpleParticleType type, int count) {
		if (at.level() instanceof ServerLevel level) {
			level.sendParticles(type, at.getX(), at.getY() + at.getBbHeight() * 0.6, at.getZ(), count, at.getBbWidth() * 0.4, 0.2,
					at.getBbWidth() * 0.4, 0.05);
		}
	}

	/** Every second: who has One and the Same on (so a pet keeps its tree while its owner is away, as they left it). */
	@SubscribeEvent
	public static void onServerTick(TickEvent.ServerTickEvent event) {
		MinecraftServer server = event.getServer();

		if (event.phase == TickEvent.Phase.END && server.getTickCount() % 20 == 0) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				remember(player);
			}
		}
	}
}
