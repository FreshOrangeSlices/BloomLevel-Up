package com.freshorangeslices.legionwarex.accessory;

import com.freshorangeslices.legionwarex.LegionWareX;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;

/**
 * Curios, reached by name (the add-on doesn't build against it): what an entity wears in a slot, and a listener on
 * Curios' attribute event with the slot and stack it's for. Everything here is a no-op without Curios.
 */
public final class CuriosReach {
	private static Method inventory;
	private static Method findBySlot;
	private static Method slotStack;
	private static Method slotContext;
	private static Method identifier;
	private static Method index;
	private static boolean tried;

	private CuriosReach() {
	}

	private static synchronized boolean ready() {
		if (!tried) {
			tried = true;

			if (ModList.get() != null && ModList.get().isLoaded("curios")) {
				try {
					inventory = Class.forName("top.theillusivec4.curios.api.CuriosApi").getMethod("getCuriosInventory", LivingEntity.class);
					findBySlot = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler").getMethod("findCurios", String[].class);
					Class<?> result = Class.forName("top.theillusivec4.curios.api.SlotResult");
					slotStack = result.getMethod("stack");
					Class<?> context = Class.forName("top.theillusivec4.curios.api.SlotContext");
					identifier = context.getMethod("identifier");
					index = context.getMethod("index");
				} catch (ReflectiveOperationException e) {
					LegionWareX.LOGGER.warn("LegionWareX accessories: Curios not readable ({})", e.toString());
					inventory = null;
				}
			}
		}

		return inventory != null;
	}

	/**
	 * The player's slot rows and their sizes, as Curios has them after loading its data (for the boot test), or empty
	 * if Curios can't say. Curios 5 has {@code getEntitySlots(EntityType)} and later also takes a client flag.
	 */
	public static java.util.Map<String, Integer> playerSlots() {
		java.util.Map<String, Integer> sizes = new java.util.TreeMap<>();

		if (!ready()) {
			return sizes;
		}

		try {
			Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
			Object slots = null;

			for (Method method : api.getMethods()) {
				if (!method.getName().equals("getEntitySlots") || method.getParameterCount() == 0
						|| method.getParameterTypes()[0] != net.minecraft.world.entity.EntityType.class) {
					continue;
				}

				if (method.getParameterCount() == 1) {
					slots = method.invoke(null, net.minecraft.world.entity.EntityType.PLAYER);
				} else if (method.getParameterCount() == 2 && method.getParameterTypes()[1] == boolean.class) {
					slots = method.invoke(null, net.minecraft.world.entity.EntityType.PLAYER, false);
				}

				if (slots != null) {
					break;
				}
			}

			if (slots instanceof java.util.Map<?, ?> map) {
				for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
					Object type = entry.getValue();
					sizes.put(String.valueOf(entry.getKey()), (Integer) type.getClass().getMethod("getSize").invoke(type));
				}
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			LegionWareX.LOGGER.warn("LegionWareX accessories: Curios' player slots not readable ({})", e.toString());
		}

		return sizes;
	}

	/** The stacks an entity wears in a slot (empty ones left out). */
	public static List<ItemStack> worn(LivingEntity entity, String slot) {
		if (!ready()) {
			return List.of();
		}

		try {
			Optional<?> handler = ((LazyOptional<?>) inventory.invoke(null, entity)).resolve();

			if (handler.isEmpty()) {
				return List.of();
			}

			List<?> found = (List<?>) findBySlot.invoke(handler.get(), (Object) new String[] {slot});
			return found.stream().map(result -> {
				try {
					return (ItemStack) slotStack.invoke(result);
				} catch (ReflectiveOperationException e) {
					return ItemStack.EMPTY;
				}
			}).filter(stack -> !stack.isEmpty()).toList();
		} catch (ReflectiveOperationException | RuntimeException e) {
			return List.of();
		}
	}

	/** One attribute event, as the add-on sees it. */
	public record AttributeEvent(Event event, ItemStack stack, String slot, int index, Method add) {
		public void add(net.minecraft.world.entity.ai.attributes.Attribute attribute, net.minecraft.world.entity.ai.attributes.AttributeModifier modifier) {
			try {
				add.invoke(event, attribute, modifier);
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException(e);
			}
		}
	}

	/** Listens to Curios' attribute event at a priority, with the slot's id and index worked out. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static void onAttributes(EventPriority priority, Consumer<AttributeEvent> handler) {
		if (!ready()) {
			return;
		}

		try {
			Class<?> type = Class.forName("top.theillusivec4.curios.api.event.CurioAttributeModifierEvent");
			Method getStack = type.getMethod("getItemStack");
			Method getContext = type.getMethod("getSlotContext");
			Method add = type.getMethod("addModifier", net.minecraft.world.entity.ai.attributes.Attribute.class,
					net.minecraft.world.entity.ai.attributes.AttributeModifier.class);
			MinecraftForge.EVENT_BUS.addListener(priority, false, (Class) type, (Event event) -> {
				try {
					Object context = getContext.invoke(event);
					handler.accept(new AttributeEvent(event, (ItemStack) getStack.invoke(event), (String) identifier.invoke(context),
							(Integer) index.invoke(context), add));
				} catch (ReflectiveOperationException | RuntimeException e) {
					LegionWareX.LOGGER.debug("LegionWareX accessories: an attribute event failed: {}", e.toString());
				}
			});
		} catch (ReflectiveOperationException e) {
			LegionWareX.LOGGER.warn("LegionWareX accessories: Curios' attribute event not found ({})", e.toString());
		}
	}
}
