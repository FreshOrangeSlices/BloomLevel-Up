package com.freshorangeslices.legionwarex.companion;

import com.freshorangeslices.legionwarex.LegionWareX;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

/**
 * Companions, as the add-on finds them: the entity types in its tag {@code legionwarex:companions} (Migamigos' five;
 * Migamigos' own tag misses three) whose class keeps an owner the way Migamigos' amigos do. Reached without compiling
 * against Migamigos: its owner field is read by name, so the add-on runs (and does nothing) without it.
 */
public final class Companions {
	public static final TagKey<EntityType<?>> COMPANIONS = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(LegionWareX.MOD_ID, "companions"));

	/** Migamigos' AmigoEntity: the player it was befriended by, kept whether or not they're online. */
	private static final String OWNER_FIELD = "followingPlayerUUID";
	private static final Map<Class<?>, Optional<Field>> OWNER_FIELDS = new ConcurrentHashMap<>();

	private Companions() {
	}

	/** A companion: in the tag, and with an owner field (so a wild one can be told from a befriended one). */
	public static boolean isCompanion(Entity entity) {
		return entity.getType().is(COMPANIONS) && ownerField(entity.getClass()).isPresent();
	}

	/** The player who befriended it, or null for a wild one (or something that isn't a companion). */
	public static UUID owner(Entity entity) {
		Optional<Field> field = ownerField(entity.getClass());

		if (field.isEmpty()) {
			return null;
		}

		try {
			return (UUID) field.get().get(entity);
		} catch (IllegalAccessException | ClassCastException e) {
			return null;
		}
	}

	/**
	 * Wild: a companion no one befriended, without a name tag, not leashed or riding. Only these despawn; one that has
	 * an owner never does, however far away the owner is (Migamigos only marks the owner nearby as "has player").
	 */
	public static boolean isWild(Mob mob) {
		return isCompanion(mob) && owner(mob) == null && !mob.hasCustomName() && !mob.isLeashed() && !mob.isPassenger() && !mob.isVehicle();
	}

	private static Optional<Field> ownerField(Class<?> type) {
		return OWNER_FIELDS.computeIfAbsent(type, Companions::findOwnerField);
	}

	private static Optional<Field> findOwnerField(Class<?> type) {
		for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
			try {
				Field field = c.getDeclaredField(OWNER_FIELD);

				if (field.getType() == UUID.class) {
					field.setAccessible(true);
					return Optional.of(field);
				}
			} catch (NoSuchFieldException | RuntimeException e) {
				// Not on this class; look further up.
			}
		}

		return Optional.empty();
	}
}
