package com.freshorangeslices.bloomlevelup.effect;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;
import com.freshorangeslices.bloomlevelup.data.BloomServices;
import com.freshorangeslices.bloomlevelup.player.BloomAttachments;
import com.freshorangeslices.bloomlevelup.player.BloomProfile;
import com.freshorangeslices.bloomlevelup.progression.ProgressionService;
import com.freshorangeslices.bloomlevelup.skill.armor.ArmorIds;
import com.freshorangeslices.bloomlevelup.skill.armor.ArmorStyles;
import com.freshorangeslices.bloomlevelup.stats.StatFormulas;
import com.freshorangeslices.bloomlevelup.stats.StatFormulas.ArmorStyle;

/**
 * Turns stat points (and the simple always-on Majors) into vanilla attribute modifiers. Idempotent: it
 * recomputes every value from the profile and only touches modifiers whose value changed, so it can run
 * on every profile change and on a short timer without churn.
 *
 * <p>Modifiers are permanent (saved with the player) so max health is already correct when the player's
 * health is loaded on login; otherwise extra hearts would be clipped every relog.
 */
public final class StatEffects {
	public static final String VITALITY = BloomLevelUp.key("armor/vitality");
	public static final String DEFENSE = BloomLevelUp.key("armor/defense");
	public static final String POWER = BloomLevelUp.key("combat/power");
	public static final String TEMPO = BloomLevelUp.key("combat/tempo");
	public static final String SPEED = BloomLevelUp.key("misc/speed");
	public static final String TITAN_HIDE = BloomLevelUp.key("armor/titan_hide");

	private static final Identifier VITALITY_MOD = BloomLevelUp.id("stat/vitality");
	private static final Identifier DEFENSE_MOD = BloomLevelUp.id("stat/defense");
	private static final Identifier POWER_MOD = BloomLevelUp.id("stat/power");
	private static final Identifier TEMPO_MOD = BloomLevelUp.id("stat/tempo");
	private static final Identifier SPEED_MOD = BloomLevelUp.id("stat/speed");
	private static final Identifier TITAN_SPEED_MOD = BloomLevelUp.id("major/titan_hide_speed");
	private static final Identifier TITAN_FALL_MOD = BloomLevelUp.id("major/titan_hide_fall");
	private static final Identifier PROTEIN_MOD = BloomLevelUp.id("major/protein");
	private static final Identifier HEAVY_KNOCKBACK_MOD = BloomLevelUp.id("major/fitting_armor_heavy");

	/** Power: +6 attack damage at 100 points (first-pass linear, per the Combat doc). */
	private static final double POWER_PER_POINT = 0.06;

	private StatEffects() {
	}

	public static void refresh(ServerPlayer player) {
		ProgressionService service = BloomServices.progression();
		BloomProfile profile = BloomAttachments.profile(player);

		boolean titanHide = service.isMajorActive(profile, TITAN_HIDE) && armorSlotsEmpty(player);

		// Protein!: the meal count belongs to owning the Major; refunding it (or Bloom+) starts over.
		if (!profile.owns(ArmorIds.PROTEIN) && profile.counter(ArmorIds.PROTEIN_MEALS) != 0) {
			profile.setCounter(ArmorIds.PROTEIN_MEALS, 0);
		}

		double proteinHealth = service.isMajorActive(profile, ArmorIds.PROTEIN)
				? StatFormulas.proteinBonusHealth(profile.counter(ArmorIds.PROTEIN_MEALS)) : 0.0;
		apply(player, Attributes.MAX_HEALTH, PROTEIN_MOD, proteinHealth, AttributeModifier.Operation.ADD_VALUE);

		// Fitting Armor / Heavy: strong knockback resistance that tapers off if you already resist knockback.
		boolean heavy = service.isMajorActive(profile, ArmorIds.FITTING_ARMOR) && ArmorStyles.styleOf(player) == ArmorStyle.HEAVY;
		double heavyKnockback = 0.0;

		if (heavy) {
			AttributeInstance knockback = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);

			if (knockback != null) {
				AttributeModifier ours = knockback.getModifier(HEAVY_KNOCKBACK_MOD);
				double others = knockback.getValue() - (ours == null ? 0.0 : ours.amount());
				heavyKnockback = StatFormulas.heavyKnockbackResistance(others);
			}
		}

		apply(player, Attributes.KNOCKBACK_RESISTANCE, HEAVY_KNOCKBACK_MOD, heavyKnockback, AttributeModifier.Operation.ADD_VALUE);

		double defenseArmor = StatFormulas.defenseArmor(service.statValue(profile, DEFENSE));

		if (titanHide) {
			// +2 innate armor first, then the whole Defense value doubles.
			defenseArmor = (defenseArmor + 2.0) * 2.0;
		}

		apply(player, Attributes.MAX_HEALTH, VITALITY_MOD,
				StatFormulas.vitalityBonusHealth(service.statValue(profile, VITALITY)), AttributeModifier.Operation.ADD_VALUE);
		apply(player, Attributes.ARMOR, DEFENSE_MOD, defenseArmor, AttributeModifier.Operation.ADD_VALUE);
		apply(player, Attributes.ATTACK_DAMAGE, POWER_MOD,
				service.statValue(profile, POWER) * POWER_PER_POINT, AttributeModifier.Operation.ADD_VALUE);
		// Tempo: 2x attack speed at 100 points; multiplies the weapon's own speed.
		apply(player, Attributes.ATTACK_SPEED, TEMPO_MOD,
				service.statValue(profile, TEMPO) / 100.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		// Speed: +1% movement per point, 2x at 100.
		apply(player, Attributes.MOVEMENT_SPEED, SPEED_MOD,
				service.statValue(profile, SPEED) / 100.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);

		// Titan Hide: +50% movement, -70% fall damage. (Its 2x knockback drawback arrives with the Armor chunk.)
		apply(player, Attributes.MOVEMENT_SPEED, TITAN_SPEED_MOD, titanHide ? 0.5 : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		apply(player, Attributes.FALL_DAMAGE_MULTIPLIER, TITAN_FALL_MOD, titanHide ? -0.7 : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	public static boolean armorSlotsEmpty(ServerPlayer player) {
		return player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
				&& player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()
				&& player.getItemBySlot(EquipmentSlot.LEGS).isEmpty()
				&& player.getItemBySlot(EquipmentSlot.FEET).isEmpty();
	}

	private static void apply(ServerPlayer player, Holder<Attribute> attribute, Identifier id, double amount,
			AttributeModifier.Operation operation) {
		AttributeInstance instance = player.getAttribute(attribute);

		if (instance == null) {
			return;
		}

		AttributeModifier existing = instance.getModifier(id);

		if (amount == 0.0) {
			if (existing != null) {
				instance.removeModifier(id);
			}

			return;
		}

		if (existing != null && Math.abs(existing.amount() - amount) < 1e-9 && existing.operation() == operation) {
			return;
		}

		instance.addOrReplacePermanentModifier(new AttributeModifier(id, amount, operation));
	}
}
