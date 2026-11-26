package com.freshorangeslices.legionwarex.magic;

import com.freshorangeslices.legionwarex.LegionWareX;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.util.thread.EffectiveSide;
import net.minecraftforge.registries.ForgeRegistries;
import com.google.common.collect.Multimap;

/**
 * Spell Engine's and Iron's Spells' gear count for both (FreshOrangeSlices, 2026-11-24): every spell bonus on an item
 * also gives the matching stat of the other magic mod, as a real stat of the item while worn or held (armor, weapons,
 * and the ring, necklace and spellbook slots through Curios' attribute event), Iron's upgrade orbs included (Iron's adds
 * them on the same events at the normal priority; the copies are made last). A copy is the item's own modifier with a
 * name of its own ({@link #NAME}), so it's never copied back.
 * <p>Pairs (Spell Engine / Iron's): Spell Power / Spell Power, Fire / Fire, Frost / Ice, Lightning / Lightning, Healing /
 * Holy, Arcane / Ender, Soul / Eldritch, Haste / Cooldown Reduction, Spell Resistance / Spell Resist; Spell Engine's Holy,
 * Blood and Nature where a mod adds them. Both mods' gear bonuses are shares (+5% on one is +5% on the other):
 * <ul>
 * <li>schools: a multiplying bonus is copied as it is; a flat one (a Spell Engine staff's "+4 Fire Spell Power", its
 * spells' damage scale, not a share) isn't copied;</li>
 * <li>Spell Power and Haste, which Spell Engine counts in points of 100 (110 is +10%): a flat bonus there is a share
 * on Iron's side divided by 100, and the other way;</li>
 * <li>resistance: Iron's is a share, Spell Engine's points (20 points halve spell damage): 1 point for each 5%.</li>
 * </ul>
 * Tooltips: where both names read the same (Fire Spell Power and Fire Spell Power) the copy's line is hidden, so the
 * item shows one line that counts for both; where they differ (Arcane and Ender) both lines show.
 */
public final class MagicBridge {
	public static final String NAME = "legionwarex magic copy";

	public enum Kind {
		/** A school's power: base 1 on both sides; only multiplying bonuses are copied. */
		SCHOOL,
		/** Spell Engine counts in points of 100 (Spell Power, Haste); Iron's in shares of 1. */
		POINTS,
		/** Iron's resistance is a share, Spell Engine's points: 20 points for each 1 (1 for each 5%). */
		RESIST
	}

	/** A pair, as ids: Spell Engine's side, Iron's side. */
	public record Pair(String spellEngine, String irons, Kind kind) {
	}

	public static final List<Pair> PAIRS = List.of(
			new Pair("spell_power:generic", "irons_spellbooks:spell_power", Kind.POINTS),
			new Pair("spell_power:fire", "irons_spellbooks:fire_spell_power", Kind.SCHOOL),
			new Pair("spell_power:frost", "irons_spellbooks:ice_spell_power", Kind.SCHOOL),
			new Pair("spell_power:lightning", "irons_spellbooks:lightning_spell_power", Kind.SCHOOL),
			new Pair("spell_power:healing", "irons_spellbooks:holy_spell_power", Kind.SCHOOL),
			new Pair("spell_power:holy", "irons_spellbooks:holy_spell_power", Kind.SCHOOL),
			new Pair("spell_power:arcane", "irons_spellbooks:ender_spell_power", Kind.SCHOOL),
			new Pair("spell_power:soul", "irons_spellbooks:eldritch_spell_power", Kind.SCHOOL),
			new Pair("spell_power:blood", "irons_spellbooks:blood_spell_power", Kind.SCHOOL),
			new Pair("spell_power:nature", "irons_spellbooks:nature_spell_power", Kind.SCHOOL),
			new Pair("spell_power:haste", "irons_spellbooks:cooldown_reduction", Kind.POINTS),
			new Pair("spell_power:resistance.generic", "irons_spellbooks:spell_resist", Kind.RESIST));

	/** Game tests: pairs of vanilla attributes in place of the magic mods' (null: {@link #PAIRS}). */
	public static List<Pair> testPairs;

	/** A copy to make: onto this attribute, how (from Spell Engine's side or from Iron's). */
	private record Copy(Attribute target, Kind kind, boolean fromSpellEngine) {
	}

	private static Map<Attribute, List<Copy>> copies;
	private static List<Pair> builtFrom;
	/** Client side: each stack's copy lines to hide (the copy reads the same as its source). */
	private static final Map<ItemStack, List<String>> HIDE = Collections.synchronizedMap(new WeakHashMap<>());

	private MagicBridge() {
	}

	private static Attribute attribute(String id) {
		ResourceLocation key = ResourceLocation.tryParse(id);
		return key == null ? null : ForgeRegistries.ATTRIBUTES.getValue(key);
	}

	/** What each source attribute copies onto, built once the registries are in (again if the test pairs change). */
	static synchronized Map<Attribute, List<Copy>> copies() {
		List<Pair> pairs = testPairs != null ? testPairs : PAIRS;

		if (copies == null || builtFrom != pairs) {
			Map<Attribute, List<Copy>> built = new HashMap<>();

			for (Pair pair : pairs) {
				Attribute se = attribute(pair.spellEngine());
				Attribute iron = attribute(pair.irons());

				if (se == null || iron == null || se == iron) {
					continue;
				}

				built.computeIfAbsent(se, key -> new ArrayList<>()).add(new Copy(iron, pair.kind(), true));

				// Iron's Holy is Spell Engine's Healing (Spell Engine's own Holy, where a mod adds one, copies onto Iron's Holy only).
				if (!pair.spellEngine().equals("spell_power:holy")) {
					built.computeIfAbsent(iron, key -> new ArrayList<>()).add(new Copy(se, pair.kind(), false));
				}
			}

			copies = built;
			builtFrom = pairs;
		}

		return copies;
	}

	/** The copy of one modifier, or null when it isn't copied (a flat school bonus, a multiplied resistance). */
	public static AttributeModifier copy(AttributeModifier source, Kind kind, boolean fromSpellEngine, ResourceLocation target) {
		AttributeModifier.Operation op = source.getOperation();
		double amount = source.getAmount();

		switch (kind) {
			case SCHOOL -> {
				if (op == AttributeModifier.Operation.ADDITION) {
					return null;
				}
			}
			case POINTS -> {
				if (op == AttributeModifier.Operation.ADDITION) {
					amount = fromSpellEngine ? amount / 100.0 : amount * 100.0;
				}
			}
			case RESIST -> {
				if (fromSpellEngine) {
					if (op != AttributeModifier.Operation.ADDITION) {
						return null;
					}

					amount = amount / 20.0;
				} else {
					if (op == AttributeModifier.Operation.MULTIPLY_TOTAL) {
						return null;
					}

					amount = amount * 20.0;
					op = AttributeModifier.Operation.ADDITION;
				}
			}
		}

		if (amount == 0.0) {
			return null;
		}

		UUID id = UUID.nameUUIDFromBytes((source.getId() + "->" + target).getBytes(StandardCharsets.UTF_8));
		return new AttributeModifier(id, NAME, amount, op);
	}

	/** Adds the copies of an item's spell bonuses through {@code add}; returns the copy lines a tooltip should hide. */
	static List<String> addCopies(Multimap<Attribute, AttributeModifier> modifiers, BiConsumer<Attribute, AttributeModifier> add) {
		Map<Attribute, List<Copy>> table = copies();
		List<String> hide = null;

		if (table.isEmpty()) {
			return null;
		}

		for (Map.Entry<Attribute, AttributeModifier> entry : new ArrayList<>(modifiers.entries())) {
			List<Copy> targets = table.get(entry.getKey());

			if (targets == null || NAME.equals(entry.getValue().getName())) {
				continue;
			}

			for (Copy copy : targets) {
				ResourceLocation targetId = ForgeRegistries.ATTRIBUTES.getKey(copy.target());
				AttributeModifier made = copy(entry.getValue(), copy.kind(), copy.fromSpellEngine(), targetId);

				if (made == null) {
					continue;
				}

				add.accept(copy.target(), made);

				if (sameName(entry.getKey(), copy.target())) {
					String line = line(copy.target(), made);

					if (line != null) {
						if (hide == null) {
							hide = new ArrayList<>();
						}

						hide.add(line);
					}
				}
			}
		}

		return hide;
	}

	static boolean sameName(Attribute a, Attribute b) {
		return Component.translatable(a.getDescriptionId()).getString().equals(Component.translatable(b.getDescriptionId()).getString());
	}

	/** A modifier's tooltip line, as vanilla words it (ItemStack#getTooltipLines). */
	public static String line(Attribute attribute, AttributeModifier modifier) {
		double amount = modifier.getAmount();
		boolean multiplies = modifier.getOperation() != AttributeModifier.Operation.ADDITION;
		double shown = multiplies ? amount * 100.0 : attribute == Attributes.KNOCKBACK_RESISTANCE ? amount * 10.0 : amount;
		String key = amount > 0.0 ? "attribute.modifier.plus." : amount < 0.0 ? "attribute.modifier.take." : null;

		if (key == null) {
			return null;
		}

		return Component.translatable(key + modifier.getOperation().toValue(), ItemStack.ATTRIBUTE_MODIFIER_FORMAT.format(Math.abs(shown)),
				Component.translatable(attribute.getDescriptionId())).getString();
	}

	private static void remember(ItemStack stack, List<String> hide) {
		if (hide != null && !hide.isEmpty() && EffectiveSide.get().isClient()) {
			HIDE.merge(stack, hide, (old, add) -> {
				List<String> both = new ArrayList<>(old);
				add.stream().filter(line -> !both.contains(line)).forEach(both::add);
				return both;
			});
		}
	}

	// ---------------------------------------------------------------- events

	/**
	 * The item and Curios listeners, added once every mod has loaded (from the add-on's load-complete step): a mod that
	 * adds its own spell bonuses on the same event at the same last priority (Wizards' armor does) runs before them then,
	 * since listeners of one priority are called in the order they were added. Added at mod construction, the copies
	 * missed those bonuses (the pack test's magic check: Wizards' robes, "no copy").
	 */
	public static void registerLate() {
		MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, ItemAttributeModifierEvent.class, MagicBridge::onItemModifiers);
		registerCurios();
	}

	public static void onItemModifiers(ItemAttributeModifierEvent event) {
		remember(event.getItemStack(), addCopies(event.getModifiers(), event::addModifier));
	}

	/** Curios' attribute event (rings, necklaces, spellbooks), reached by name: the add-on doesn't build against Curios. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static void registerCurios() {
		if (!ModList.get().isLoaded("curios")) {
			return;
		}

		try {
			Class<?> type = Class.forName("top.theillusivec4.curios.api.event.CurioAttributeModifierEvent");
			Method getModifiers = type.getMethod("getModifiers");
			Method addModifier = type.getMethod("addModifier", Attribute.class, AttributeModifier.class);
			Method getItemStack = type.getMethod("getItemStack");
			MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, (Class) type, (Event event) -> {
				try {
					Multimap<Attribute, AttributeModifier> modifiers = (Multimap<Attribute, AttributeModifier>) getModifiers.invoke(event);
					List<String> hide = addCopies(modifiers, (attribute, modifier) -> {
						try {
							addModifier.invoke(event, attribute, modifier);
						} catch (ReflectiveOperationException e) {
							throw new IllegalStateException(e);
						}
					});
					remember((ItemStack) getItemStack.invoke(event), hide);
				} catch (ReflectiveOperationException | RuntimeException e) {
					logOnce("Curios' attribute event: " + e);
				}
			});
		} catch (ReflectiveOperationException e) {
			LegionWareX.LOGGER.warn("LegionWareX magic: Curios' attribute event not found ({}): rings and necklaces won't count for both", e.toString());
		}
	}

	private static boolean failed;

	private static void logOnce(String text) {
		if (!failed) {
			failed = true;
			LegionWareX.LOGGER.warn("LegionWareX magic: {}", text);
		}
	}

	/** The copy lines that read the same as their source go, so the item shows one line that counts for both. */
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onTooltip(ItemTooltipEvent event) {
		List<String> hide = HIDE.get(event.getItemStack());

		if (hide == null || hide.isEmpty()) {
			return;
		}

		for (String line : hide) {
			// The copy follows its source, so the second of two matching lines is the copy's.
			int first = -1;

			for (int i = 0; i < event.getToolTip().size(); i++) {
				if (event.getToolTip().get(i).getString().equals(line)) {
					if (first < 0) {
						first = i;
					} else {
						event.getToolTip().remove(i);
						break;
					}
				}
			}
		}
	}

	/** Items the check builds: a Spell Engine robe and an Iron's robe (worn), each should carry the other mod's copies. */
	static final Map<String, net.minecraft.world.entity.EquipmentSlot> CHECKED = new java.util.LinkedHashMap<>();

	static {
		CHECKED.put("wizards:netherite_fire_robe_chest", net.minecraft.world.entity.EquipmentSlot.CHEST);
		CHECKED.put("wizards:arcane_robe_head", net.minecraft.world.entity.EquipmentSlot.HEAD);
		CHECKED.put("irons_spellbooks:pyromancer_chestplate", net.minecraft.world.entity.EquipmentSlot.CHEST);
		CHECKED.put("irons_spellbooks:shadowwalker_helmet", net.minecraft.world.entity.EquipmentSlot.HEAD);
	}

	/** {@code /legionwarex magic}: what the copies on a few real items come to, for the pack test ("LegionWareX magic check:"). */
	public static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> node() {
		return net.minecraft.commands.Commands.literal("magic").executes(context -> {
			List<String> parts = new ArrayList<>();

			for (Map.Entry<String, net.minecraft.world.entity.EquipmentSlot> entry : CHECKED.entrySet()) {
				ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
				net.minecraft.world.item.Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);

				if (item == null || item == net.minecraft.world.item.Items.AIR) {
					parts.add(entry.getKey() + (id != null && ModList.get().isLoaded(id.getNamespace()) ? ": missing (renamed?)" : ": not in this layer"));
					continue;
				}

				Multimap<Attribute, AttributeModifier> modifiers = new ItemStack(item).getAttributeModifiers(entry.getValue());
				List<String> copied = new ArrayList<>();
				List<String> own = new ArrayList<>();
				int copyable = 0;

				for (Map.Entry<Attribute, AttributeModifier> e : modifiers.entries()) {
					String text = ForgeRegistries.ATTRIBUTES.getKey(e.getKey()) + " " + ItemStack.ATTRIBUTE_MODIFIER_FORMAT.format(e.getValue().getAmount())
							+ " " + e.getValue().getOperation().name().toLowerCase(java.util.Locale.ROOT);
					boolean isCopy = NAME.equals(e.getValue().getName());
					(isCopy ? copied : own).add(text);

					if (!isCopy) {
						for (Copy copy : copies().getOrDefault(e.getKey(), List.of())) {
							if (copy(e.getValue(), copy.kind(), copy.fromSpellEngine(), ForgeRegistries.ATTRIBUTES.getKey(copy.target())) != null) {
								copyable++;
							}
						}
					}
				}

				// An item whose spell bonuses are all flat school ones (Wizards' robes: +1 Fire Spell Power) has nothing to copy, by design.
				String verdict = !copied.isEmpty() ? "copies " + copied : copyable == 0 ? "nothing to copy " + own : "no copy of " + own;
				parts.add(entry.getKey() + ": " + verdict + " (own " + own.size() + ")");
			}

			String text = String.join("; ", parts);
			LegionWareX.LOGGER.info("LegionWareX magic check: {}", text);
			context.getSource().sendSuccess(() -> Component.literal(text), false);
			return 1;
		});
	}

	/** What the server runs with, for the boot test: the pairs found, and which side is missing for the rest. */
	@SubscribeEvent
	public static void onServerStarted(ServerStartedEvent event) {
		List<String> found = new ArrayList<>();
		List<String> missing = new ArrayList<>();

		for (Pair pair : PAIRS) {
			boolean se = attribute(pair.spellEngine()) != null;
			boolean iron = attribute(pair.irons()) != null;
			(se && iron ? found : missing).add(pair.spellEngine() + " / " + pair.irons() + (se && iron ? "" : se ? " (no Iron's)" : iron ? " (no Spell Engine)" : " (neither)"));
		}

		LegionWareX.LOGGER.info("LegionWareX magic: {} pair(s) count for both: {}; not paired: {}", found.size(), found, missing);
	}
}
