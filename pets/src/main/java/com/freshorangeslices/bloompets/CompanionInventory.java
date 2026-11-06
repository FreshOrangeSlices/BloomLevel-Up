package com.freshorangeslices.bloompets;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

/**
 * A companion's own inventory, as its mod keeps it (Bloom Pets doesn't compile against those mods): a public
 * {@code getInventory()} that gives a {@link Container} (a Migamigos companion's backpack), and Forge's item capability
 * (a Zoni's companion's slots; every living thing has one too, for what it holds and wears). A hungry companion that's
 * out with its owner takes a meal from it (FreshOrangeSlices, 2026-11-05).
 */
public final class CompanionInventory {
	private static final Map<Class<?>, Optional<Method>> GETTERS = new ConcurrentHashMap<>();

	private CompanionInventory() {
	}

	/** Takes one of the first stack that's {@code wanted}, from the backpack first, then the item capability. */
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

		Optional<IItemHandler> handler = entity.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve();

		if (handler.isPresent()) {
			IItemHandler items = handler.get();

			for (int i = 0; i < items.getSlots(); i++) {
				if (wanted.test(items.getStackInSlot(i)) && !items.extractItem(i, 1, false).isEmpty()) {
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
