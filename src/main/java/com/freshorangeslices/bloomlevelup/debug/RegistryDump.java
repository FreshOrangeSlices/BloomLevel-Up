package com.freshorangeslices.bloomlevelup.debug;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;
import com.freshorangeslices.bloomlevelup.compat.BetterCombat;

/**
 * A tool for supporting modpacks, off unless the server starts with {@code -Dbloomlevelup.dump=<file>}: two seconds after
 * the server is up it writes every item (its classes, tags, damage, armor, Better Combat category) and every entity type
 * (health, armor, whether it keeps a boss bar) to that file, one JSON object per line. CI runs it on LegionWareX to
 * sort the pack's weapons, armor and bosses into Bloom's tags. Does nothing to the world: the entities it measures are
 * made and thrown away, never added.
 */
public final class RegistryDump {
	private static final Gson GSON = new Gson();

	private RegistryDump() {
	}

	public static void init() {
		String target = System.getProperty("bloomlevelup.dump");

		if (target != null && !target.isBlank()) {
			// Two seconds after the start, so other mods' own server-start loading (Better Combat's weapon data) is done.
			int[] ticks = {0};
			MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) -> {
				if (event.phase == TickEvent.Phase.END && ++ticks[0] == 40) {
					write(event.getServer().overworld(), Path.of(target));
				}
			});
		}
	}

	private static void write(ServerLevel level, Path target) {
		List<String> lines = new ArrayList<>();
		int items = 0;
		int entities = 0;

		for (Item item : BuiltInRegistries.ITEM) {
			try {
				lines.add(GSON.toJson(item(item)));
				items++;
			} catch (Throwable e) {
				lines.add(GSON.toJson(error("item", BuiltInRegistries.ITEM.getKey(item).toString(), e)));
			}
		}

		for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
			try {
				lines.add(GSON.toJson(entity(level, type)));
				entities++;
			} catch (Throwable e) {
				lines.add(GSON.toJson(error("entity", BuiltInRegistries.ENTITY_TYPE.getKey(type).toString(), e)));
			}
		}

		try {
			Files.write(target, lines, StandardCharsets.UTF_8);
			BloomLevelUp.LOGGER.info("Bloom registry dump: {} items, {} entity types -> {}", items, entities, target.toAbsolutePath());
		} catch (IOException e) {
			BloomLevelUp.LOGGER.warn("Bloom registry dump couldn't be written to {}", target, e);
		}
	}

	private static JsonObject item(Item item) {
		JsonObject out = new JsonObject();
		ItemStack stack = new ItemStack(item);
		out.addProperty("kind", "item");
		out.addProperty("id", BuiltInRegistries.ITEM.getKey(item).toString());
		out.addProperty("name", stack.getHoverName().getString());
		out.add("classes", classes(item.getClass(), Item.class));
		out.add("tags", strings(item.builtInRegistryHolder().tags().map(tag -> tag.location().toString()).sorted().toList()));
		out.addProperty("damage", sum(stack, EquipmentSlot.MAINHAND, Attributes.ATTACK_DAMAGE));
		out.addProperty("speed", sum(stack, EquipmentSlot.MAINHAND, Attributes.ATTACK_SPEED));
		out.addProperty("durability", stack.getMaxDamage());
		out.addProperty("stack", stack.getMaxStackSize());
		out.addProperty("use", stack.getUseAnimation().name().toLowerCase(java.util.Locale.ROOT));

		if (item instanceof ArmorItem armor) {
			JsonObject a = new JsonObject();
			a.addProperty("slot", armor.getType().getName());
			a.addProperty("defense", armor.getDefense());
			a.addProperty("toughness", armor.getToughness());
			a.addProperty("knockback", armor.getMaterial().getKnockbackResistance());
			a.addProperty("material", armor.getMaterial().getName());
			out.add("armor", a);
		}

		String bc = BetterCombat.describe(stack);

		if (bc != null) {
			out.addProperty("bettercombat", bc);
		}

		return out;
	}

	private static JsonObject entity(ServerLevel level, EntityType<?> type) {
		JsonObject out = new JsonObject();
		out.addProperty("kind", "entity");
		out.addProperty("id", BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
		out.addProperty("category", type.getCategory().getName());
		out.add("tags", strings(type.builtInRegistryHolder().tags().map(tag -> tag.location().toString()).sorted().toList()));
		Entity made = null;

		try {
			made = type.create(level);

			if (made != null) {
				out.add("classes", classes(made.getClass(), Entity.class));
				out.addProperty("bossBar", keepsBossBar(made.getClass()));

				if (made instanceof LivingEntity living) {
					out.addProperty("health", living.getMaxHealth());
					out.addProperty("armor", living.getArmorValue());
					out.addProperty("attack", living.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)
							? living.getAttributeValue(Attributes.ATTACK_DAMAGE) : -1.0);
				}
			}
		} catch (Throwable e) {
			out.addProperty("createError", e.toString());
		} finally {
			if (made != null) {
				made.discard();
			}
		}

		return out;
	}

	/** Whether the class (or a superclass) has a boss bar field: how 1.20.1 bosses show their health. */
	private static boolean keepsBossBar(Class<?> type) {
		for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
			for (Field field : c.getDeclaredFields()) {
				if (BossEvent.class.isAssignableFrom(field.getType())) {
					return true;
				}
			}
		}

		return false;
	}

	private static double sum(ItemStack stack, EquipmentSlot slot, Attribute attribute) {
		double total = 0.0;

		for (Map.Entry<Attribute, AttributeModifier> entry : stack.getAttributeModifiers(slot).entries()) {
			if (entry.getKey() == attribute && entry.getValue().getOperation() == AttributeModifier.Operation.ADDITION) {
				total += entry.getValue().getAmount();
			}
		}

		return total;
	}

	private static JsonArray classes(Class<?> type, Class<?> stop) {
		JsonArray out = new JsonArray();

		for (Class<?> c = type; c != null && c != stop && c != Object.class; c = c.getSuperclass()) {
			out.add(c.getSimpleName());
		}

		return out;
	}

	private static JsonArray strings(List<String> values) {
		JsonArray out = new JsonArray();
		values.forEach(out::add);
		return out;
	}

	private static JsonObject error(String kind, String id, Throwable e) {
		JsonObject out = new JsonObject();
		out.addProperty("kind", kind);
		out.addProperty("id", id);
		out.addProperty("error", e.toString());
		return out;
	}
}
