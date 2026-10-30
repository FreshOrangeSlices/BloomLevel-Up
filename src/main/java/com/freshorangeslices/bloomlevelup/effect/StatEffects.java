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

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;
import com.freshorangeslices.bloomlevelup.config.BloomConfig;
import com.freshorangeslices.bloomlevelup.data.BloomServices;
import com.freshorangeslices.bloomlevelup.player.BloomAttachments;
import com.freshorangeslices.bloomlevelup.player.BloomProfile;
import com.freshorangeslices.bloomlevelup.progression.ProgressionService;
import com.freshorangeslices.bloomlevelup.skill.armor.ArmorIds;
import com.freshorangeslices.bloomlevelup.skill.armor.ArmorStyles;
import com.freshorangeslices.bloomlevelup.skill.combat.CombatIds;
import com.freshorangeslices.bloomlevelup.skill.combat.CombatSkill;
import com.freshorangeslices.bloomlevelup.skill.combat.Weapons.Family;
import com.freshorangeslices.bloomlevelup.skill.misc.MiscSkill;
import com.freshorangeslices.bloomlevelup.skill.utility.UtilitySkill;
import com.freshorangeslices.bloomlevelup.stats.StatFormulas;
import com.freshorangeslices.bloomlevelup.stats.StatFormulas.ArmorStyle;

/**
 * Turns stat points (and the simple always-on Majors) into vanilla attribute modifiers. Idempotent: it
 * recomputes every value from the profile and only touches modifiers whose value changed, so it can run
 * on every profile change and on a short timer without churn.
 *
 * <p>Modifiers are permanent (saved with the player) so max health is already correct when the player's
 * health is loaded on login; otherwise extra hearts would be clipped every relog.
 *
 * <p>1.20.1 attribute modifiers are keyed by UUID (26.x uses ids): each of Bloom's gets a fixed UUID made from
 * its id, so the same modifier is found again after a relog. 1.20.1 has no block-break-speed or fall-damage
 * attribute and no sweep attribute: Efficiency and Titan Hide's fall cut are Forge events here, Full Sweep a
 * mixin on the sweep itself.
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
	public static final ModifierId BRAWLER_MOD = ModifierId.of("major/brawler");
	public static final ModifierId GUARANTEED_CRITICAL_MOD = ModifierId.of("major/guaranteed_critical");
	public static final ModifierId TITAN_SPEED_MOD = ModifierId.of("major/titan_hide_speed");
	public static final ModifierId PROTEIN_MOD = ModifierId.of("major/protein");
	public static final ModifierId HEAVY_KNOCKBACK_MOD = ModifierId.of("major/fitting_armor_heavy");
	public static final ModifierId WISDOM_HASTE_MOD = ModifierId.of("stat/wisdom_spell_haste");
	/** Spell Power's spell haste (the RPG Series): faster casting and shorter spell cooldowns. Absent without it. */
	private static final net.minecraft.resources.ResourceLocation SPELL_HASTE = new net.minecraft.resources.ResourceLocation("spell_power", "haste");

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

	public static void init() {
		// Efficiency (and Shovel Mastery's boost): faster block breaking, on both sides so the client's own mining
		// keeps pace with the server.
		MinecraftForge.EVENT_BUS.addListener((PlayerEvent.BreakSpeed event) -> {
			int efficiency = UtilitySkill.efficiencyAnySide(event.getEntity(), event.getEntity().getMainHandItem());

			if (efficiency > 0) {
				event.setNewSpeed(event.getNewSpeed() * (1.0F + efficiency / 100.0F));
			}
		});
		// Titan Hide: -70% fall damage while wearing no armor.
		MinecraftForge.EVENT_BUS.addListener((LivingFallEvent event) -> {
			if (event.getEntity() instanceof ServerPlayer player && titanHide(player)) {
				event.setDamageMultiplier(event.getDamageMultiplier() * 0.3F);
			}
		});
	}

	/** Titan Hide's armor for this much Defense armor: its innate armor added, then doubled. */
	public static double titanHideArmor(double defenseArmor, BloomConfig config) {
		return (defenseArmor + config.titanHideInnateArmor) * 2.0;
	}

	public static boolean titanHide(ServerPlayer player) {
		return BloomServices.progression().isMajorActive(BloomAttachments.profile(player), TITAN_HIDE) && armorSlotsEmpty(player);
	}

	public static void refresh(ServerPlayer player) {
		BloomProfile profile = BloomAttachments.profile(player);

		// Misc: Ascension's flight, and Water to Wine's cut when it's switched off.
		MiscSkill.refresh(player);

		// Protein!: the meal count belongs to owning the Major; refunding it (or Bloom+) starts over.
		// Protein!'s meals are kept while either build owns it (switching builds isn't a refund).
		if (!profile.ownsInAnyBuild(ArmorIds.PROTEIN) && profile.counter(ArmorIds.PROTEIN_MEALS) != 0) {
			profile.setCounter(ArmorIds.PROTEIN_MEALS, 0);
		}

		for (HeldModifier modifier : profileModifiers(player)) {
			apply(player, modifier.attribute(), modifier.id(), modifier.amount(), modifier.operation());
		}

		// Everything that depends on the item in hand (HeldItemWatcher refreshes on every swap).
		for (HeldModifier modifier : heldModifiers(player, player.getMainHandItem())) {
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
		boolean titanHide = service.isMajorActive(profile, TITAN_HIDE) && armorSlotsEmpty(player);
		double proteinHealth = service.isMajorActive(profile, ArmorIds.PROTEIN)
				? StatFormulas.proteinBonusHealth(profile.counter(ArmorIds.PROTEIN_MEALS)) : 0.0;

		// Fitting Armor / Heavy: strong knockback resistance that tapers off if you already resist knockback.
		boolean heavy = service.isMajorActive(profile, ArmorIds.FITTING_ARMOR) && ArmorStyles.styleOf(player) == ArmorStyle.HEAVY;
		double heavyKnockback = 0.0;

		if (heavy) {
			AttributeInstance knockback = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);

			if (knockback != null) {
				double others = knockback.getValue() - current(knockback, HEAVY_KNOCKBACK_MOD);
				heavyKnockback = StatFormulas.heavyKnockbackResistance(others);
			}
		}

		double defenseArmor = StatFormulas.defenseArmor(service.statValue(profile, DEFENSE));

		if (titanHide) {
			// Its innate armor first (+2 by default, config), then the whole Defense value doubles.
			defenseArmor = titanHideArmor(defenseArmor, BloomServices.config());
		}

		List<HeldModifier> modifiers = new java.util.ArrayList<>(List.of(
				new HeldModifier(Attributes.MAX_HEALTH, PROTEIN_MOD, proteinHealth, AttributeModifier.Operation.ADDITION),
				new HeldModifier(Attributes.KNOCKBACK_RESISTANCE, HEAVY_KNOCKBACK_MOD, heavyKnockback, AttributeModifier.Operation.ADDITION),
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
						AttributeModifier.Operation.MULTIPLY_BASE),
				// Titan Hide: +50% movement. (Its fall cut is the fall event above; its 2x knockback drawback is in LivingEntityMixin.)
				new HeldModifier(Attributes.MOVEMENT_SPEED, TITAN_SPEED_MOD, titanHide ? 0.5 : 0.0, AttributeModifier.Operation.MULTIPLY_BASE)));

		// Wisdom: +0.5% spell haste per point when Spell Power is installed (+50% at 100).
		if (net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES.containsKey(SPELL_HASTE)) {
			modifiers.add(new HeldModifier(net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES.getValue(SPELL_HASTE), WISDOM_HASTE_MOD,
					StatFormulas.wisdomSpellHaste(service.statValue(profile, com.freshorangeslices.bloomlevelup.skill.misc.MiscIds.WISDOM)),
					AttributeModifier.Operation.ADDITION));
		}

		return modifiers;
	}

	/**
	 * The modifiers that depend on the item in the main hand, worked out for {@code held} (the stat sheet asks about
	 * items that aren't in hand).
	 */
	public static List<HeldModifier> heldModifiers(ServerPlayer player, ItemStack held) {
		BloomConfig config = BloomServices.config();
		return List.of(
				new HeldModifier(Attributes.ATTACK_DAMAGE, BRAWLER_MOD,
						CombatSkill.has(player, CombatIds.BRAWLER, Family.UNARMED, held) ? config.brawlerDamage : 0.0, AttributeModifier.Operation.ADDITION),
				new HeldModifier(Attributes.ATTACK_DAMAGE, GUARANTEED_CRITICAL_MOD,
						CombatSkill.has(player, CombatIds.GUARANTEED_CRITICAL, Family.AXE, held) ? -config.guaranteedCriticalPenalty : 0.0,
						AttributeModifier.Operation.ADDITION));
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
