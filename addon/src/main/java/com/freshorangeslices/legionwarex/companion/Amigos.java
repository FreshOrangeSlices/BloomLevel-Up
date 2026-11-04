package com.freshorangeslices.legionwarex.companion;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3f;

/**
 * Migamigos' companion state, read through its own public methods by name (the add-on doesn't compile against
 * Migamigos): whether it follows its owner or stays ("Is Following" / "Is Staying"), whether it may wander ("Can
 * Wander"), where it was told to stay, and whether it's an Enemigo. Each answer falls back to a harmless default when
 * the method isn't there (another mod, or a Migamigos that renamed it).
 */
public final class Amigos {
	private static final Map<String, Optional<Method>> METHODS = new ConcurrentHashMap<>();

	private Amigos() {
	}

	private static Object call(Entity entity, String name) {
		Optional<Method> method = METHODS.computeIfAbsent(entity.getClass().getName() + "#" + name, key -> find(entity.getClass(), name));

		if (method.isEmpty()) {
			return null;
		}

		try {
			return method.get().invoke(entity);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}

	private static Optional<Method> find(Class<?> type, String name) {
		try {
			Method method = type.getMethod(name);
			method.setAccessible(true);
			return Optional.of(method);
		} catch (NoSuchMethodException | RuntimeException e) {
			return Optional.empty();
		}
	}

	private static boolean flag(Entity entity, String name, boolean fallback) {
		return call(entity, name) instanceof Boolean value ? value : fallback;
	}

	/** "Is Following" (true) or "Is Staying" (false). */
	public static boolean following(Entity entity) {
		return flag(entity, "isFollowing", true);
	}

	/** "Can Wander" (true) or "Cannot Wander" (false). */
	public static boolean canWander(Entity entity) {
		return flag(entity, "canWander", true);
	}

	public static boolean attacking(Entity entity) {
		return flag(entity, "isAttacking", false);
	}

	/** Its owner has its backpack open. */
	public static boolean containerOpen(Entity entity) {
		return flag(entity, "isContainerOpen", false);
	}

	/** Migamigos' hostile mode, or null when it can't be told this way. */
	public static Boolean enemigo(Entity entity) {
		return call(entity, "isEnemigo") instanceof Boolean value ? value : null;
	}

	/** Where it was told to stay, or null. */
	public static Vector3f post(Entity entity) {
		return call(entity, "getPostPos") instanceof Vector3f post ? post : null;
	}
}
