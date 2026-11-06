package com.freshorangeslices.bloompets;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.Entity;

/**
 * Migamigos' own Amigo levels (1 to 99; XP only from the mobs an Amigo kills, the XP they'd drop; 10 x 1.1^level a
 * level; +0.1 damage a level and +1 health every second one), read and fed through its public methods by name (Bloom
 * Pets doesn't compile against Migamigos). A Migamigos companion's level in Bloom Pets is its Amigo level
 * (FreshOrangeSlices, 2026-11-05: one level per companion). Bloom Pets adds to its XP at a Bloom pet's pace
 * ({@link PetLevels#addXp}); Migamigos turns that XP into levels itself, on the Amigo's next kill, with its own rewards.
 */
public final class Amigos {
	/** Migamigos' top level. */
	public static final int MAX = 99;
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

	/** The damage its Amigo levels add to each of its attacks (+0.1 a level), or null. */
	public static Float extraPower(Entity entity) {
		if (level(entity) == null) {
			return null;
		}

		Optional<Method> method = find(entity.getClass(), "getExtraPower");

		try {
			return method.isPresent() && method.get().invoke(entity) instanceof Float value ? value : null;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}

	/** Its XP toward the next Amigo level, or null. */
	public static Integer xp(Entity entity) {
		return call(entity, "getExperience");
	}

	/**
	 * The Amigo's level once the XP it holds is counted: Migamigos only levels an Amigo when it kills something, so XP
	 * Bloom Pets gave it can wait in it until then. Null if it isn't an Amigo.
	 */
	public static Integer owedLevel(Entity entity) {
		Integer level = level(entity);
		Integer xp = xp(entity);

		if (level == null || xp == null) {
			return null;
		}

		int at = level;
		long left = xp;

		while (at < MAX) {
			Integer need = xpNext(entity, at);

			if (need == null || left < need) {
				break;
			}

			left -= need;
			at++;
		}

		return at;
	}

	/**
	 * Gives the Amigo one more Amigo level's worth of XP (the next one after what it already holds), unless that would
	 * take it past {@code cap}. Returns whether it gave any.
	 */
	public static boolean giveLevel(Entity entity, int cap) {
		Integer owed = owedLevel(entity);
		Integer xp = xp(entity);

		if (owed == null || xp == null || owed >= cap || owed >= MAX) {
			return false;
		}

		Integer need = xpNext(entity, owed);
		Optional<Method> set = find(entity.getClass(), "setExperience", int.class);

		if (need == null || set.isEmpty()) {
			return false;
		}

		try {
			set.get().invoke(entity, (int) Math.min(Integer.MAX_VALUE, (long) xp + need));
			return true;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
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
