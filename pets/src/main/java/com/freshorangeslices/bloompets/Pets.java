package com.freshorangeslices.bloompets;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Ocelot;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Who a pet belongs to and what kind it is. A Bloom pet's owner is kept in its own data ({@code bloompets.owner});
 * a wolf or horse tamed the vanilla way counts too, by its vanilla owner. Companions from other mods (the config's
 * {@code moddedNamespaces}) count by their own owner: a tamed animal's, or Migamigos' {@code followingPlayerUUID}, read
 * by name (no compile-time dependency).
 */
public final class Pets {
	/** The pet's own data, under its persistent data: owner, party, home, bond (feedings so far, per player). */
	public static final String DATA = "bloompets";
	private static final String MIGAMIGOS_OWNER = "followingPlayerUUID";
	private static final Map<Class<?>, Optional<Field>> OWNER_FIELDS = new ConcurrentHashMap<>();

	private Pets() {
	}

	public enum Kind {
		/** One of the roster's animals. */
		BLOOM,
		/** Another mod's companion: always with its owner, no party place needed. */
		MODDED
	}

	/** The pet's own data, created (and kept) on first use. */
	public static CompoundTag data(Entity entity) {
		CompoundTag root = entity.getPersistentData();

		if (!root.contains(DATA, Tag.TAG_COMPOUND)) {
			root.put(DATA, new CompoundTag());
		}

		return root.getCompound(DATA);
	}

	private static CompoundTag dataOrNull(Entity entity) {
		CompoundTag root = entity.getPersistentData();
		return root.contains(DATA, Tag.TAG_COMPOUND) ? root.getCompound(DATA) : null;
	}

	public static Species species(Entity entity) {
		return SpeciesData.of(entity.getType());
	}

	public static boolean moddedKind(Entity entity) {
		var key = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
		return key != null && entity instanceof LivingEntity && PetsConfig.moddedNamespaces().contains(key.getNamespace());
	}

	public static Kind kind(Entity entity) {
		if (species(entity) != null) {
			return Kind.BLOOM;
		}

		return moddedKind(entity) ? Kind.MODDED : null;
	}

	/** The player the pet belongs to, or null. */
	public static UUID owner(Entity entity) {
		CompoundTag data = dataOrNull(entity);

		if (data != null && data.hasUUID("owner")) {
			return data.getUUID("owner");
		}

		if (entity instanceof OwnableEntity owned && owned.getOwnerUUID() != null) {
			return owned.getOwnerUUID();
		}

		if (entity instanceof AbstractHorse horse && horse.getOwnerUUID() != null) {
			return horse.getOwnerUUID();
		}

		return migamigosOwner(entity);
	}

	private static UUID migamigosOwner(Entity entity) {
		Optional<Field> field = OWNER_FIELDS.computeIfAbsent(entity.getClass(), type -> {
			for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
				try {
					Field f = c.getDeclaredField(MIGAMIGOS_OWNER);
					f.setAccessible(true);
					return Optional.of(f);
				} catch (NoSuchFieldException | RuntimeException e) {
					// keep looking up the hierarchy
				}
			}

			return Optional.empty();
		});

		try {
			return field.isPresent() && field.get().get(entity) instanceof UUID uuid ? uuid : null;
		} catch (IllegalAccessException e) {
			return null;
		}
	}

	/** A pet of the player's: one of the roster's animals or a listed mod's companion, with an owner. */
	public static boolean listed(Entity entity) {
		return entity instanceof LivingEntity && kind(entity) != null && owner(entity) != null;
	}

	public static boolean inParty(Entity entity) {
		CompoundTag data = dataOrNull(entity);
		return data != null && data.getBoolean("party");
	}

	public static void setParty(Entity entity, boolean party) {
		data(entity).putBoolean("party", party);

		if (!party) {
			setHome(entity, entity.blockPosition());
		}
	}

	public static BlockPos home(Entity entity) {
		CompoundTag data = dataOrNull(entity);
		return data != null && data.contains("home", Tag.TAG_LONG) ? BlockPos.of(data.getLong("home")) : null;
	}

	public static void setHome(Entity entity, BlockPos pos) {
		data(entity).putLong("home", pos.asLong());
	}

	/** Feedings toward bonding so far, by this player. */
	public static int bondProgress(Entity entity, UUID player) {
		CompoundTag data = dataOrNull(entity);
		return data == null ? 0 : data.getCompound("bond").getInt(player.toString());
	}

	public static void setBondProgress(Entity entity, UUID player, int feedings) {
		CompoundTag data = data(entity);
		CompoundTag bond = data.getCompound("bond");
		bond.putInt(player.toString(), feedings);
		data.put("bond", bond);
	}

	/**
	 * Makes the mob the player's Bloom pet: their owner mark, kept for good (never despawns), tamed the vanilla way where
	 * the animal has one (wolves and cats, horses and llamas), trusting them (foxes, ocelots), liking them (allays),
	 * built by them (iron golems), no longer tied to a hive (bees), and named if it had no name.
	 */
	public static void claim(Mob mob, Player player) {
		// Vanilla state that only lives in saved data first: loading it back replaces the entity's persistent data.
		if (mob instanceof Fox || mob instanceof Ocelot || mob instanceof Bee) {
			CompoundTag tag = mob.saveWithoutId(new CompoundTag());

			if (mob instanceof Fox) {
				ListTag trusted = tag.getList("Trusted", Tag.TAG_INT_ARRAY);
				trusted.add(NbtUtils.createUUID(player.getUUID()));
				tag.put("Trusted", trusted);
			} else if (mob instanceof Ocelot) {
				tag.putBoolean("Trusting", true);
			} else {
				tag.remove("HivePos");
				tag.putInt("AngerTime", 0);
			}

			mob.load(tag);
		}

		if (mob instanceof TamableAnimal tamable && !tamable.isTame()) {
			tamable.setTame(true);
			tamable.setOwnerUUID(player.getUUID());
		} else if (mob instanceof AbstractHorse horse && !horse.isTamed()) {
			horse.setTamed(true);
			horse.setOwnerUUID(player.getUUID());
		}

		if (mob instanceof IronGolem golem) {
			golem.setPlayerCreated(true);
		}

		if (mob instanceof Allay allay) {
			allay.getBrain().setMemory(MemoryModuleType.LIKED_PLAYER, player.getUUID());
		}

		mob.setPersistenceRequired();
		mob.setTarget(null);

		if (!mob.hasCustomName()) {
			mob.setCustomName(Component.literal(Names.pick(mob.getRandom())));
		}

		CompoundTag data = data(mob);
		data.putUUID("owner", player.getUUID());
		data.remove("bond");
		setHome(mob, mob.blockPosition());
	}

	/** The pet, wherever it's loaded right now, or null. */
	public static Entity loaded(MinecraftServer server, UUID pet) {
		for (ServerLevel level : server.getAllLevels()) {
			Entity entity = level.getEntity(pet);

			if (entity != null && entity.isAlive()) {
				return entity;
			}
		}

		return null;
	}

	/** Cute names for a newly bonded pet that has none (as the plugin gave them). */
	public static final class Names {
		private static final String[] NAMES = {"Biscuit", "Clover", "Maple", "Pip", "Juniper", "Nutmeg", "Pebble", "Sprout",
				"Willow", "Bramble", "Toffee", "Pickle", "Thistle", "Hazel", "Fern", "Mochi", "Acorn", "Basil", "Cinder", "Dumpling",
				"Ember", "Fig", "Ginger", "Honey", "Ivy", "Jasper", "Kiwi", "Lark", "Moss", "Olive", "Pumpkin", "Quill", "Rosie",
				"Saffron", "Tansy", "Umber", "Violet", "Wren", "Yarrow", "Ziggy"};

		private Names() {
		}

		public static String pick(RandomSource random) {
			return NAMES[random.nextInt(NAMES.length)];
		}
	}
}
