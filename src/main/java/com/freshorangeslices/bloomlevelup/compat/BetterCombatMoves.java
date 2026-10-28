package com.freshorangeslices.bloomlevelup.compat;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.logic.WeaponRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;
import com.freshorangeslices.bloomlevelup.skill.combat.CombatIds;
import com.freshorangeslices.bloomlevelup.skill.combat.CombatSkill;
import com.freshorangeslices.bloomlevelup.skill.combat.Weapons;
import com.freshorangeslices.bloomlevelup.skill.combat.Weapons.Family;

/**
 * Batter Up! with Better Combat (FreshOrangeSlices, from main): weapons that poke or slam swing like swords instead.
 * Spears, lances, glaives, halberds, rapiers, maces and hammers (by Better Combat's category) take the sword's combo,
 * or the greatsword's (claymore's) when they're held in two hands, so the animations fit the grip. Everything else
 * stays the weapon's own: its speed (the wind-up stretches with it), reach, idle pose and grip.
 *
 * <p>Only weapons of a material up to netherite, the vanilla end game (wooden, stone, copper, bronze, gold, iron, steel,
 * diamond, netherite: by the item id's first word), so named and unique weapons keep the moves their makers gave them.
 * Tridents (Bloom's trident tree) keep theirs. Both sides pick the swing the same way (the client for the animation
 * and the targets, the server for the damage and the sound), see {@code BetterCombatPlayerAttackHelperMixin}. Only
 * loaded when Better Combat is installed.
 */
public final class BetterCombatMoves {
	/** Better Combat's categories for weapons that poke or slam. */
	static final Set<String> RESWUNG = Set.of("spear", "lance", "glaive", "halberd", "rapier", "mace", "hammer", "greathammer", "trident");
	/** Item id prefixes of materials up to netherite. */
	static final List<String> MATERIALS = List.of("wooden_", "wood_", "stone_", "copper_", "bronze_", "tin_", "silver_", "golden_",
			"gold_", "iron_", "steel_", "diamond_", "netherite_");
	static final String SWORD = "sword";
	static final String CLAYMORE = "claymore";

	/** The combos last worked out, by category, and the registry they came from (see {@link #preset}). */
	private static final Map<String, WeaponAttributes.Attack[]> PRESETS = new HashMap<>();
	private static WeaponAttributes presetsFor;

	private BetterCombatMoves() {
	}

	/** The attributes this player swings this weapon with: the sword's or greatsword's combo with Batter Up!, else its own. */
	public static WeaponAttributes forPlayer(Player player, ItemStack stack, WeaponAttributes attributes) {
		if (player == null || stack == null || stack.isEmpty() || attributes == null || attributes.attacks() == null
				|| attributes.category() == null || !RESWUNG.contains(attributes.category().toLowerCase(Locale.ROOT))
				|| Weapons.is(stack, Family.TRIDENT) || !materialWeapon(stack) || !CombatSkill.activeAnySide(player, CombatIds.BATTER_UP)) {
			return attributes;
		}

		WeaponAttributes.Attack[] moves = preset(attributes.isTwoHanded() ? CLAYMORE : SWORD);

		if (moves == null || moves.length == 0) {
			return attributes;
		}

		return new WeaponAttributes(attributes.attackRange(), attributes.pose(), attributes.offHandPose(), attributes.two_handed(),
				attributes.category(), moves);
	}

	/** A weapon of a material up to netherite (by its item id's first word), as opposed to a named or unique one. */
	public static boolean materialWeapon(ItemStack stack) {
		ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());

		if (id == null) {
			return false;
		}

		for (String material : MATERIALS) {
			if (id.getPath().startsWith(material)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * The combo most weapons of a category share: the preset they inherit (Better Combat sends clients only the
	 * finished weapons, not its presets, so both sides read it off the weapons). Worked out again whenever Better
	 * Combat's registry is replaced (a reload, or joining a server).
	 */
	static synchronized WeaponAttributes.Attack[] preset(String category) {
		WeaponAttributes marker = WeaponRegistry.getAttributes(new ItemStack(Items.DIAMOND_SWORD));

		if (marker != presetsFor) {
			PRESETS.clear();
			presetsFor = marker;
		}

		return PRESETS.computeIfAbsent(category, BetterCombatMoves::commonest);
	}

	private static WeaponAttributes.Attack[] commonest(String category) {
		Map<String, Integer> counts = new TreeMap<>();
		Map<String, WeaponAttributes.Attack[]> combos = new HashMap<>();

		for (WeaponAttributes attributes : registered()) {
			if (attributes == null || attributes.attacks() == null || attributes.attacks().length == 0
					|| !category.equalsIgnoreCase(attributes.category())) {
				continue;
			}

			String key = signature(attributes.attacks());
			counts.merge(key, 1, Integer::sum);
			combos.putIfAbsent(key, attributes.attacks());
		}

		String best = null;

		for (Map.Entry<String, Integer> entry : counts.entrySet()) { // sorted, so a tie goes the same way on both sides
			if (best == null || entry.getValue() > counts.get(best)) {
				best = entry.getKey();
			}
		}

		return best == null ? null : combos.get(best);
	}

	/** Every weapon Better Combat knows, by item id (its registry is package-private; read once per lookup). */
	@SuppressWarnings("unchecked")
	private static List<WeaponAttributes> registered() {
		try {
			Field field = WeaponRegistry.class.getDeclaredField("registrations");
			field.setAccessible(true);
			Map<ResourceLocation, WeaponAttributes> map = (Map<ResourceLocation, WeaponAttributes>) field.get(null);
			Map<String, WeaponAttributes> sorted = new TreeMap<>();

			if (map != null) {
				map.forEach((id, attributes) -> sorted.put(String.valueOf(id), attributes));
			}

			return new ArrayList<>(sorted.values());
		} catch (ReflectiveOperationException | RuntimeException e) {
			BloomLevelUp.LOGGER.warn("Couldn't read Better Combat's weapons; Batter Up! leaves the swings alone", e);
			return List.of();
		}
	}

	/** What a combo looks like (its animations and shapes), to tell inherited presets apart. */
	private static String signature(WeaponAttributes.Attack[] attacks) {
		StringBuilder out = new StringBuilder();

		for (WeaponAttributes.Attack attack : attacks) {
			if (attack != null) {
				out.append(attack.animation()).append('/').append(attack.hitbox()).append('/').append(attack.angle()).append('/')
						.append(attack.damageMultiplier()).append('/').append(attack.upswing()).append(';');
			}
		}

		return out.toString();
	}
}
