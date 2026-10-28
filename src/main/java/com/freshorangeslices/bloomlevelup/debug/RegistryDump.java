package com.freshorangeslices.bloomlevelup.debug;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSpawnOverride;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.extensions.IForgeItem;
import net.minecraftforge.event.TickEvent;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;
import com.freshorangeslices.bloomlevelup.compat.BetterCombat;

/**
 * A tool for supporting modpacks, off unless the server starts with {@code -Dbloomlevelup.dump=<file>}: two seconds after
 * the server is up it writes every item (its classes, tags, damage, armor, Better Combat category), every entity type
 * (health, armor, whether it keeps a boss bar), every recipe (id, type, result), and every biome's natural spawns and
 * every structure's own spawns (after all mods' biome and structure modifiers) to that file, one JSON object per
 * line. CI runs it on LegionWareX to sort the pack's weapons, armor and bosses into Bloom's tags, and to check the
 * recipes and items the pack's own data pack takes out. Does nothing to the world: the entities it measures are
 * made and thrown away, never added.
 */
public final class RegistryDump {
	private static final Gson GSON = new Gson();
	private static final Map<String, Method> HOOKS = hookMethods();

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
				lines.add(GSON.toJson(item(level, item)));
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

		// Every recipe the server loaded (id, type, result), so a pack can check the ones it takes out are gone.
		int recipes = 0;

		for (Recipe<?> recipe : level.getServer().getRecipeManager().getRecipes()) {
			try {
				lines.add(GSON.toJson(recipe(level, recipe)));
				recipes++;
			} catch (Throwable e) {
				lines.add(GSON.toJson(error("recipe", String.valueOf(recipe.getId()), e)));
			}
		}

		// What spawns where, after every mod's biome and structure modifiers: each biome's natural spawns by category,
		// and the structures that replace them inside (for tuning a pack's spawns).
		Registry<Biome> biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
		int places = 0;

		for (Map.Entry<ResourceKey<Biome>, Biome> entry : biomes.entrySet()) {
			try {
				lines.add(GSON.toJson(biome(biomes, entry.getKey(), entry.getValue())));
				places++;
			} catch (Throwable e) {
				lines.add(GSON.toJson(error("biome", entry.getKey().location().toString(), e)));
			}
		}

		Registry<Structure> structures = level.registryAccess().registryOrThrow(Registries.STRUCTURE);

		for (Map.Entry<ResourceKey<Structure>, Structure> entry : structures.entrySet()) {
			try {
				JsonObject row = structure(entry.getKey(), entry.getValue());

				if (row != null) {
					lines.add(GSON.toJson(row));
					places++;
				}
			} catch (Throwable e) {
				lines.add(GSON.toJson(error("structure", entry.getKey().location().toString(), e)));
			}
		}

		try {
			Files.write(target, lines, StandardCharsets.UTF_8);
			BloomLevelUp.LOGGER.info("Bloom registry dump: {} items, {} entity types, {} recipes, {} biomes and structures -> {}", items,
					entities, recipes, places, target.toAbsolutePath());
		} catch (IOException e) {
			BloomLevelUp.LOGGER.warn("Bloom registry dump couldn't be written to {}", target, e);
		}
	}

	private static JsonObject item(ServerLevel level, Item item) {
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

		// What a weapon does beyond its numbers: the uses and hooks its own code adds, what it implements, its other
		// stats, and what its tooltip says (for sorting a pack's weapons into ones with a skill, an effect, or neither).
		if (bc != null || item instanceof SwordItem || item instanceof AxeItem || item instanceof TridentItem
				|| item instanceof ProjectileWeaponItem || out.get("damage").getAsDouble() > 0) {
			out.add("hooks", hooks(item.getClass()));
			out.add("interfaces", interfaces(item.getClass()));
			out.add("stats", extraStats(stack));
			out.add("tooltip", tooltip(level, stack));
		}

		return out;
	}

	/**
	 * The behaviour methods an item can override, found by their parameter types (their names differ between a dev run
	 * and a real game); Forge's own keep their names.
	 */
	private static Map<String, Method> hookMethods() {
		Map<String, Method> out = new LinkedHashMap<>();
		hook(out, "use", InteractionResultHolder.class, Level.class, Player.class, InteractionHand.class);
		hook(out, "useOn", InteractionResult.class, UseOnContext.class);
		hook(out, "useOnEntity", InteractionResult.class, ItemStack.class, Player.class, LivingEntity.class, InteractionHand.class);
		hook(out, "releaseUsing", void.class, ItemStack.class, Level.class, LivingEntity.class, int.class);
		hook(out, "useTick", void.class, Level.class, LivingEntity.class, ItemStack.class, int.class);
		hook(out, "finishUsing", ItemStack.class, ItemStack.class, Level.class, LivingEntity.class);
		hook(out, "hurtEnemy", boolean.class, ItemStack.class, LivingEntity.class, LivingEntity.class);
		hook(out, "inventoryTick", void.class, ItemStack.class, Level.class, Entity.class, int.class, boolean.class);
		hook(out, "tooltip", void.class, ItemStack.class, Level.class, List.class, TooltipFlag.class);

		try {
			out.put("leftClickEntity", IForgeItem.class.getMethod("onLeftClickEntity", ItemStack.class, Player.class, Entity.class));
			out.put("swing", IForgeItem.class.getMethod("onEntitySwing", ItemStack.class, LivingEntity.class));
		} catch (NoSuchMethodException e) {
			BloomLevelUp.LOGGER.warn("Bloom registry dump: Forge's item hooks weren't found", e);
		}

		return out;
	}

	private static void hook(Map<String, Method> out, String name, Class<?> returns, Class<?>... parameters) {
		for (Method method : Item.class.getDeclaredMethods()) {
			if (method.getReturnType() == returns && Arrays.equals(method.getParameterTypes(), parameters)) {
				out.put(name, method);
				return;
			}
		}

		BloomLevelUp.LOGGER.warn("Bloom registry dump: no Item method for {}", name);
	}

	/** Which of those an item's class overrides outside Minecraft and Forge, and in which class. */
	private static JsonObject hooks(Class<?> type) {
		JsonObject out = new JsonObject();

		for (Map.Entry<String, Method> hook : HOOKS.entrySet()) {
			try {
				Class<?> owner = type.getMethod(hook.getValue().getName(), hook.getValue().getParameterTypes()).getDeclaringClass();

				if (!vanilla(owner)) {
					out.addProperty(hook.getKey(), owner.getSimpleName());
				}
			} catch (NoSuchMethodException | RuntimeException e) {
				// not there
			}
		}

		return out;
	}

	/** The interfaces a class and its superclasses implement, other than Minecraft's, Forge's and Java's. */
	private static JsonArray interfaces(Class<?> type) {
		Set<String> names = new TreeSet<>();
		List<Class<?>> queue = new ArrayList<>();

		for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
			queue.addAll(Arrays.asList(c.getInterfaces()));
		}

		while (!queue.isEmpty()) {
			Class<?> next = queue.remove(queue.size() - 1);

			if (!vanilla(next) && !next.getName().startsWith("java.") && names.add(next.getSimpleName())) {
				queue.addAll(Arrays.asList(next.getInterfaces()));
			}
		}

		return strings(List.copyOf(names));
	}

	private static boolean vanilla(Class<?> type) {
		return type.getName().startsWith("net.minecraft.") || type.getName().startsWith("net.minecraftforge.");
	}

	/** Main-hand attribute modifiers other than plain attack damage and speed, summed by attribute and operation. */
	private static JsonObject extraStats(ItemStack stack) {
		Map<String, Double> totals = new TreeMap<>();

		for (Map.Entry<Attribute, AttributeModifier> entry : stack.getAttributeModifiers(EquipmentSlot.MAINHAND).entries()) {
			AttributeModifier.Operation operation = entry.getValue().getOperation();

			if ((entry.getKey() == Attributes.ATTACK_DAMAGE || entry.getKey() == Attributes.ATTACK_SPEED)
					&& operation == AttributeModifier.Operation.ADDITION) {
				continue;
			}

			String name = String.valueOf(BuiltInRegistries.ATTRIBUTE.getKey(entry.getKey())) + switch (operation) {
				case ADDITION -> "";
				case MULTIPLY_BASE -> " (x base)";
				case MULTIPLY_TOTAL -> " (x total)";
			};
			totals.merge(name, entry.getValue().getAmount(), Double::sum);
		}

		JsonObject out = new JsonObject();
		totals.forEach(out::addProperty);
		return out;
	}

	/**
	 * The item's own tooltip lines as the server reads them, and the translation keys in them (a dedicated server has
	 * only vanilla's English, so a mod's lines come back as their keys). Tooltips that need the client fail here.
	 */
	private static JsonObject tooltip(ServerLevel level, ItemStack stack) {
		JsonObject out = new JsonObject();

		try {
			List<Component> lines = new ArrayList<>();
			stack.getItem().appendHoverText(stack, level, lines, TooltipFlag.NORMAL);
			Set<String> keys = new LinkedHashSet<>();
			JsonArray text = new JsonArray();

			for (Component line : lines) {
				text.add(line.getString());
				keys(line, keys);
			}

			out.add("lines", text);
			out.add("keys", strings(List.copyOf(keys)));
		} catch (Throwable e) {
			out.addProperty("error", e.toString());
		}

		return out;
	}

	private static void keys(Component component, Set<String> out) {
		if (component.getContents() instanceof TranslatableContents translatable) {
			out.add(translatable.getKey());

			for (Object argument : translatable.getArgs()) {
				if (argument instanceof Component inner) {
					keys(inner, out);
				}
			}
		}

		for (Component sibling : component.getSiblings()) {
			keys(sibling, out);
		}
	}

	private static JsonObject recipe(ServerLevel level, Recipe<?> recipe) {
		JsonObject out = new JsonObject();
		out.addProperty("kind", "recipe");
		out.addProperty("id", recipe.getId().toString());
		out.addProperty("type", String.valueOf(BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType())));
		ItemStack result = recipe.getResultItem(level.registryAccess());

		if (result != null && !result.isEmpty()) {
			out.addProperty("result", BuiltInRegistries.ITEM.getKey(result.getItem()).toString());
		}

		return out;
	}

	private static JsonObject biome(Registry<Biome> biomes, ResourceKey<Biome> key, Biome biome) {
		JsonObject out = new JsonObject();
		out.addProperty("kind", "biome");
		out.addProperty("id", key.location().toString());
		out.add("tags", strings(biomes.getHolderOrThrow(key).tags().map(tag -> tag.location().toString()).sorted().toList()));
		MobSpawnSettings settings = biome.getMobSettings();
		out.addProperty("creatureProbability", settings.getCreatureProbability());
		JsonObject spawns = new JsonObject();

		for (MobCategory category : MobCategory.values()) {
			JsonArray list = spawners(settings.getMobs(category).unwrap());

			if (!list.isEmpty()) {
				spawns.add(category.getName(), list);
			}
		}

		out.add("spawns", spawns);
		return out;
	}

	/** A structure's own spawns (they replace the biome's inside it), or null when it has none. */
	private static JsonObject structure(ResourceKey<Structure> key, Structure structure) {
		JsonObject spawns = new JsonObject();

		for (Map.Entry<MobCategory, StructureSpawnOverride> entry : structure.spawnOverrides().entrySet()) {
			JsonObject override = new JsonObject();
			override.addProperty("box", entry.getValue().boundingBox().getSerializedName());
			override.add("spawns", spawners(entry.getValue().spawns().unwrap()));
			spawns.add(entry.getKey().getName(), override);
		}

		if (spawns.size() == 0) {
			return null;
		}

		JsonObject out = new JsonObject();
		out.addProperty("kind", "structure");
		out.addProperty("id", key.location().toString());
		out.add("spawns", spawns);
		return out;
	}

	private static JsonArray spawners(List<MobSpawnSettings.SpawnerData> list) {
		JsonArray out = new JsonArray();

		for (MobSpawnSettings.SpawnerData data : list) {
			JsonObject spawner = new JsonObject();
			spawner.addProperty("type", String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(data.type)));
			spawner.addProperty("weight", data.getWeight().asInt());
			spawner.addProperty("min", data.minCount);
			spawner.addProperty("max", data.maxCount);
			out.add(spawner);
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
