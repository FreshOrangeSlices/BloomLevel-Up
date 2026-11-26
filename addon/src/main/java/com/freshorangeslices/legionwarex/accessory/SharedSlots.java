package com.freshorangeslices.legionwarex.accessory;

import com.freshorangeslices.legionwarex.LegionWareX;
import com.google.common.collect.Multimap;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.EventPriority;

/**
 * Shared accessory rows (FreshOrangeSlices, 2026-11-25): the pack folds Cataclysm's own Rings and Waist rows into the
 * shared Ring and Belt slots (3 and 2, pack_data's curios files), so any ring or belt fits any slot of its row. Cataclysm
 * gives its curios their stats only in their own row ({@code CuriosItem.attributeSlot}); here they get them in the
 * shared one too, read by name from the item. Its belts' extra Talisman slots never cared which slot.
 */
public final class SharedSlots {
	/** A shared row and the mod's own row its items are made for. */
	public static final Map<String, String> ROWS = Map.of("ring", "rings", "belt", "waist");
	private static Field slotField;
	private static Field attributesField;
	private static boolean logged;

	private SharedSlots() {
	}

	public static void register() {
		CuriosReach.onAttributes(EventPriority.LOW, SharedSlots::onAttributes);
	}

	/** The row an item gives its stats in, and how (Cataclysm's CuriosItem), or null for any other item. */
	@SuppressWarnings("unchecked")
	static Function<Integer, Multimap<Attribute, AttributeModifier>> stats(Item item, String row) {
		for (Class<?> type = item.getClass(); type != null && type != Item.class; type = type.getSuperclass()) {
			if (!type.getName().equals("com.github.L_Ender.cataclysm.items.CuriosItem.CuriosItem")) {
				continue;
			}

			try {
				if (slotField == null) {
					slotField = type.getDeclaredField("attributeSlot");
					slotField.setAccessible(true);
					attributesField = type.getDeclaredField("attributes");
					attributesField.setAccessible(true);
				}

				return row.equals(slotField.get(item)) ? (Function<Integer, Multimap<Attribute, AttributeModifier>>) attributesField.get(item) : null;
			} catch (ReflectiveOperationException | RuntimeException e) {
				if (!logged) {
					logged = true;
					LegionWareX.LOGGER.warn("LegionWareX accessories: Cataclysm's curios not readable ({}): its ring and belts get no stats in the shared rows", e.toString());
				}

				return null;
			}
		}

		return null;
	}

	static void onAttributes(CuriosReach.AttributeEvent event) {
		String own = ROWS.get(event.slot());

		if (own == null || event.stack().isEmpty()) {
			return;
		}

		Function<Integer, Multimap<Attribute, AttributeModifier>> stats = stats(event.stack().getItem(), own);

		if (stats != null) {
			stats.apply(event.index()).forEach(event::add);
		}
	}
}
