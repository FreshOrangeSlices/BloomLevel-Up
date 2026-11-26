package com.freshorangeslices.legionwarex.magic;

import com.freshorangeslices.legionwarex.LegionWareX;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Fabled Weaponry's tomes work with all magic (FreshOrangeSlices, 2026-11-24): a tome counts as Spell Power for every
 * school of both Iron's Spells and Spell Engine, Leather +10%, Iron +20%, Golden +30%, Diamond +40%, Netherite +50%,
 * added to the other spell bonuses (not multiplying them), held in either hand or worn in Iron's spellbook slot or Spell
 * Engine's spell book slot (the add-on's curios tags let them in); only the best one counts. Vanilla magic (thrown
 * Harming and the like) keeps the mod's own x1.1 to x1.5. Set on the player every half second, as one modifier on each
 * mod's Spell Power ({@link #ID}).
 * <p>Also: a Fabled mace's hit with a tome in the off hand adds magic damage with no attacker, so its kills gave no XP or
 * player-only drops; magic damage with no attacker right after a player's hit with a Fabled weapon is credited to them.
 */
public final class Tomes {
	public static final UUID ID = UUID.fromString("6c1f3b52-9e4a-4d87-b0a1-2f7e5c9d3a14");
	public static final String NAME = "legionwarex tome";
	/** Each tome's Spell Power, as a share. */
	public static final Map<String, Double> POWER = new LinkedHashMap<>();
	/** Spell Engine's Spell Power counts in points of 100 (150 is +50%), Iron's in shares of 1. */
	static final String SPELL_ENGINE = "spell_power:generic";
	static final String IRONS = "irons_spellbooks:spell_power";
	/** Game tests: a tome table and the two attributes in place of the real ones (null: the real ones). */
	public static Map<String, Double> testPower;
	public static List<String> testAttributes;
	private static final Map<UUID, Hit> LAST_HITS = new HashMap<>();
	private static boolean creditLogged;

	static {
		POWER.put("fabledweaponry:tome_leather", 0.10);
		POWER.put("fabledweaponry:tome_iron", 0.20);
		POWER.put("fabledweaponry:tome_gold", 0.30);
		POWER.put("fabledweaponry:tome_diamond", 0.40);
		POWER.put("fabledweaponry:tome_netherite", 0.50);
	}

	private record Hit(Player player, long time) {
	}

	private Tomes() {
	}

	static Map<String, Double> power() {
		return testPower != null ? testPower : POWER;
	}

	/** A stack's tome power, or 0 if it isn't a tome. */
	public static double power(ItemStack stack) {
		if (stack.isEmpty()) {
			return 0.0;
		}

		ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
		return id == null ? 0.0 : power().getOrDefault(id.toString(), 0.0);
	}

	/** The best tome's power among what the player holds and wears (0 for none). */
	public static double best(Player player) {
		double best = Math.max(power(player.getMainHandItem()), power(player.getOffhandItem()));

		for (ItemStack worn : curios(player, stack -> power(stack) > 0.0)) {
			best = Math.max(best, power(worn));
		}

		return best;
	}

	/** Sets the player's tome Spell Power on both mods' attributes (none: taken off). */
	public static void apply(Player player, double power) {
		List<String> ids = testAttributes != null ? testAttributes : List.of(IRONS, SPELL_ENGINE);

		for (String id : ids) {
			ResourceLocation key = ResourceLocation.tryParse(id);
			Attribute attribute = key == null ? null : ForgeRegistries.ATTRIBUTES.getValue(key);
			AttributeInstance instance = attribute == null ? null : player.getAttribute(attribute);

			if (instance == null) {
				continue;
			}

			AttributeModifier current = instance.getModifier(ID);

			if (current != null && current.getAmount() == power) {
				continue;
			}

			if (current != null) {
				instance.removeModifier(ID);
			}

			if (power > 0.0) {
				// Multiplying the base: added to the other bonuses on the same attribute, not multiplying them.
				instance.addTransientModifier(new AttributeModifier(ID, NAME, power, AttributeModifier.Operation.MULTIPLY_BASE));
			}
		}
	}

	@SubscribeEvent
	public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
		if (event.phase == TickEvent.Phase.END && !event.player.level().isClientSide() && event.player.tickCount % 10 == 3) {
			apply(event.player, best(event.player));
		}
	}

	// ---------------------------------------------------------------- Curios, by name

	private static Method inventory;
	private static Method findCurios;
	private static Method slotStack;
	private static boolean curiosTried;

	@SuppressWarnings("unchecked")
	static List<ItemStack> curios(LivingEntity entity, Predicate<ItemStack> filter) {
		if (!curiosTried) {
			curiosTried = true;

			if (ModList.get() != null && ModList.get().isLoaded("curios")) {
				try {
					inventory = Class.forName("top.theillusivec4.curios.api.CuriosApi").getMethod("getCuriosInventory", LivingEntity.class);
					findCurios = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler").getMethod("findCurios", Predicate.class);
					slotStack = Class.forName("top.theillusivec4.curios.api.SlotResult").getMethod("stack");
				} catch (ReflectiveOperationException e) {
					LegionWareX.LOGGER.warn("LegionWareX magic: Curios not readable ({}): tomes count only in hand", e.toString());
					inventory = null;
				}
			}
		}

		if (inventory == null) {
			return List.of();
		}

		try {
			Optional<?> handler = ((LazyOptional<?>) inventory.invoke(null, entity)).resolve();

			if (handler.isEmpty()) {
				return List.of();
			}

			List<?> found = (List<?>) findCurios.invoke(handler.get(), filter);
			return found.stream().map(result -> {
				try {
					return (ItemStack) slotStack.invoke(result);
				} catch (ReflectiveOperationException e) {
					return ItemStack.EMPTY;
				}
			}).toList();
		} catch (ReflectiveOperationException | RuntimeException e) {
			return List.of();
		}
	}

	// ---------------------------------------------------------------- the tooltip

	@SubscribeEvent
	public static void onTooltip(ItemTooltipEvent event) {
		double power = power(event.getItemStack());

		if (power > 0.0) {
			event.getToolTip().add(Component.literal("+" + Math.round(power * 100.0) + "% Spell Power (all magic)").withStyle(ChatFormatting.BLUE));
			event.getToolTip().add(Component.literal("Held in either hand, or in a spellbook slot; the best tome counts").withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	// ---------------------------------------------------------------- a Fabled mace's magic, credited

	/** A player's hit with a Fabled weapon, remembered for a tick or two. */
	@SubscribeEvent
	public static void onAttack(AttackEntityEvent event) {
		Player player = event.getEntity();
		ResourceLocation weapon = ForgeRegistries.ITEMS.getKey(player.getMainHandItem().getItem());

		if (!player.level().isClientSide() && weapon != null && weapon.getNamespace().equals("fabledweaponry")) {
			LAST_HITS.put(event.getTarget().getUUID(), new Hit(player, player.level().getGameTime()));
		}
	}

	/** Magic with no attacker right after that hit is the weapon's: the player gets the credit (XP, player-only drops). */
	@SubscribeEvent(priority = EventPriority.LOW)
	public static void onHurt(LivingHurtEvent event) {
		LivingEntity target = event.getEntity();

		if (target.level().isClientSide() || event.getSource().getEntity() != null
				|| !(event.getSource().is(DamageTypes.MAGIC) || event.getSource().is(DamageTypes.INDIRECT_MAGIC))) {
			return;
		}

		Hit hit = LAST_HITS.remove(target.getUUID());

		if (hit != null && target.level().getGameTime() - hit.time() <= 2L && hit.player().isAlive()) {
			target.setLastHurtByPlayer(hit.player());

			if (!creditLogged) {
				creditLogged = true;
				LegionWareX.LOGGER.info("LegionWareX magic: credited {} for magic with no attacker right after their hit ({})", hit.player().getGameProfile().getName(),
						ForgeRegistries.ITEMS.getKey(hit.player().getMainHandItem().getItem()));
			}
		}

		if (LAST_HITS.size() > 512) {
			long now = target.level().getGameTime();
			LAST_HITS.values().removeIf(old -> now - old.time() > 40L);
		}
	}

	/** For the game tests: whether an entity has the credit a player kill needs. */
	public static boolean creditedTo(LivingEntity target, Entity player) {
		return target.getLastHurtByMob() == player || target.getKillCredit() == player;
	}
}
