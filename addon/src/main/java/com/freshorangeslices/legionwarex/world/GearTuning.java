package com.freshorangeslices.legionwarex.world;

import com.freshorangeslices.legionwarex.AddonConfig;
import com.freshorangeslices.legionwarex.LegionWareX;
import com.google.common.collect.Multimap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Weapons whose attack damage the pack sets (FreshOrangeSlices, 2026-11-23: the top weapons' numbers by how hard each is
 * to get; config {@code weaponDamage}, "item=damage", the damage the tooltip shows). The weapon's own attack damage
 * modifiers (in the main hand) are scaled so a plain one shows that damage; its attack speed, its other modifiers and
 * their ids stay as they are, so its swing (Better Combat's too) and Bloom's reading of it don't change, and a weapon whose
 * damage grows with its own state (Simply Swords' uniques waking up) still grows, to the set damage when full. The
 * server's config reaches players' games as they join, so tooltips show the same numbers.
 */
public final class GearTuning {
	/** For the game tests: this table in place of the config's (null for the config's). */
	public static Map<ResourceLocation, Double> testTable;
	/** Each weapon's own attack damage modifiers, added up, on a plain one (cached; the hand's 1 not included). */
	private static final Map<Item, Double> PLAIN = new ConcurrentHashMap<>();
	private static List<? extends String> parsedFrom;
	private static Map<ResourceLocation, Double> parsed = Map.of();

	private GearTuning() {
	}

	/** The set damage per item (the tooltip's, a hand's 1 included). */
	public static Map<ResourceLocation, Double> table() {
		if (testTable != null) {
			return testTable;
		}

		List<? extends String> list = AddonConfig.weaponDamage();

		if (list != parsedFrom) {
			Map<ResourceLocation, Double> out = new HashMap<>();

			for (String entry : list) {
				String[] parts = entry.split("=", 2);
				ResourceLocation id = ResourceLocation.tryParse(parts[0].trim());

				try {
					double damage = Double.parseDouble(parts[1].trim());

					if (id != null && damage > 1.0) {
						out.put(id, damage);
					}
				} catch (RuntimeException e) {
					LegionWareX.LOGGER.warn("LegionWareX gear: weaponDamage entry \"{}\" isn't \"item=damage\"", entry);
				}
			}

			parsed = Map.copyOf(out);
			parsedFrom = list;
		}

		return parsed;
	}

	private static double ownDamage(Multimap<Attribute, AttributeModifier> modifiers) {
		double sum = 0.0;

		for (AttributeModifier modifier : modifiers.get(Attributes.ATTACK_DAMAGE)) {
			if (modifier.getOperation() == AttributeModifier.Operation.ADDITION) {
				sum += modifier.getAmount();
			}
		}

		return sum;
	}

	/** The weapon's own damage on a plain one (its item's modifiers, the event not asked). */
	private static double plain(Item item) {
		return PLAIN.computeIfAbsent(item, it -> ownDamage(it.getAttributeModifiers(EquipmentSlot.MAINHAND, new ItemStack(it))));
	}

	@SubscribeEvent(priority = EventPriority.HIGH)
	public static void onModifiers(ItemAttributeModifierEvent event) {
		if (event.getSlotType() != EquipmentSlot.MAINHAND) {
			return;
		}

		ItemStack stack = event.getItemStack();
		ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
		Double target = id == null ? null : table().get(id);

		if (target == null) {
			return;
		}

		double base = plain(stack.getItem());

		if (base <= 0.0) {
			return;
		}

		double factor = (target - 1.0) / base;
		List<AttributeModifier> own = new ArrayList<>();

		for (AttributeModifier modifier : event.getOriginalModifiers().get(Attributes.ATTACK_DAMAGE)) {
			if (modifier.getOperation() == AttributeModifier.Operation.ADDITION) {
				own.add(modifier);
			}
		}

		for (AttributeModifier modifier : own) {
			event.removeModifier(Attributes.ATTACK_DAMAGE, modifier);
			event.addModifier(Attributes.ATTACK_DAMAGE,
					new AttributeModifier(modifier.getId(), modifier.getName(), modifier.getAmount() * factor, modifier.getOperation()));
		}
	}

	/** What the server set, as "LegionWareX gear:" (each weapon's damage before and after, and any the pack doesn't have). */
	@SubscribeEvent
	public static void onServerStarted(ServerStartedEvent event) {
		List<String> rows = new ArrayList<>();
		List<String> missing = new ArrayList<>();

		for (Map.Entry<ResourceLocation, Double> entry : table().entrySet()) {
			Item item = ForgeRegistries.ITEMS.containsKey(entry.getKey()) ? ForgeRegistries.ITEMS.getValue(entry.getKey()) : null;

			if (item == null) {
				missing.add(entry.getKey().toString());
				continue;
			}

			double now = ownDamage(new ItemStack(item).getAttributeModifiers(EquipmentSlot.MAINHAND)) + 1.0;
			rows.add(entry.getKey() + " " + round(plain(item) + 1.0) + " -> " + round(now));
		}

		LegionWareX.LOGGER.info("LegionWareX gear: {} weapon(s) set: {}{}", rows.size(), String.join(", ", rows),
				missing.isEmpty() ? "" : "; not in the game: " + String.join(", ", missing));
	}

	private static String round(double value) {
		return String.valueOf(Math.round(value * 100.0) / 100.0);
	}
}
