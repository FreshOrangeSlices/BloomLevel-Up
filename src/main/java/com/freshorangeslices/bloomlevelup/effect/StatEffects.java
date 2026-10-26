package com.freshorangeslices.bloomlevelup.effect;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

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
import com.freshorangeslices.bloomlevelup.stats.StatFormulas;

/**
 * Turns stat points (and the simple always-on Majors) into vanilla attribute modifiers. Idempotent: it
 * recomputes every value from the profile and only touches modifiers whose value changed, so it can run
 * on every profile change and on a short timer without churn.
 *
 * <p>Modifiers are permanent (saved with the player) so max health is already correct when the player's
 * health is loaded on login; otherwise extra hearts would be clipped every relog.
 *
 * <p>1.20.1 attribute modifiers are keyed by UUID (26.x uses ids): each of Bloom's gets a fixed UUID made from
 * its id, so the same modifier is found again after a relog.
 */
public final class StatEffects {
	public static final String VITALITY = BloomLevelUp.key("armor/vitality");
	public static final String DEFENSE = BloomLevelUp.key("armor/defense");
	public static final String POWER = BloomLevelUp.key("combat/power");
	public static final String TEMPO = BloomLevelUp.key("combat/tempo");
	public static final String SPEED = BloomLevelUp.key("misc/speed");
	public static final String TITAN_HIDE = BloomLevelUp.key("armor/titan_hide");

	public static final ModifierId VITALITY_MOD = ModifierId.of("stat/vitality");
	public static final ModifierId DEFENSE_MOD = ModifierId.of("stat/defense");
	public static final ModifierId POWER_MOD = ModifierId.of("stat/power");
	public static final ModifierId TEMPO_MOD = ModifierId.of("stat/tempo");
	public static final ModifierId SPEED_MOD = ModifierId.of("stat/speed");

	/** Power: +6 attack damage at 100 points (first-pass linear, per the Combat doc). */
	public static final double POWER_PER_POINT = 0.06;

	/** A Bloom modifier's identity: its id (for logs and names) and the UUID 1.20.1 keys modifiers by. */
	public record ModifierId(String name, UUID uuid) {
		public static ModifierId of(String path) {
			String name = BloomLevelUp.key(path);
			return new ModifierId(name, UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)));
		}
	}

	/** A Bloom attribute modifier (amount 0 = none): from the item in hand, or from the profile. */
	public record HeldModifier(Attribute attribute, ModifierId id, double amount, AttributeModifier.Operation operation) {
	}

	private StatEffects() {
	}

	public static void refresh(ServerPlayer player) {
		for (HeldModifier modifier : profileModifiers(player)) {
			apply(player, modifier.attribute(), modifier.id(), modifier.amount(), modifier.operation());
		}

		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	/**
	 * The modifiers that come from the profile (stat points and always-on Majors), worked out from the profile as it
	 * is right now. The stat sheet asks this for a build that isn't in use, or a point not spent yet.
	 */
	public static List<HeldModifier> profileModifiers(ServerPlayer player) {
		ProgressionService service = BloomServices.progression();
		BloomProfile profile = BloomAttachments.profile(player);
		double defenseArmor = StatFormulas.defenseArmor(service.statValue(profile, DEFENSE));

		return List.of(
				new HeldModifier(Attributes.MAX_HEALTH, VITALITY_MOD, StatFormulas.vitalityBonusHealth(service.statValue(profile, VITALITY)),
						AttributeModifier.Operation.ADDITION),
				new HeldModifier(Attributes.ARMOR, DEFENSE_MOD, defenseArmor, AttributeModifier.Operation.ADDITION),
				new HeldModifier(Attributes.ATTACK_DAMAGE, POWER_MOD, service.statValue(profile, POWER) * POWER_PER_POINT,
						AttributeModifier.Operation.ADDITION),
				// Tempo: 2x attack speed at 100 points; multiplies the weapon's own speed.
				new HeldModifier(Attributes.ATTACK_SPEED, TEMPO_MOD, service.statValue(profile, TEMPO) / 100.0,
						AttributeModifier.Operation.MULTIPLY_BASE),
				// Speed: +1% movement per point, 2x at 100.
				new HeldModifier(Attributes.MOVEMENT_SPEED, SPEED_MOD, service.statValue(profile, SPEED) / 100.0,
						AttributeModifier.Operation.MULTIPLY_BASE));
	}

	public static boolean armorSlotsEmpty(ServerPlayer player) {
		return player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
				&& player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()
				&& player.getItemBySlot(EquipmentSlot.LEGS).isEmpty()
				&& player.getItemBySlot(EquipmentSlot.FEET).isEmpty();
	}

	/** The amount of one of Bloom's modifiers on this attribute right now (0 when it isn't there). */
	public static double current(AttributeInstance instance, ModifierId id) {
		AttributeModifier modifier = instance == null ? null : instance.getModifier(id.uuid());
		return modifier == null ? 0.0 : modifier.getAmount();
	}

	private static void apply(ServerPlayer player, Attribute attribute, ModifierId id, double amount, AttributeModifier.Operation operation) {
		AttributeInstance instance = player.getAttribute(attribute);

		if (instance == null) {
			return;
		}

		AttributeModifier existing = instance.getModifier(id.uuid());

		if (amount == 0.0) {
			if (existing != null) {
				instance.removeModifier(id.uuid());
			}

			return;
		}

		if (existing != null && Math.abs(existing.getAmount() - amount) < 1e-9 && existing.getOperation() == operation) {
			return;
		}

		if (existing != null) {
			instance.removeModifier(id.uuid());
		}

		instance.addPermanentModifier(new AttributeModifier(id.uuid(), id.name(), amount, operation));
	}
}
