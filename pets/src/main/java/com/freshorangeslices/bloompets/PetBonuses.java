package com.freshorangeslices.bloompets;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.LlamaSpit;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Each pet's bonus for its owner (docs/design/PETS.md, the roster), the plugin's numbers, a little stronger as the pet
 * levels up ({@link #power}: 1 at level 1, 1.45 at 100). Who gives one: the Bloom pet in the party, and up to three of
 * the owner's non-combat pets within 32 blocks of them, following or not (the nearest first: a fourth does nothing).
 * One of each kind counts (the highest level), so two Donkeys aren't four hearts. A sulking or fainted pet gives
 * nothing. The Bee and the Pig use Bloom's own Growth Aura (with saplings, no hoe) and Timber, so owning those Majors
 * too is no faster. Modded companions get the bonus of their Spirit with Phase 5.
 */
public final class PetBonuses {
	private static final double SUPPORT_RANGE = 32.0;
	private static final int SUPPORT_MAX = 3;
	private static final int EVERY = 10;
	private static final int XP_PER_BOTTLE = 7;
	private static final String TOSSED = "bloompets.tossed";
	/** Per player: who's giving them a bonus right now, by kind (the species' path: wolf, donkey...). */
	private static final Map<UUID, Map<String, Bonus>> ACTIVE = new HashMap<>();
	private static final Map<UUID, Long> COW_READY = new HashMap<>();
	private static final Map<UUID, Long> SPIT_READY = new HashMap<>();
	private static final Map<UUID, Long> SNIFF_READY = new HashMap<>();
	private static final Map<UUID, Long> DIG_NEXT = new HashMap<>();
	private static final Map<UUID, Deque<Long>> TRIMS_FOUND = new HashMap<>();
	private static final Map<UUID, Vec3> STILL_AT = new HashMap<>();
	private static final Map<UUID, Integer> STILL_FOR = new HashMap<>();
	private static final Map<UUID, Long> MOVED_AT = new HashMap<>();
	private static final Map<UUID, Long> SLEPT_AT = new HashMap<>();
	private static final List<long[]> LATER_TIMES = new ArrayList<>();
	private static final List<Runnable> LATER = new ArrayList<>();
	private static int tick;

	/** One pet giving its owner its kind's bonus, at this strength. */
	public record Bonus(LivingEntity pet, int level, double power) {
	}

	private record Stat(String kind, Attribute attribute, UUID id, AttributeModifier.Operation operation) {
	}

	private static final List<Stat> STATS = List.of(
			new Stat("wolf", Attributes.ATTACK_DAMAGE, UUID.fromString("3f4e1b0a-8d1c-4c55-9a3e-01b1007e0001"), AttributeModifier.Operation.MULTIPLY_TOTAL),
			new Stat("iron_golem", Attributes.ARMOR, UUID.fromString("3f4e1b0a-8d1c-4c55-9a3e-01b1007e0002"), AttributeModifier.Operation.ADDITION),
			new Stat("donkey", Attributes.MAX_HEALTH, UUID.fromString("3f4e1b0a-8d1c-4c55-9a3e-01b1007e0003"), AttributeModifier.Operation.ADDITION),
			new Stat("mule", Attributes.KNOCKBACK_RESISTANCE, UUID.fromString("3f4e1b0a-8d1c-4c55-9a3e-01b1007e0004"), AttributeModifier.Operation.ADDITION),
			new Stat("horse", Attributes.MOVEMENT_SPEED, UUID.fromString("3f4e1b0a-8d1c-4c55-9a3e-01b1007e0005"), AttributeModifier.Operation.MULTIPLY_TOTAL));

	private static final Set<Block> SANDY = Set.of(Blocks.SAND, Blocks.RED_SAND, Blocks.SUSPICIOUS_SAND, Blocks.SANDSTONE, Blocks.RED_SANDSTONE,
			Blocks.SOUL_SAND, Blocks.SOUL_SOIL, Blocks.SNOW, Blocks.SNOW_BLOCK, Blocks.POWDER_SNOW);
	/** Ground a player can dig up a Sniffer's find in. */
	private static final Set<Block> SNIFFABLE = Set.of(Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT, Blocks.PODZOL,
			Blocks.MYCELIUM, Blocks.MOSS_BLOCK, Blocks.MUD);
	/** Ground a Sniffer digs in by itself. */
	private static final Set<Block> DIGGABLE = Set.of(Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT, Blocks.PODZOL,
			Blocks.MYCELIUM, Blocks.MOSS_BLOCK, Blocks.MUD, Blocks.MUDDY_MANGROVE_ROOTS, Blocks.SAND, Blocks.RED_SAND, Blocks.GRAVEL, Blocks.CLAY,
			Blocks.FARMLAND, Blocks.DIRT_PATH, Blocks.SNOW_BLOCK, Blocks.SOUL_SOIL);
	/** Armor trims a Sniffer can dig up (1.20.1's), how likely each (common 3, uncommon 2, rare 1). */
	private static final Map<Item, Integer> TRIMS = new java.util.LinkedHashMap<>();
	private static final Item[] CAT_GIFTS = {Items.STRING, Items.FEATHER, Items.RABBIT_HIDE, Items.RABBIT_FOOT, Items.CHICKEN, Items.ROTTEN_FLESH,
			Items.PHANTOM_MEMBRANE};

	static {
		for (Item trim : List.of(Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE, Items.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE, Items.WILD_ARMOR_TRIM_SMITHING_TEMPLATE,
				Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE, Items.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE, Items.HOST_ARMOR_TRIM_SMITHING_TEMPLATE,
				Items.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE, Items.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE)) {
			TRIMS.put(trim, 3);
		}

		for (Item trim : List.of(Items.VEX_ARMOR_TRIM_SMITHING_TEMPLATE, Items.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE, Items.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE,
				Items.RIB_ARMOR_TRIM_SMITHING_TEMPLATE)) {
			TRIMS.put(trim, 2);
		}

		for (Item trim : List.of(Items.WARD_ARMOR_TRIM_SMITHING_TEMPLATE, Items.EYE_ARMOR_TRIM_SMITHING_TEMPLATE, Items.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE,
				Items.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE)) {
			TRIMS.put(trim, 1);
		}
	}

	private PetBonuses() {
	}

	/** How strong a pet's bonus is at its level: 1 at level 1, about 45% more at 100 (the plugin's top level's). */
	public static double power(int level) {
		return 1.0 + 0.45 * (Math.max(1, Math.min(PetLevels.MAX, level)) - 1) / (PetLevels.MAX - 1.0);
	}

	/** Who gives the player a bonus right now, by kind. Public for the game tests. */
	public static Map<String, Bonus> gather(ServerPlayer player) {
		Map<String, Bonus> out = new HashMap<>();
		List<LivingEntity> support = new ArrayList<>();

		for (PetRegistry.Record record : PetRegistry.get(player.server).pets(player.getUUID())) {
			if (record.kind != Pets.Kind.BLOOM || !(player.serverLevel().getEntity(record.pet) instanceof LivingEntity pet) || !pet.isAlive()
					|| Fainting.fainted(pet) || PetMood.sad(pet) || Pets.species(pet) == null) {
				continue;
			}

			if (Pets.inParty(pet)) {
				add(out, pet);
			} else if (!Pets.species(pet).combat() && pet.distanceToSqr(player) <= SUPPORT_RANGE * SUPPORT_RANGE) {
				support.add(pet);
			}
		}

		support.sort(Comparator.comparingDouble(pet -> pet.distanceToSqr(player)));
		support.stream().limit(SUPPORT_MAX).forEach(pet -> add(out, pet));
		return out;
	}

	private static void add(Map<String, Bonus> out, LivingEntity pet) {
		String kind = Pets.species(pet).id().getPath();
		int level = PetLevels.level(pet);

		if (!out.containsKey(kind) || out.get(kind).level() < level) {
			out.put(kind, new Bonus(pet, level, power(level)));
		}
	}

	/** The bonus of that kind the player has right now, or null. */
	static Bonus active(Entity player, String kind) {
		Map<String, Bonus> bonuses = ACTIVE.get(player.getUUID());
		return bonuses == null ? null : bonuses.get(kind);
	}

	@SubscribeEvent
	public static void onServerTick(TickEvent.ServerTickEvent event) {
		if (event.phase != TickEvent.Phase.END) {
			return;
		}

		MinecraftServer server = event.getServer();
		long now = server.overworld().getGameTime();
		runLater(now);

		if (++tick % EVERY != 0) {
			return;
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Map<String, Bonus> bonuses = player.isAlive() && !player.isSpectator() ? gather(player) : Map.of();
			ACTIVE.put(player.getUUID(), bonuses);
			apply(player, bonuses, now);
		}
	}

	/** Brings the player's bonuses up to date (every half second). Public for the game tests. */
	public static void apply(ServerPlayer player, Map<String, Bonus> bonuses, long now) {
		ACTIVE.put(player.getUUID(), bonuses);
		stats(player, bonuses);
		moved(player, now);

		for (Map.Entry<String, Bonus> entry : bonuses.entrySet()) {
			Bonus bonus = entry.getValue();
			double power = bonus.power();

			switch (entry.getKey()) {
				case "rabbit" -> effect(player, MobEffects.JUMP, bonus.level() >= 80 ? 1 : 0);
				case "strider" -> {
					if (player.level().dimension() == Level.NETHER) {
						effect(player, MobEffects.FIRE_RESISTANCE, 0);
					}
				}
				case "turtle" -> {
					if (player.isInWater()) {
						effect(player, MobEffects.WATER_BREATHING, 0);
					}
				}
				case "frog" -> {
					if (player.isInWater()) {
						effect(player, MobEffects.DOLPHINS_GRACE, 0);
					}
				}
				case "camel" -> {
					if (SANDY.contains(player.getBlockStateOn().getBlock()) || SANDY.contains(player.getFeetBlockState().getBlock())) {
						effect(player, MobEffects.MOVEMENT_SPEED, bonus.level() >= 80 ? 1 : 0);
					}
				}
				case "axolotl" -> {
					if (player.getHealth() < player.getMaxHealth()) {
						effect(player, MobEffects.REGENERATION, player.isInWater() ? 1 : 0);
					}
				}
				case "bat" -> {
					MobEffectInstance sight = player.getEffect(MobEffects.NIGHT_VISION);

					if (sight == null || !sight.isInfiniteDuration() && sight.getDuration() < 300) {
						player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, true, false, true));
					}
				}
				case "chicken" -> {
					if (player.fallDistance > 4.0F && !player.isFallFlying() && !player.getAbilities().flying && !player.isPassenger()) {
						effect(player, MobEffects.SLOW_FALLING, 0);
					}
				}
				case "polar_bear" -> player.setTicksFrozen(0);
				case "panda" -> {
					if (STILL_FOR.getOrDefault(player.getUUID(), 0) >= 60) {
						effect(player, MobEffects.REGENERATION, 0);
					}
				}
				case "cow" -> milk(player, bonus, now);
				case "allay" -> allay(player, bonus);
				case "sniffer" -> {
					long next = DIG_NEXT.computeIfAbsent(player.getUUID(), k -> now + digGap(power));

					if (now >= next) {
						DIG_NEXT.put(player.getUUID(), now + digGap(power));
						dig(player, bonus, now);
					}
				}
				default -> {
				}
			}
		}

		// The Bee and the Pig: Bloom's Growth Aura and Timber, kept on while they're near (see UtilitySkill).
		CompoundTag kept = player.getPersistentData().getCompound("bloompets");
		Bonus bee = bonuses.get("bee");
		Bonus pig = bonuses.get("pig");

		if (bee != null) {
			kept.putDouble("growth", bee.power());
			kept.putLong("growthUntil", now + 2 * EVERY + 1);
		}

		if (pig != null) {
			kept.putLong("timberUntil", now + 2 * EVERY + 1);
		}

		if (bee != null || pig != null || !kept.isEmpty()) {
			player.getPersistentData().put("bloompets", kept);
		}
	}

	/** The stat bonuses, as attribute modifiers on the owner (never saved; off as soon as the pet isn't giving it). */
	private static void stats(ServerPlayer player, Map<String, Bonus> bonuses) {
		for (Stat stat : STATS) {
			Bonus bonus = bonuses.get(stat.kind());
			double amount = bonus == null ? 0.0 : switch (stat.kind()) {
				case "wolf", "horse" -> 0.08 * bonus.power();
				case "iron_golem" -> 3.0 * bonus.power();
				case "donkey" -> 4.0 * bonus.power();
				case "mule" -> Math.min(0.6, 0.3 * bonus.power());
				default -> 0.0;
			};
			AttributeInstance instance = player.getAttribute(stat.attribute());

			if (instance == null) {
				continue;
			}

			AttributeModifier old = instance.getModifier(stat.id());

			if (old != null && Math.abs(old.getAmount() - amount) < 1.0E-6) {
				continue;
			}

			if (old != null) {
				instance.removeModifier(stat.id());
			}

			if (amount > 0) {
				instance.addTransientModifier(new AttributeModifier(stat.id(), "Bloom Pets: " + stat.kind(), amount, stat.operation()));
			}
		}

		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	/** A quiet effect (no swirl) that keeps topping itself up while the bonus is on. */
	private static void effect(ServerPlayer player, MobEffect type, int amplifier) {
		MobEffectInstance current = player.getEffect(type);

		if (current != null && (current.getAmplifier() > amplifier || current.isInfiniteDuration() || current.getDuration() > 60)) {
			return;
		}

		player.addEffect(new MobEffectInstance(type, 100, amplifier, true, false, true));
	}

	/** Whether the player moved this half second (the Panda's "stand still", the Sniffer's "not AFK"). */
	private static void moved(ServerPlayer player, long now) {
		Vec3 here = player.position();
		Vec3 before = STILL_AT.put(player.getUUID(), here);
		boolean still = before != null && before.distanceToSqr(here) < 0.0025;
		STILL_FOR.put(player.getUUID(), still ? STILL_FOR.getOrDefault(player.getUUID(), 0) + EVERY : 0);

		if (!still) {
			MOVED_AT.put(player.getUUID(), now);
		}
	}

	/** Cow: clears one bad effect, then needs 2 minutes (less as it levels). */
	private static void milk(ServerPlayer player, Bonus cow, long now) {
		if (now < COW_READY.getOrDefault(player.getUUID(), 0L)) {
			return;
		}

		for (MobEffectInstance effect : List.copyOf(player.getActiveEffects())) {
			if (effect.getEffect().getCategory() == MobEffectCategory.HARMFUL) {
				player.removeEffect(effect.getEffect());
				COW_READY.put(player.getUUID(), now + (long) (2400 / cow.power()));
				player.displayClientMessage(Component.translatable("bloompets.bonus.milk", cow.pet().getName(), effect.getEffect().getDisplayName()), true);
				player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 0.6F, 1.2F);
				return;
			}
		}
	}

	/** Allay: pulls drops (not ones a player threw) and loose XP to its owner; XP goes into its storage as bottles. */
	private static void allay(ServerPlayer player, Bonus allay) {
		Vec3 to = player.position().add(0.0, 0.6, 0.0);
		PetRegistry registry = PetRegistry.get(player.server);
		PetRegistry.Record record = registry.find(allay.pet().getUUID());
		SimpleContainer box = record == null || record.slots <= 0 ? null : new SimpleContainer(record.slots);

		if (box != null) {
			box.fromTag(record.items);
		}

		ItemStack bottle = new ItemStack(Items.EXPERIENCE_BOTTLE);
		boolean bottling = box != null && box.canAddItem(bottle);
		CompoundTag data = Pets.data(allay.pet());
		int bank = data.getInt("xpBank");

		for (Entity entity : player.level().getEntities(player, player.getBoundingBox().inflate(8.0, 4.0, 8.0),
				e -> e instanceof ItemEntity || e instanceof ExperienceOrb)) {
			if (entity instanceof ItemEntity item) {
				if (!item.hasPickUpDelay() && !item.getPersistentData().getBoolean(TOSSED)) {
					pull(item, to, 0.4);
				}
			} else if (entity instanceof ExperienceOrb orb) {
				if (bottling) {
					bank += orb.getValue();
					orb.discard();
				} else {
					pull(orb, to, 0.35);
				}
			}
		}

		boolean made = false;

		while (box != null && bank >= XP_PER_BOTTLE && box.canAddItem(bottle)) {
			box.addItem(bottle.copy());
			bank -= XP_PER_BOTTLE;
			made = true;
		}

		data.putInt("xpBank", bank);

		if (made) {
			record.items = box.createTag();
			registry.setDirty();
			allay.pet().playSound(SoundEvents.ALLAY_ITEM_GIVEN, 0.5F, 1.3F);
		}
	}

	private static void pull(Entity entity, Vec3 to, double speed) {
		Vec3 way = to.subtract(entity.position());
		double length = way.length();

		if (length >= 1.2) {
			entity.setDeltaMovement(way.scale(Math.min(speed, length * 0.4) / length).add(0.0, 0.04, 0.0));
			entity.hurtMarked = true;
		}
	}

	/** Items a player throws stay where they land (the Allay leaves them). */
	@SubscribeEvent
	public static void onToss(ItemTossEvent event) {
		event.getEntity().getPersistentData().putBoolean(TOSSED, true);
	}

	// The Sniffer.

	/** Four minutes between digs at level 1, under three at 100. */
	private static long digGap(double power) {
		return (long) (4800 / power);
	}

	/** Whether the player has moved in the last 5 minutes (no treasure for standing AFK). */
	static boolean awake(ServerPlayer player, long now) {
		return now - MOVED_AT.getOrDefault(player.getUUID(), now) <= 5 * 60 * 20;
	}

	/** The Sniffer's bonus: now and then it digs near its owner (within 12 blocks, while they aren't AFK). */
	private static void dig(ServerPlayer player, Bonus bonus, long now) {
		if (bonus.pet() instanceof Sniffer sniffer && sniffer.level() == player.level() && sniffer.distanceToSqr(player) <= 12.0 * 12.0
				&& awake(player, now)) {
			dig(sniffer, player, true, now);
		}
	}

	/**
	 * The Sniffer stops, sniffs, digs, and comes up with a find (its own animation plays), on ground it could dig in:
	 * seeds and flowers, and with {@code treasure} sometimes an armor trim. Returns whether it dug.
	 */
	static boolean dig(Sniffer sniffer, ServerPlayer owner, boolean treasure, long now) {
		if (!sniffer.onGround() || !DIGGABLE.contains(sniffer.getBlockStateOn().getBlock())) {
			return false;
		}

		sniffer.transitionTo(Sniffer.State.SNIFFING);
		later(now + 40, () -> {
			if (sniffer.isAlive()) {
				sniffer.transitionTo(Sniffer.State.DIGGING);
			}
		});
		later(now + 130, () -> {
			if (!sniffer.isAlive()) {
				return;
			}

			sniffer.transitionTo(Sniffer.State.RISING);
			Item found = treasure && owner != null ? treasure(owner.getUUID(), sniffer.getRandom(), now) : plainFind(sniffer.getRandom());
			double yaw = Math.toRadians(sniffer.getYRot());
			ItemEntity drop = new ItemEntity(sniffer.level(), sniffer.getX() - Math.sin(yaw) * 1.6, sniffer.getY() + 0.4,
					sniffer.getZ() + Math.cos(yaw) * 1.6, new ItemStack(found));
			drop.setDefaultPickUpDelay();
			sniffer.level().addFreshEntity(drop);
			sniffer.playSound(SoundEvents.SNIFFER_DROP_SEED, 1.0F, 1.0F);

			if (owner != null) {
				announce(owner, sniffer, found);
			}
		});
		later(now + 160, () -> {
			if (sniffer.isAlive()) {
				sniffer.transitionTo(Sniffer.State.IDLING);
			}
		});
		return true;
	}

	/** An armor trim one dig in five (rarer trims less often; at most three an hour), else seeds and flowers. */
	private static Item treasure(UUID owner, RandomSource random, long now) {
		Deque<Long> recent = TRIMS_FOUND.computeIfAbsent(owner, k -> new ArrayDeque<>());

		while (!recent.isEmpty() && now - recent.peekFirst() > 72000) {
			recent.pollFirst();
		}

		if (recent.size() < 3 && random.nextDouble() < 0.2) {
			int pick = random.nextInt(TRIMS.values().stream().mapToInt(Integer::intValue).sum());

			for (Map.Entry<Item, Integer> trim : TRIMS.entrySet()) {
				pick -= trim.getValue();

				if (pick < 0) {
					recent.addLast(now);
					return trim.getKey();
				}
			}
		}

		return plainFind(random);
	}

	/** Torchflower seeds, pitcher pods, other seeds, small flowers. */
	private static Item plainFind(RandomSource random) {
		double roll = random.nextDouble();

		if (roll < 0.3) {
			return Items.TORCHFLOWER_SEEDS;
		}

		if (roll < 0.5) {
			return Items.PITCHER_POD;
		}

		if (roll < 0.8) {
			Item[] seeds = {Items.WHEAT_SEEDS, Items.BEETROOT_SEEDS, Items.PUMPKIN_SEEDS, Items.MELON_SEEDS};
			return seeds[random.nextInt(seeds.length)];
		}

		List<Item> flowers = ForgeRegistries.BLOCKS.tags() == null ? List.of() : ForgeRegistries.BLOCKS.tags().getTag(BlockTags.SMALL_FLOWERS).stream()
				.filter(b -> b != Blocks.WITHER_ROSE).map(Block::asItem).filter(i -> i != Items.AIR).toList();
		return flowers.isEmpty() ? Items.POPPY : flowers.get(random.nextInt(flowers.size()));
	}

	private static void announce(ServerPlayer player, LivingEntity sniffer, Item found) {
		if (TRIMS.containsKey(found)) {
			player.sendSystemMessage(Component.translatable("bloompets.bonus.trim", sniffer.getName(), found.getDescription())
					.withStyle(ChatFormatting.LIGHT_PURPLE));
			player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.6F, 1.8F);
		} else {
			player.displayClientMessage(Component.translatable("bloompets.bonus.sniffed", sniffer.getName(), found.getDescription())
					.withStyle(ChatFormatting.GREEN), true);
		}
	}

	/** Sniffer: digging dirt yourself sometimes turns up a find (4% a block, at most one a minute; never a trim). */
	@SubscribeEvent
	public static void onBreak(BlockEvent.BreakEvent event) {
		if (!(event.getPlayer() instanceof ServerPlayer player) || player.isCreative() || !SNIFFABLE.contains(event.getState().getBlock())) {
			return;
		}

		Bonus bonus = active(player, "sniffer");
		long now = player.server.overworld().getGameTime();

		if (bonus == null || now < SNIFF_READY.getOrDefault(player.getUUID(), 0L) || player.getRandom().nextDouble() >= 0.04 * bonus.power()) {
			return;
		}

		SNIFF_READY.put(player.getUUID(), now + 1200);
		Item found = plainFind(player.getRandom());
		BlockPos pos = event.getPos();
		ItemEntity drop = new ItemEntity(player.level(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, new ItemStack(found));
		drop.setDefaultPickUpDelay();
		player.level().addFreshEntity(drop);
		bonus.pet().playSound(SoundEvents.SNIFFER_HAPPY, 0.8F, 1.0F);
		announce(player, bonus.pet(), found);
	}

	private static void later(long when, Runnable task) {
		LATER_TIMES.add(new long[] {when});
		LATER.add(task);
	}

	private static void runLater(long now) {
		for (int i = LATER.size() - 1; i >= 0; i--) {
			if (LATER_TIMES.get(i)[0] <= now) {
				Runnable task = LATER.remove(i);
				LATER_TIMES.remove(i);
				task.run();
			}
		}
	}

	// Bonuses that answer to something happening.

	/** Goat: softer landings. Llama: spits at whatever hits you. A pet llama's spit stings more as it levels. */
	@SubscribeEvent(priority = EventPriority.HIGH)
	public static void onHurt(LivingHurtEvent event) {
		LivingEntity target = event.getEntity();

		if (target.level().isClientSide()) {
			return;
		}

		if (event.getSource().getDirectEntity() instanceof LlamaSpit spit && spit.getOwner() instanceof Llama llama && Pets.listed(llama)) {
			event.setAmount((float) (2.0 * power(PetLevels.level(llama))));
			return;
		}

		if (!(target instanceof ServerPlayer player)) {
			return;
		}

		Bonus goat = active(player, "goat");

		if (goat != null && event.getSource().is(DamageTypeTags.IS_FALL)) {
			event.setAmount((float) (event.getAmount() * Math.max(0.3, 1.0 - 0.4 * goat.power())));
		}

		Bonus spitter = active(player, "llama");

		if (spitter != null && event.getSource().getEntity() instanceof Mob attacker && !Pets.listed(attacker)) {
			long now = player.server.overworld().getGameTime();

			if (now >= SPIT_READY.getOrDefault(player.getUUID(), 0L)) {
				SPIT_READY.put(player.getUUID(), now + 20);
				spit(spitter.pet(), attacker);
			}
		}
	}

	/** The llama spits at a mob within 16 blocks of it. Public for posts (a llama at its post spits at monsters). */
	public static void spit(LivingEntity pet, LivingEntity target) {
		if (!(pet instanceof Llama llama) || llama.level() != target.level() || llama.distanceToSqr(target) > 16.0 * 16.0) {
			return;
		}

		LlamaSpit spit = new LlamaSpit(llama.level(), llama);
		double dx = target.getX() - llama.getX();
		double dy = target.getY(0.3333) - spit.getY();
		double dz = target.getZ() - llama.getZ();
		spit.shoot(dx, dy + Math.sqrt(dx * dx + dz * dz) * 0.2, dz, 1.5F, 10.0F);
		llama.level().addFreshEntity(spit);
		llama.lookAt(target, 30.0F, 30.0F);
		llama.level().playSound(null, llama.blockPosition(), SoundEvents.LLAMA_SPIT, SoundSource.NEUTRAL, 1.0F, 1.0F);
	}

	/** Fox: mobs notice you from a shorter range. Ocelot and Cat: creepers don't come for you, phantoms leave you be. */
	@SubscribeEvent
	public static void onTarget(LivingChangeTargetEvent event) {
		if (!(event.getNewTarget() instanceof ServerPlayer player) || !(event.getEntity() instanceof Mob mob) || Pets.listed(mob)) {
			return;
		}

		// Whoever the player hit may come for them all the same.
		boolean provoked = mob.getLastHurtByMob() == player;
		Bonus fox = active(player, "fox");

		if (fox != null && !provoked) {
			AttributeInstance follow = mob.getAttribute(Attributes.FOLLOW_RANGE);
			double range = (follow == null ? 16.0 : follow.getValue()) * Math.max(0.5, 1.0 - 0.25 * fox.power());

			if (mob.distanceToSqr(player) > range * range) {
				event.setCanceled(true);
				return;
			}
		}

		if ((active(player, "cat") != null || active(player, "ocelot") != null) && (mob instanceof Creeper && !provoked || mob instanceof Phantom)) {
			event.setCanceled(true);
		}
	}

	/** Polar bear: Slowness can't touch you. */
	@SubscribeEvent
	public static void onEffect(MobEffectEvent.Applicable event) {
		if (event.getEntity() instanceof ServerPlayer player && event.getEffectInstance().getEffect() == MobEffects.MOVEMENT_SLOWDOWN
				&& active(player, "polar_bear") != null) {
			event.setResult(Event.Result.DENY);
		}
	}

	@SubscribeEvent
	public static void onSleep(PlayerSleepInBedEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			SLEPT_AT.put(player.getUUID(), player.server.overworld().getDayTime());
		}
	}

	/** Cat and Ocelot: after a night's sleep you often wake up to a little gift. */
	@SubscribeEvent
	public static void onWake(PlayerWakeUpEvent event) {
		if (!(event.getEntity() instanceof ServerPlayer player)) {
			return;
		}

		Long slept = SLEPT_AT.remove(player.getUUID());
		Bonus cat = active(player, "cat") != null ? active(player, "cat") : active(player, "ocelot");

		// Only when the night really passed (the clock jumped), and seven times in ten.
		if (cat == null || slept == null || player.server.overworld().getDayTime() - slept < 1000 || player.getRandom().nextDouble() >= 0.7) {
			return;
		}

		ItemStack gift = new ItemStack(CAT_GIFTS[player.getRandom().nextInt(CAT_GIFTS.length)]);
		Component name = gift.getHoverName();

		if (!player.getInventory().add(gift)) {
			player.drop(gift, false);
		}

		player.playNotifySound(SoundEvents.CAT_PURR, SoundSource.NEUTRAL, 1.0F, 1.0F);
		player.displayClientMessage(Component.translatable("bloompets.bonus.gift", cat.pet().getName(), name).withStyle(ChatFormatting.LIGHT_PURPLE), true);
	}

	@SubscribeEvent
	public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		UUID id = event.getEntity().getUUID();
		ACTIVE.remove(id);
		STILL_AT.remove(id);
		STILL_FOR.remove(id);
		MOVED_AT.remove(id);
		SPIT_READY.remove(id);
		SLEPT_AT.remove(id);
		DIG_NEXT.remove(id);
	}
}
