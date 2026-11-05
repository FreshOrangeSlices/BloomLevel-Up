package com.freshorangeslices.bloompets;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.Entity;

/**
 * Migamigos' own Amigo levels (1 to 99: XP from the mobs an Amigo kills, health and power as it levels), read through
 * its public methods by name (Bloom Pets doesn't compile against Migamigos). A Migamigos companion's level in Bloom Pets
 * is its Amigo level (FreshOrangeSlices, 2026-11-05: one level per companion), and Bloom Pets never changes it.
 */
public final class Amigos {
	private static final Map<String, Optional<Method>> METHODS = new ConcurrentHashMap<>();

	private Amigos() {
	}

	private static Optional<Method> find(Class<?> type, String name, Class<?>... parameters) {
		return METHODS.computeIfAbsent(type.getName() + "#" + name, k -> {
			try {
				return Optional.of(type.getMethod(name, parameters));
			} catch (NoSuchMethodException | RuntimeException e) {
				return Optional.empty();
			}
		});
	}

	private static Integer call(Entity entity, String name) {
		Optional<Method> method = find(entity.getClass(), name);

		try {
			return method.isPresent() && method.get().invoke(entity) instanceof Integer value ? value : null;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}

	/** Whether this companion levels its own way (a Migamigos Amigo). */
	public static boolean has(Entity entity) {
		return Pets.kind(entity) == Pets.Kind.MODDED && find(entity.getClass(), "getAmigoLevel").isPresent();
	}

	/** Its Amigo level, or null. */
	public static Integer level(Entity entity) {
		return Pets.kind(entity) == Pets.Kind.MODDED ? call(entity, "getAmigoLevel") : null;
	}

	/** Its XP toward the next Amigo level, or null. */
	public static Integer xp(Entity entity) {
		return call(entity, "getExperience");
	}

	/** The XP its next Amigo level takes, or null. */
	public static Integer xpNext(Entity entity, int level) {
		Optional<Method> method = find(entity.getClass(), "getExperienceForNextLevel", int.class);

		try {
			return method.isPresent() && method.get().invoke(null, level) instanceof Integer value ? value : null;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}
}
