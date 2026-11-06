package com.freshorangeslices.bloompets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Posts (the plugin's stations, docs/design/PETS.md): from level 8 a Bloom pet out of the party can be left at a spot
 * to work there, three posts a player at most. It strolls around its post (6 blocks) and does its kind's job: combat
 * pets guard (monsters within 16 blocks), the Bee tends crops and saplings, the Allay collects drops and loose XP into
 * its storage, the Sniffer digs up finds, the Llama spits at monsters, the Cat and Ocelot keep creepers and phantoms
 * away from everyone near it; the rest just wait. A pet at its post uses its skills and faints rather than dying, its
 * mood drops a point every 2 minutes while its owner is online, and it earns XP from its job (30 a minute at most; a
 * guard earns its fighting XP as in the party). At level 100 its area is half again as big.
 */
public final class PetPosts {
	public static final int ROAM = 6;
	private static final double GUARD = 16.0;
	private static final int JOB_XP_PER_MINUTE = 30;
	private static final double CAT_RANGE = 24.0;
	/** Cats and ocelots at their posts (loaded), with where and how far they reach, seen in the last few seconds. */
	private static final Map<UUID, Watch> WATCHES = new ConcurrentHashMap<>();

	private record Watch(ResourceKey<Level> dimension, BlockPos post, double range, long seen) {
	}

	private PetPosts() {
	}

	public static BlockPos post(Entity pet) {
		CompoundTag data = Pets.dataOrNull(pet);
		return data != null && data.contains("post", Tag.TAG_LONG) ? BlockPos.of(data.getLong("post")) : null;
	}

	public static boolean posted(Entity pet) {
		return post(pet) != null;
	}

	private static boolean maxed(Entity pet) {
		return PetLevels.level(pet) >= PetLevels.MAX;
	}

	/** How far a guard keeps monsters away from its post. */
	public static double guardRadius(Entity pet) {
		return GUARD * (maxed(pet) ? 1.5 : 1.0);
	}

	private static long now(Entity pet) {
		return pet.getServer() != null ? pet.getServer().overworld().getGameTime() : pet.level().getGameTime();
	}

	/** Leaves the pet at its post where it stands, or calls it off its post. Returns what to tell the player. */
	public static Component toggle(ServerPlayer player, PetRegistry.Record record) {
		if (record.kind != Pets.Kind.BLOOM) {
			return Component.translatable("bloompets.post.modded", record.name);
		}

		Entity entity = Pets.loaded(player.server, record.pet);

		if (record.post != null) {
			if (entity != null) {
				leave(entity);
			}

			record.post = null;
			PetRegistry.get(player.server).setDirty();
			return Component.translatable("bloompets.post.left", record.name);
		}

		if (!(entity instanceof Mob mob)) {
			return Component.translatable("bloompets.post.away", record.name);
		}

		if (record.party) {
			return Component.translatable("bloompets.post.party", record.name);
		}

		if (PetLevels.level(mob) < PetsConfig.postLevel()) {
			return Component.translatable("bloompets.post.level", record.name, PetsConfig.postLevel(), PetLevels.level(mob));
		}

		long posts = PetRegistry.get(player.server).pets(player.getUUID()).stream().filter(r -> r.post != null).count();

		if (posts >= PetsConfig.maxPosts()) {
			return Component.translatable("bloompets.post.full", PetsConfig.maxPosts());
		}

		if (!mob.onGround() && !mob.isInWater() && !mob.isNoGravity()) {
			return Component.translatable("bloompets.post.land", record.name);
		}

		take(mob, mob.blockPosition());
		PetAdvancements.award(player, PetAdvancements.POST);
		record.post = mob.blockPosition();
		PetEvents.observe(mob);
		player.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), net.minecraft.sounds.SoundSource.NEUTRAL, 0.7F, 1.2F);
		return Component.translatable("bloompets.post.taken", record.name, Component.translatable("bloompets.post.job." + job(mob)))
				.withStyle(ChatFormatting.LIGHT_PURPLE);
	}

	/** The pet takes its post here. Public for the game tests. */
	public static void take(Mob pet, BlockPos at) {
		Pets.data(pet).putLong("post", at.asLong());
		Pets.setHome(pet, at);

		if (pet instanceof TamableAnimal tamable) {
			tamable.setOrderedToSit(false);
			tamable.setInSittingPose(false);
		}
	}

	/** The pet leaves its post (called, joining the party, or told to). */
	public static void leave(Entity pet) {
		CompoundTag data = Pets.dataOrNull(pet);

		if (data != null && data.contains("post")) {
			data.remove("post");
			Pets.setHome(pet, pet.blockPosition());
			WATCHES.remove(pet.getUUID());
		}
	}

	/** What the pet does at a post: its job's name. */
	public static String job(Entity pet) {
		Species species = Pets.species(pet);

		if (species == null) {
			return "wait";
		}

		return switch (species.id().getPath()) {
			case "bee" -> "tend";
			case "allay" -> "collect";
			case "sniffer" -> "dig";
			case "cat", "ocelot" -> "watch";
			case "llama" -> "spit";
			default -> species.combat() ? "guard" : "wait";
		};
	}

	/** Once a second for a loaded pet at its post: its job. Public for the game tests. */
	public static void second(LivingEntity pet) {
		BlockPos post = post(pet);

		if (post == null || !(pet.level() instanceof ServerLevel level) || Fainting.fainted(pet) || PetMood.sad(pet)) {
			WATCHES.remove(pet.getUUID());
			return;
		}

		long now = now(pet);
		double power = PetBonuses.power(PetLevels.level(pet));
		int extra = maxed(pet) ? 2 : 0;
		int second = (int) (now / 20);

		switch (job(pet)) {
			case "tend" -> {
				if (second % Math.max(1, (int) Math.round(3.0 / power)) == 0) {
					jobXp(pet, tend(level, post, 6 + extra, 4, pet.getRandom()));
				}
			}
			case "collect" -> {
				if (second % 2 == 0) {
					jobXp(pet, collect(pet, post, 8 + 2 * extra));
				}
			}
			case "dig" -> {
				CompoundTag data = Pets.data(pet);

				if (pet instanceof Sniffer sniffer && now >= data.getLong("digAt")) {
					data.putLong("digAt", now + (long) (4800 / power));
					ServerPlayer owner = pet.getServer().getPlayerList().getPlayer(Pets.owner(pet));

					if (PetBonuses.dig(sniffer, owner, owner != null && PetBonuses.awake(owner, now), now)) {
						jobXp(pet, 10);
					}
				}
			}
			case "spit" -> {
				if (second % 2 == 0) {
					LivingEntity monster = nearestMonster(level, post, 10 + extra, pet);

					if (monster != null) {
						PetBonuses.spit(pet, monster);
						jobXp(pet, 2);
					}
				}
			}
			case "watch" -> WATCHES.put(pet.getUUID(), new Watch(level.dimension(), post, CAT_RANGE * (maxed(pet) ? 1.5 : 1.0), now));
			default -> {
			}
		}
	}

	/** XP for work done, at most 30 a minute. */
	private static void jobXp(LivingEntity pet, int amount) {
		if (amount <= 0) {
			return;
		}

		CompoundTag data = Pets.data(pet);
		long minute = now(pet) / 1200;

		if (data.getLong("jobMinute") != minute) {
			data.putLong("jobMinute", minute);
			data.putInt("jobXp", 0);
		}

		int allowed = Math.min(amount, JOB_XP_PER_MINUTE - data.getInt("jobXp"));

		if (allowed > 0) {
			data.putInt("jobXp", data.getInt("jobXp") + allowed);
			PetLevels.addXp(pet, allowed);
		}
	}

	/** A monster worth fighting near the post (not a creeper, which would blow it up; an enderman or piglin only once angry). */
	static boolean worthFighting(LivingEntity mob) {
		if (!(mob instanceof Enemy) || mob instanceof Creeper || Pets.listed(mob)) {
			return false;
		}

		boolean neutral = mob instanceof EnderMan || mob instanceof ZombifiedPiglin;
		return !neutral || mob instanceof Mob angry && angry.getTarget() != null;
	}

	/** The nearest monster worth fighting within {@code range} of the post (for a guard, or a llama). Public for the game tests. */
	public static LivingEntity nearestMonster(ServerLevel level, BlockPos post, double range, LivingEntity pet) {
		LivingEntity best = null;
		double bestDistance = range * range;

		for (LivingEntity mob : level.getEntitiesOfClass(LivingEntity.class, new AABB(post).inflate(range, Math.min(range, 8.0), range))) {
			double distance = mob.distanceToSqr(post.getX() + 0.5, post.getY(), post.getZ() + 0.5);

			if (mob != pet && mob.isAlive() && worthFighting(mob) && !PetXp.sameSide(pet, mob) && distance < bestDistance) {
				best = mob;
				bestDistance = distance;
			}
		}

		return best;
	}

	/** The Bee's job: a few crops and saplings around the post grow a stage. Returns how many. Public for the game tests. */
	public static int tend(ServerLevel level, BlockPos post, int radius, int count, RandomSource random) {
		List<BlockPos> growing = new ArrayList<>();

		for (BlockPos pos : BlockPos.betweenClosed(post.offset(-radius, -2, -radius), post.offset(radius, 1, radius))) {
			BlockState state = level.getBlockState(pos);

			if (state.getBlock() instanceof SaplingBlock || state.getBlock() instanceof CropBlock crop && !crop.isMaxAge(state)
					|| !(state.getBlock() instanceof CropBlock) && state.is(BlockTags.BEE_GROWABLES) && young(state)) {
				growing.add(pos.immutable());
			}
		}

		Collections.shuffle(growing, new java.util.Random(random.nextLong()));
		int grown = 0;

		for (BlockPos pos : growing.subList(0, Math.min(count, growing.size()))) {
			BlockState state = level.getBlockState(pos);

			if (state.getBlock() instanceof SaplingBlock sapling) {
				sapling.advanceTree(level, pos, state, random);
			} else if (state.getBlock() instanceof CropBlock crop) {
				IntegerProperty age = age(state);

				if (age != null) {
					level.setBlock(pos, crop.getStateForAge(state.getValue(age) + 1), Block.UPDATE_CLIENTS);
				}
			} else {
				IntegerProperty age = age(state);

				if (age != null) {
					level.setBlock(pos, state.setValue(age, state.getValue(age) + 1), Block.UPDATE_CLIENTS);
				}
			}

			if (level.getBlockState(pos) != state) {
				grown++;
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 4, 0.25, 0.2, 0.25, 0.0);
			}
		}

		return grown;
	}

	private static IntegerProperty age(BlockState state) {
		for (Property<?> property : state.getProperties()) {
			if (property instanceof IntegerProperty age && "age".equals(property.getName())) {
				return age;
			}
		}

		return null;
	}

	/** Berries, wart, cocoa and the like that aren't full grown. */
	private static boolean young(BlockState state) {
		IntegerProperty age = age(state);
		return age != null && state.getValue(age) < Collections.max(age.getPossibleValues());
	}

	/**
	 * The Allay's job: the drops (not ones a player threw) and loose XP around its post go into its storage, the XP as
	 * bottles. Only for its owner: if another player is nearer the post, it leaves things be. Returns how many things
	 * it stored. Public for the game tests.
	 */
	public static int collect(LivingEntity allay, BlockPos post, int radius) {
		ServerLevel level = (ServerLevel) allay.level();
		Player nearest = level.getNearestPlayer(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, 48.0, false);

		if (nearest != null && !nearest.getUUID().equals(Pets.owner(allay))) {
			return 0;
		}

		PetRegistry registry = PetRegistry.get(level.getServer());
		PetRegistry.Record record = registry.find(allay.getUUID());

		if (record == null || record.slots <= 0) {
			return 0;
		}

		SimpleContainer box = new SimpleContainer(record.slots);
		box.fromTag(record.items);
		ItemStack bottle = new ItemStack(Items.EXPERIENCE_BOTTLE);
		CompoundTag data = Pets.data(allay);
		int bank = data.getInt("xpBank");
		int stored = 0;

		for (Entity entity : level.getEntities((Entity) null, new AABB(post).inflate(radius, 4.0, radius),
				e -> e instanceof ItemEntity || e instanceof ExperienceOrb)) {
			if (entity instanceof ItemEntity item) {
				if (item.hasPickUpDelay() || item.getPersistentData().getBoolean("bloompets.tossed")) {
					continue;
				}

				int before = item.getItem().getCount();
				ItemStack left = box.addItem(item.getItem().copy());

				if (left.isEmpty()) {
					item.discard();
					stored++;
				} else if (left.getCount() != before) {
					item.setItem(left);
					stored++;
				}
			} else if (entity instanceof ExperienceOrb orb && box.canAddItem(bottle)) {
				bank += orb.getValue();
				orb.discard();
			}
		}

		while (bank >= 7 && box.canAddItem(bottle)) {
			box.addItem(bottle.copy());
			bank -= 7;
			stored++;
		}

		data.putInt("xpBank", bank);

		if (stored > 0) {
			record.items = box.createTag();
			registry.setDirty();
			allay.playSound(SoundEvents.ALLAY_ITEM_TAKEN, 0.4F, 1.3F);
		}

		return stored;
	}

	/** The Cat's and Ocelot's job: creepers (unless someone hit them) and phantoms near its post go after no one. */
	@SubscribeEvent
	public static void onTarget(LivingChangeTargetEvent event) {
		if (WATCHES.isEmpty() || !(event.getNewTarget() instanceof Player) || !(event.getEntity() instanceof Mob mob)
				|| !(mob instanceof Creeper && mob.getLastHurtByMob() == null || mob instanceof Phantom)) {
			return;
		}

		long now = mob.getServer() == null ? 0 : mob.getServer().overworld().getGameTime();

		for (Watch watch : WATCHES.values()) {
			if (now - watch.seen() <= 100 && watch.dimension() == mob.level().dimension()
					&& mob.distanceToSqr(watch.post().getX() + 0.5, watch.post().getY(), watch.post().getZ() + 0.5) < watch.range() * watch.range()) {
				event.setCanceled(true);
				return;
			}
		}
	}
}
