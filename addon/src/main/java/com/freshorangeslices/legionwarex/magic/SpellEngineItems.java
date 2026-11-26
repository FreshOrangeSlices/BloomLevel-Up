package com.freshorangeslices.legionwarex.magic;

import com.freshorangeslices.legionwarex.LegionWareX;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

/**
 * Spell Engine's items (Wizards', Paladins' and Rogues' armor and weapons) get their attributes from Spell Engine's own
 * mixin on {@code ItemStack#getAttributeModifiers}, which sets the method's result after Forge's
 * {@code ItemAttributeModifierEvent} has run, throwing away everything any mod added on that event: the add-on's magic
 * copies, found-far bonuses, Iron's upgrade orbs (read in its jar, probe of 2026-11-26; the pack test's magic check showed
 * Wizards' robes with no copy). Its injection cancels at the method's return, so nothing injected at the return after it
 * runs; the add-on's mixin ({@code mixin/ItemStackAttributesMixin}) answers at the method's head for those items instead:
 * Spell Engine's own attributes for the slot, put through Forge's event, so they take part like any other.
 */
public final class SpellEngineItems {
	private static MethodHandle itemModifiers;
	private static boolean tried;

	private SpellEngineItems() {
	}

	private static synchronized MethodHandle handle() {
		if (!tried) {
			tried = true;

			if (ModList.get() != null && ModList.get().isLoaded("spell_engine")) {
				try {
					Class<?> util = Class.forName("net.spell_engine.utils.AttributeModifierUtil");
					Class<?> result = Class.forName("net.spell_engine.api.item.ItemAttributeModifiers", false, util.getClassLoader());
					itemModifiers = MethodHandles.publicLookup().findStatic(util, "itemModifiers", MethodType.methodType(result, Item.class));
				} catch (ReflectiveOperationException | RuntimeException e) {
					try {
						Class<?> util = Class.forName("net.spell_engine.utils.AttributeModifierUtil");

						for (java.lang.reflect.Method method : util.getMethods()) {
							if (method.getName().equals("itemModifiers") && method.getParameterCount() == 1 && method.getParameterTypes()[0] == Item.class) {
								itemModifiers = MethodHandles.publicLookup().unreflect(method);
							}
						}
					} catch (ReflectiveOperationException | RuntimeException e2) {
						LegionWareX.LOGGER.warn("LegionWareX magic: Spell Engine's item attributes not readable ({}): its items keep only its own", e2.toString());
					}
				}
			}
		}

		return itemModifiers;
	}

	/**
	 * Spell Engine's attributes for this stack in this slot, as its mixin would set them, or null where it wouldn't (not
	 * one of its items, or NBT attributes of the stack's own).
	 */
	@SuppressWarnings("unchecked")
	public static com.google.common.collect.Multimap<net.minecraft.world.entity.ai.attributes.Attribute, net.minecraft.world.entity.ai.attributes.AttributeModifier> forSlot(
			ItemStack stack, net.minecraft.world.entity.EquipmentSlot slot) {
		MethodHandle handle = handle();

		if (handle == null || stack.isEmpty() || stack.hasTag() && stack.getTag().contains("AttributeModifiers", Tag.TAG_LIST)) {
			return null;
		}

		try {
			Object modifiers = handle.invoke(stack.getItem());

			if (modifiers == null) {
				return null;
			}

			java.lang.reflect.Method method = FOR_SLOT.computeIfAbsent(modifiers.getClass(), type -> {
				for (java.lang.reflect.Method candidate : type.getMethods()) {
					if (candidate.getName().equals("forSlot") && candidate.getParameterCount() == 1
							&& candidate.getParameterTypes()[0] == net.minecraft.world.entity.EquipmentSlot.class) {
						return java.util.Optional.of(candidate);
					}
				}

				LegionWareX.LOGGER.warn("LegionWareX magic: Spell Engine's {} has no forSlot(EquipmentSlot)", type.getName());
				return java.util.Optional.empty();
			}).orElse(null);

			return method == null ? null : (com.google.common.collect.Multimap<net.minecraft.world.entity.ai.attributes.Attribute, net.minecraft.world.entity.ai.attributes.AttributeModifier>) method.invoke(modifiers, slot);
		} catch (Throwable e) {
			return null;
		}
	}

	private static final java.util.Map<Class<?>, java.util.Optional<java.lang.reflect.Method>> FOR_SLOT = new java.util.concurrent.ConcurrentHashMap<>();

	/** Whether Spell Engine set this stack's attributes itself (an item it configures, without NBT attributes of its own). */
	public static boolean replaced(ItemStack stack) {
		MethodHandle handle = handle();

		if (handle == null || stack.isEmpty()) {
			return false;
		}

		if (stack.hasTag() && stack.getTag().contains("AttributeModifiers", Tag.TAG_LIST)) {
			return false;
		}

		try {
			return handle.invoke(stack.getItem()) != null;
		} catch (Throwable e) {
			return false;
		}
	}
}
