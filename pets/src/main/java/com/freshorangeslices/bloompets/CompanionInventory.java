package com.freshorangeslices.bloompets;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/**
 * A companion's own inventory, as its mod keeps it (Bloom Pets doesn't compile against those mods): a public
 * {@code getInventory()} that gives a {@link Container} (a Migamigos companion's backpack). A hungry companion that's
 * out with its owner takes a meal from it (FreshOrangeSlices, 2026-11-05). Zoni's companions have nothing but what
 * they hold and wear (FreshOrangeSlices' playtest), so they're fed by hand.
 */
public final class CompanionInventory {
	private static final Map<Class<?>, Optional<Method>> GETTERS = new ConcurrentHashMap<>();

	private CompanionInventory() {
	}

	/** Takes one of the first stack that's {@code wanted} from its backpack. */
	public static boolean takeOne(Entity entity, Predicate<ItemStack> wanted) {
		Container box = container(entity);

		if (box != null) {
			for (int i = 0; i < box.getContainerSize(); i++) {
				if (wanted.test(box.getItem(i))) {
					box.removeItem(i, 1);
					box.setChanged();
					return true;
				}
			}
		}

		return false;
	}

	/** Its backpack (a public {@code getInventory()} that gives a container), or null. Public for the game tests. */
	public static Container container(Entity entity) {
		Optional<Method> getter = GETTERS.computeIfAbsent(entity.getClass(), type -> {
			try {
				Method method = type.getMethod("getInventory");
				return method.getParameterCount() == 0 && Container.class.isAssignableFrom(method.getReturnType()) ? Optional.of(method) : Optional.empty();
			} catch (NoSuchMethodException | RuntimeException e) {
				return Optional.empty();
			}
		});

		try {
			return getter.isPresent() && getter.get().invoke(entity) instanceof Container container ? container : null;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}
}
