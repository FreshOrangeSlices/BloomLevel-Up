package com.freshorangeslices.bloompets;

import java.lang.reflect.Method;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * A companion's hidden armor, the Warionex add-on's (its {@code armor.HiddenArmor}, found by name: Bloom Pets doesn't
 * need the add-on, and without it the pet screen has no armor switch). The pet screen's dot hides all of a companion's
 * armor for everyone, or shows it again; it still protects. FreshOrangeSlices' playtest, 2026-11-05.
 */
public final class ArmorBridge {
	/** The add-on's mask on the entity (bit per armor piece; all four hidden is 15). */
	private static final String KEY = "legionwarex.hiddenArmor";
	private static final int ALL = 0b1111;
	private static Method toggleAll;
	private static boolean looked;

	private ArmorBridge() {
	}

	private static Method toggleAll() {
		if (!looked) {
			looked = true;

			try {
				toggleAll = Class.forName("com.freshorangeslices.legionwarex.armor.HiddenArmor").getMethod("toggleAll", LivingEntity.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				toggleAll = null;
			}
		}

		return toggleAll;
	}

	/** Whether hidden armor is there (the add-on is). */
	public static boolean available() {
		return toggleAll() != null;
	}

	public static boolean hidden(Entity pet) {
		return (pet.getPersistentData().getInt(KEY) & ALL) == ALL;
	}

	/** The pet screen's switch for a companion (it has to be loaded). */
	public static Component toggle(ServerPlayer player, PetRegistry.Record record) {
		if (record.kind != Pets.Kind.MODDED || toggleAll() == null) {
			return null;
		}

		if (!(Pets.loaded(player.server, record.pet) instanceof LivingEntity pet)) {
			return Component.translatable("bloompets.away", record.name);
		}

		try {
			boolean hidden = (Boolean) toggleAll().invoke(null, pet);
			return Component.translatable(hidden ? "bloompets.armor.hidden" : "bloompets.armor.shown", record.name);
		} catch (ReflectiveOperationException | RuntimeException e) {
			BloomPets.LOGGER.warn("Bloom Pets: couldn't switch {}'s armor: {}", record.name, e.toString());
			return null;
		}
	}
}
