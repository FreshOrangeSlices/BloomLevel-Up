package com.freshorangeslices.bloomlevelup.sheet;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import com.freshorangeslices.bloomlevelup.config.BloomConfig;
import com.freshorangeslices.bloomlevelup.data.BloomServices;
import com.freshorangeslices.bloomlevelup.effect.StatEffects;
import com.freshorangeslices.bloomlevelup.item.BloomComponents;
import com.freshorangeslices.bloomlevelup.item.FuelImprint;
import com.freshorangeslices.bloomlevelup.player.BloomAttachments;
import com.freshorangeslices.bloomlevelup.player.BloomProfile;
import com.freshorangeslices.bloomlevelup.progression.ProgressionService;
import com.freshorangeslices.bloomlevelup.skill.combat.CombatAbilities;
import com.freshorangeslices.bloomlevelup.skill.combat.CombatIds;
import com.freshorangeslices.bloomlevelup.skill.combat.CombatSkill;
import com.freshorangeslices.bloomlevelup.skill.combat.Weapons;
import com.freshorangeslices.bloomlevelup.skill.combat.Weapons.Family;
import com.freshorangeslices.bloomlevelup.skill.misc.MiscSkill;
import com.freshorangeslices.bloomlevelup.stats.StatFormulas;

/**
 * The Combat sheet: what a weapon (or a bare hand) hits for, with every source of damage itemised in the order the
 * game applies them. Vanilla's part comes from the game itself (attributes with the weapon swapped in, the
 * enchantments' own damage rules against stand-in mobs, the armor formula), and Bloom's part follows
 * {@link CombatSkill}'s hooks step for step, so the sheet and a real hit agree (a game test checks it).
 */
final class CombatSheet {
	/** How far the "falling" switch falls. */
	static final double FALL_BLOCKS = 10.0;
	/** The target picker: armor points and toughness of a full set. */
	private static final float[][] TARGET_ARMOR = {{0.0F, 0.0F}, {15.0F, 0.0F}, {20.0F, 8.0F}, {20.0F, 12.0F}};
	private static final String[] TARGET_NAMES = {"none", "iron", "diamond", "netherite"};
	/** The swords the picture compares to, weakest first (any a version lacks are skipped). */
	private static final String[] SWORDS = {"wooden_sword", "stone_sword", "copper_sword", "iron_sword", "diamond_sword", "netherite_sword"};

	private CombatSheet() {
	}

	/** Bloom's own part of a hit: everything added, then everything multiplied (CombatSkill.applyBonuses). */
	private record Bonus(double add, double multiplier) {
		double apply(double amount) {
			return (amount + add) * multiplier;
		}
	}

	/** The switches the player turned on (each only offered when the player owns that effect). */
	private record Switches(boolean charged, boolean chargedThrow, boolean fall, boolean stored, boolean combo, boolean firstStrike,
			boolean predator, boolean bloodlust, boolean marksman) {
	}

	static void build(ServerPlayer player, ItemStack weapon, Sheet sheet) {
		ServerLevel level = (ServerLevel) player.level();
		BloomConfig config = BloomServices.config();
		ProgressionService service = BloomServices.progression();
		BloomProfile profile = BloomAttachments.profile(player);
		Loadout loadout = new Loadout(player, weapon);
		Set<Family> families = Weapons.families(weapon);
		boolean melee = Weapons.isMeleeWeapon(weapon);
		boolean bow = families.contains(Family.BOW);
		boolean crossbow = families.contains(Family.CROSSBOW);
		boolean trident = families.contains(Family.TRIDENT);
		boolean mace = families.contains(Family.MACE);
		boolean throwable = CombatAbilities.throwable(weapon) && CombatSkill.active(player, CombatIds.CATCH);

		// ---- the switches, only for effects this player owns (and that work with this weapon)
		boolean charged = melee && CombatAbilities.canCharge(player, weapon) && sheet.toggle("charged", Sheet.num(config.chargeAttackMultiplier));
		boolean chargedThrow = throwable && CombatAbilities.masterOfArms(player) && CombatAbilities.canCharge(player, weapon)
				&& sheet.toggle("charged_throw", Sheet.num(config.chargeAttackMultiplier));
		boolean fall = melee && (mace || CombatSkill.active(player, CombatIds.DEATH_FROM_ABOVE)) && sheet.toggle("fall", Sheet.num(FALL_BLOCKS));
		boolean stored = CombatSkill.has(player, CombatIds.STORED_IMPACT, Family.MACE, weapon) && sheet.toggle("stored", Sheet.num(config.storedImpactCap));
		boolean combo = CombatSkill.has(player, CombatIds.COMBO, Family.UNARMED, weapon) && sheet.toggle("combo", Sheet.num(config.comboCap));
		boolean firstStrike = CombatSkill.active(player, CombatIds.FIRST_STRIKE) && sheet.toggle("first_strike", Sheet.num(config.firstStrikeBonus * 100.0));
		boolean predator = CombatSkill.active(player, CombatIds.PREDATOR) && sheet.toggle("predator", Sheet.num(config.predatorMaxStacks));
		boolean bloodlust = CombatSkill.active(player, CombatIds.BLOODLUST) && sheet.toggle("bloodlust", Sheet.num(config.bloodlustMaxStacks));
		boolean marksman = (bow || crossbow) && CombatSkill.has(player, CombatIds.MARKSMAN, Family.BOW, weapon)
				&& sheet.toggle("marksman", Sheet.num(config.marksmanFullDistance));
		Switches on = new Switches(charged, chargedThrow, fall, stored, combo, firstStrike, predator, bloodlust, marksman);

		// ---- the target
		int target = sheet.targets(List.of(targetName(0), targetName(1), targetName(2), targetName(3)));
		float armor = TARGET_ARMOR[target][0];
		float toughness = TARGET_ARMOR[target][1];
		double penetration = StatFormulas.penetration(service.statValue(profile, CombatIds.PENETRATION));
		int critPoints = service.statValue(profile, CombatIds.CRITICAL);
		double critMultiplier = StatFormulas.criticalMultiplier(critPoints);
		LivingEntity mob = Dummies.of(level, EntityTypes.PIG);
		DamageSource swing = player.damageSources().playerAttack(player);

		double gaugeDamage;

		if (bow || crossbow) {
			gaugeDamage = ranged(player, level, weapon, sheet, on, mob, bow, armor, toughness, penetration, config);
		} else {
			gaugeDamage = melee(player, level, loadout, weapon, sheet, on, mob, swing, families, armor, toughness, penetration, critPoints,
					critMultiplier, config, profile, service);
		}

		if (throwable || trident) {
			thrown(player, level, weapon, sheet, on, mob, trident && !throwable, armor, toughness, penetration, config);
		}

		onHit(player, weapon, sheet, families, config, profile, service);
		swordGauge(level, sheet, mob, swing, gaugeDamage);
	}

	// ------------------------------------------------------------------ melee

	private static double melee(ServerPlayer player, ServerLevel level, Loadout loadout, ItemStack weapon, Sheet sheet, Switches on, LivingEntity mob,
			DamageSource swing, Set<Family> families, float armor, float toughness, double penetration, int critPoints, double critMultiplier,
			BloomConfig config, BloomProfile profile, ProgressionService service) {
		boolean mace = families.contains(Family.MACE);
		float attack = (float) loadout.value(Attributes.ATTACK_DAMAGE);
		float enchant = EnchantmentHelper.modifyDamage(level, weapon, mob, swing, attack) - attack;
		// Player.attack: the mace's fall bonus is part of the base damage (so a crit multiplies it), enchantments come after.
		float smash = mace && on.fall()
				? (float) (StatFormulas.maceFallBonus(FALL_BLOCKS) + EnchantmentHelper.modifyFallBasedDamage(level, weapon, mob, swing, 0.0F) * FALL_BLOCKS)
				: 0.0F;
		double vanillaHit = attack + smash + enchant;
		double vanillaCrit = (attack + smash) * 1.5 + enchant;
		boolean guaranteed = CombatSkill.has(player, CombatIds.GUARANTEED_CRITICAL, Family.AXE, weapon);
		Bonus bonus = primaryBonus(player, weapon, on, config);
		double hit = bonus.apply(vanillaHit) * (guaranteed ? critMultiplier : 1.0);
		double crit = bonus.apply(vanillaCrit) * critMultiplier / 1.5;
		double speed = loadout.value(Attributes.ATTACK_SPEED);
		double hitAfter = afterArmor(level, mob, weapon, swing, hit, armor, toughness, penetration);
		double critAfter = afterArmor(level, mob, weapon, swing, crit, armor, toughness, penetration);

		// ---- summary
		sheet.summary(Sheet.plain(hitAfter), guaranteed ? "combat.hit_always_crit" : "combat.hit");

		if (!guaranteed) {
			sheet.summary(Sheet.plain(critAfter), "combat.crit");
		}

		sheet.summary(Sheet.plain(speed), "combat.speed");
		sheet.summary(Sheet.plain(hitAfter * speed), "combat.dps");

		// ---- breakdown: attack damage
		sheet.heading("combat.h.attack");
		double base = loadout.base(Attributes.ATTACK_DAMAGE);
		double weaponPart = weapon.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
				.compute(Attributes.ATTACK_DAMAGE, base, EquipmentSlot.MAINHAND) - base;
		double power = service.statValue(profile, CombatIds.POWER) * StatEffects.POWER_PER_POINT;
		double brawler = CombatSkill.has(player, CombatIds.BRAWLER, Family.UNARMED, weapon) ? config.brawlerDamage : 0.0;
		double penalty = guaranteed ? -config.guaranteedCriticalPenalty : 0.0;
		double other = attack - base - weaponPart - power - brawler - penalty;
		sheet.row(Sheet.plain(base), "combat.base");

		if (!weapon.isEmpty()) {
			sheet.row(Sheet.plus(weaponPart), "combat.weapon", weapon.getHoverName());
		}

		sheet.row(Sheet.plus(power), "combat.power", Sheet.num(service.statValue(profile, CombatIds.POWER)));

		if (brawler != 0.0) {
			sheet.row(Sheet.plus(brawler), "combat.brawler");
		}

		if (penalty != 0.0) {
			sheet.row(Sheet.plus(penalty), "combat.guaranteed_penalty");
		}

		if (Math.abs(other) > 0.005) {
			sheet.row(Sheet.plus(other), "combat.other");
			sheet.note("combat.other.note");
		}

		sheet.total(Sheet.plain(attack), "combat.attack_total");

		// ---- breakdown: the hit
		sheet.heading("combat.h.hit");
		sheet.row(Sheet.plain(attack), "combat.attack_damage");

		if (smash > 0.0F) {
			sheet.row(Sheet.plus(smash), "combat.mace_fall", Sheet.num(FALL_BLOCKS));
		}

		sheet.row(Sheet.plus(enchant), "combat.enchantments");
		conditionalEnchantments(level, weapon, swing, attack, enchant, sheet);
		addBonusRows(player, weapon, on, config, sheet, true);

		if (guaranteed) {
			sheet.row(Sheet.times(critMultiplier), "combat.guaranteed_crit");
		}

		sheet.total(Sheet.plain(hit), "combat.hit_total");
		sheet.row(Sheet.times(critMultiplier), "combat.crit_multiplier", Sheet.num(critPoints));
		sheet.note("combat.crit_multiplier.note");
		sheet.total(Sheet.plain(crit), "combat.crit_total");

		// ---- breakdown: armor
		armorRows(sheet, armor, toughness, penetration, service.statValue(profile, CombatIds.PENETRATION), hitAfter, critAfter);

		// ---- breakdown: speed and reach
		sheet.heading("combat.h.speed");
		double baseSpeed = loadout.base(Attributes.ATTACK_SPEED);
		double weaponSpeed = weapon.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
				.compute(Attributes.ATTACK_SPEED, baseSpeed, EquipmentSlot.MAINHAND);
		int tempo = service.statValue(profile, CombatIds.TEMPO);
		sheet.row(Sheet.plain(weaponSpeed), "combat.weapon_speed");
		sheet.row(Sheet.plusPct(tempo / 100.0), "combat.tempo", Sheet.num(tempo));

		if (Math.abs(speed - weaponSpeed * (1.0 + tempo / 100.0)) > 0.005) {
			sheet.note("combat.speed.other");
		}

		sheet.total(Sheet.plain(speed), "combat.speed_total");
		sheet.row(Sheet.seconds(20.0 / Math.max(0.01, speed)), "combat.cooldown");
		sheet.row(Sheet.plain(hitAfter * speed), "combat.dps_total");
		double reach = loadout.value(Attributes.ENTITY_INTERACTION_RANGE);

		if (weapon.has(DataComponents.ATTACK_RANGE)) {
			reach = Math.max(reach, player.getAttackRangeWith(weapon).maxReach());
		}

		sheet.row(Sheet.colored(Sheet.num(reach), Sheet.PLAIN), "combat.reach");
		sheet.summary(Sheet.plain(reach), "combat.reach");

		// ---- breakdown: hitting more than one
		areaRows(player, level, loadout, weapon, sheet, on, mob, swing, attack, enchant, vanillaHit, hit, armor, toughness, penetration, config);
		return bonus.apply(vanillaHit) * (guaranteed ? critMultiplier : 1.0);
	}

	/** Bloom's bonuses on a swing's own target, with the switches. */
	private static Bonus primaryBonus(ServerPlayer player, ItemStack weapon, Switches on, BloomConfig config) {
		double add = 0.0;
		double multiplier = 1.0;

		if (on.charged()) {
			multiplier *= config.chargeAttackMultiplier;
		}

		if (CombatSkill.has(player, CombatIds.STORED_IMPACT, Family.MACE, weapon)) {
			add += on.stored() ? config.storedImpactCap : CombatSkill.storedImpactNow(player);
		}

		if (on.fall() && CombatSkill.active(player, CombatIds.DEATH_FROM_ABOVE) && Weapons.isMeleeWeapon(weapon) && !Weapons.is(weapon, Family.MACE)) {
			add += StatFormulas.maceFallBonus(FALL_BLOCKS);
		}

		if (on.combo()) {
			add += config.comboCap;
		}

		return new Bonus(add, multiplier * shared(on, config, true));
	}

	/** First Strike, Predator (single-target hits only) and Bloodlust: every kind of weapon hit. */
	private static double shared(Switches on, BloomConfig config, boolean singleTarget) {
		double multiplier = 1.0;

		if (on.firstStrike()) {
			multiplier *= 1.0 + config.firstStrikeBonus;
		}

		if (on.predator() && singleTarget) {
			multiplier *= StatFormulas.stackMultiplier(config.predatorMaxStacks, config.predatorPerStack, config.predatorMaxStacks);
		}

		if (on.bloodlust()) {
			multiplier *= StatFormulas.stackMultiplier(config.bloodlustMaxStacks, config.bloodlustPerStack, config.bloodlustMaxStacks);
		}

		return multiplier;
	}

	/** The rows for Bloom's bonuses on a swing (or, {@code swing} false, on a projectile), in the order they apply. */
	private static void addBonusRows(ServerPlayer player, ItemStack weapon, Switches on, BloomConfig config, Sheet sheet, boolean swing) {
		if (swing) {
			if (CombatSkill.has(player, CombatIds.STORED_IMPACT, Family.MACE, weapon)) {
				double now = CombatSkill.storedImpactNow(player);
				sheet.row(Sheet.plus(on.stored() ? config.storedImpactCap : now), on.stored() ? "combat.stored_full" : "combat.stored_now");
			}

			if (on.fall() && CombatSkill.active(player, CombatIds.DEATH_FROM_ABOVE) && !Weapons.is(weapon, Family.MACE)) {
				sheet.row(Sheet.plus(StatFormulas.maceFallBonus(FALL_BLOCKS)), "combat.death_from_above", Sheet.num(FALL_BLOCKS));
			}

			if (on.combo()) {
				sheet.row(Sheet.plus(config.comboCap), "combat.combo");
			}

			if (on.charged()) {
				sheet.row(Sheet.times(config.chargeAttackMultiplier), "combat.charged");
			}
		}

		if (on.firstStrike()) {
			sheet.row(Sheet.times(1.0 + config.firstStrikeBonus), "combat.first_strike");
		}

		if (on.predator()) {
			sheet.row(Sheet.times(StatFormulas.stackMultiplier(config.predatorMaxStacks, config.predatorPerStack, config.predatorMaxStacks)),
					"combat.predator", Sheet.num(config.predatorMaxStacks));
		}

		if (on.bloodlust()) {
			sheet.row(Sheet.times(StatFormulas.stackMultiplier(config.bloodlustMaxStacks, config.bloodlustPerStack, config.bloodlustMaxStacks)),
					"combat.bloodlust", Sheet.num(config.bloodlustMaxStacks));
		}
	}

	/** Smite, Bane of Arthropods, Impaling and the like: how much more they add against the mobs they're for. */
	private static void conditionalEnchantments(ServerLevel level, ItemStack weapon, DamageSource source, float attack, float general, Sheet sheet) {
		conditional(level, weapon, source, attack, general, sheet, EntityTypes.ZOMBIE, "combat.vs_undead");
		conditional(level, weapon, source, attack, general, sheet, EntityTypes.SPIDER, "combat.vs_arthropods");
		conditional(level, weapon, source, attack, general, sheet, EntityTypes.GUARDIAN, "combat.vs_water");
	}

	private static void conditional(ServerLevel level, ItemStack weapon, DamageSource source, float attack, float general, Sheet sheet,
			EntityType<? extends LivingEntity> type, String key) {
		float against = EnchantmentHelper.modifyDamage(level, weapon, Dummies.of(level, type), source, attack) - attack;

		if (Math.abs(against - general) > 0.005F) {
			sheet.note(key, Sheet.num(against));
		}
	}

	private static void armorRows(Sheet sheet, float armor, float toughness, double penetration, int penetrationPoints, double hitAfter,
			double critAfter) {
		sheet.heading("combat.h.armor");
		sheet.row(Sheet.word("target." + TARGET_NAMES[sheet.target]), "combat.target");

		if (armor > 0.0F) {
			sheet.row(Sheet.plain(armor), "combat.target_armor");
			sheet.row(Sheet.plain(toughness), "combat.target_toughness");
			sheet.row(Sheet.pct(penetration), "combat.penetration", Sheet.num(penetrationPoints));
			sheet.row(Sheet.plain(armor * (1.0 - penetration)), "combat.armor_left");
		}

		sheet.total(Sheet.plain(hitAfter), "combat.hit_after");
		sheet.total(Sheet.plain(critAfter), "combat.crit_after");
	}

	/** Sweeps, Aura Sword, Lightning Shock, Overkill. */
	private static void areaRows(ServerPlayer player, ServerLevel level, Loadout loadout, ItemStack weapon, Sheet sheet, Switches on, LivingEntity mob,
			DamageSource swing, float attack, float enchant, double vanillaHit, double hit, float armor, float toughness, double penetration,
			BloomConfig config) {
		boolean swordSweep = weapon.is(ItemTags.SWORDS);
		boolean batterUp = !swordSweep && Weapons.isMeleeWeapon(weapon) && CombatSkill.active(player, CombatIds.BATTER_UP);
		boolean aura = CombatSkill.active(player, CombatIds.AURA_SWORD);
		boolean shock = CombatSkill.has(player, CombatIds.LIGHTNING_SHOCK, Family.TRIDENT, weapon);
		boolean overkill = CombatSkill.active(player, CombatIds.OVERKILL);

		if (!swordSweep && !batterUp && !aura && !shock && !overkill) {
			return;
		}

		sheet.heading("combat.h.area");
		// Sweeps and Aura waves get First Strike and Bloodlust, never Predator.
		double spread = shared(on, config, false);

		if (swordSweep || batterUp) {
			double ratio = loadout.value(Attributes.SWEEPING_DAMAGE_RATIO);
			// A sword's sweep: 1 + ratio x the swing's base damage, then its enchantments again (vanilla). Batter Up!:
			// 1 + ratio x the whole hit (CombatSkill.onHitEffects).
			double sweep = swordSweep ? EnchantmentHelper.modifyDamage(level, weapon, mob, swing, (float) (1.0 + ratio * attack)) : 1.0 + ratio * (attack + enchant);
			double sweepHit = sweep * spread;
			double sweepAfter = afterArmor(level, mob, weapon, swing, sweepHit, armor, toughness, penetration);
			sheet.row(Sheet.pct(ratio), "combat.sweep_ratio");
			sheet.total(Sheet.plain(sweepAfter), batterUp ? "combat.sweep_batter" : "combat.sweep");
			sheet.note("combat.sweep.note");
			sheet.summary(Sheet.plain(sweepAfter), "combat.sweep");
		}

		if (aura) {
			double wave = attack * spread;
			sheet.row(Sheet.plain(afterArmor(level, mob, weapon, swing, wave, armor, toughness, penetration)), "combat.aura", Sheet.num(config.auraSwordHits));
		}

		if (shock) {
			sheet.row(Sheet.plain(hit * config.lightningShockRatio), "combat.shock", Sheet.num(config.lightningShockSparks),
					Sheet.num(config.lightningShockJumps));
		}

		if (overkill) {
			sheet.row(Sheet.pct(config.overkillRatio), "combat.overkill", Sheet.num(config.overkillMaxTargets), Sheet.num(config.overkillRadius));
		}
	}

	// ------------------------------------------------------------------ thrown weapons and tridents

	private static void thrown(ServerPlayer player, ServerLevel level, ItemStack weapon, Sheet sheet, Switches on, LivingEntity mob, boolean tridentOnly,
			float armor, float toughness, double penetration, BloomConfig config) {
		DamageSource source = player.damageSources().trident(player, player);
		float vanilla = EnchantmentHelper.modifyDamage(level, weapon, mob, source, 8.0F);
		double damage;

		if (tridentOnly) {
			damage = vanilla;
		} else {
			// CombatAbilities.thrownDamage: the weapon's own damage on your base (no Power or Strength), plus its enchantments.
			double base = weapon.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
					.compute(Attributes.ATTACK_DAMAGE, player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE), EquipmentSlot.MAINHAND);
			damage = (base + Math.max(0.0, vanilla - 8.0)) * (on.chargedThrow() ? config.chargeAttackMultiplier : 1.0);
		}

		double total = damage * shared(on, config, true);
		double after = afterArmor(level, mob, weapon, source, total, armor, toughness, penetration);
		sheet.heading("combat.h.thrown");
		sheet.row(Sheet.plain(damage), tridentOnly ? "combat.trident_throw" : on.chargedThrow() ? "combat.charged_throw" : "combat.catch_throw");

		if (!tridentOnly) {
			sheet.note("combat.catch_throw.note");
		}

		addBonusRows(player, weapon, new Switches(false, false, false, false, false, on.firstStrike(), on.predator(), on.bloodlust(), false), config,
				sheet, false);
		sheet.total(Sheet.plain(after), "combat.thrown_after");
		sheet.summary(Sheet.plain(after), "combat.thrown");
	}

	// ------------------------------------------------------------------ bows and crossbows

	private static double ranged(ServerPlayer player, ServerLevel level, ItemStack weapon, Sheet sheet, Switches on, LivingEntity mob, boolean bow,
			float armor, float toughness, double penetration, BloomConfig config) {
		DamageSource source = player.damageSources().arrow(null, player);
		// AbstractArrow.onHitEntity: the weapon's enchantments (Power) on the arrow's base 2, times its speed, rounded up.
		float base = EnchantmentHelper.modifyDamage(level, weapon, mob, source, 2.0F);
		double speed = bow ? 3.0 : 3.15;
		int arrow = Mth.ceil(Mth.clamp(speed * base, 0.0, Integer.MAX_VALUE));
		boolean copiedCrit = CombatSkill.copied(player).map(CombatIds.GUARANTEED_CRITICAL::equals).orElse(false);
		boolean crits = bow || copiedCrit;
		int critMax = crits ? arrow + arrow / 2 + 1 : arrow;
		double add = CombatSkill.has(player, CombatIds.REINFORCED_SHOT, Family.CROSSBOW, weapon) ? config.reinforcedShotDamage : 0.0;
		double multiplier = shared(on, config, true);

		if (on.marksman()) {
			multiplier *= 1.0 + config.marksmanMaxBonus;
		}

		double low = (arrow + add) * multiplier;
		double high = (critMax + add) * multiplier;
		double lowAfter = afterArmor(level, mob, weapon, source, low, armor, toughness, penetration);
		double highAfter = afterArmor(level, mob, weapon, source, high, armor, toughness, penetration);
		Component range = Sheet.plain(crits ? Sheet.num(lowAfter) + "-" + Sheet.num(highAfter) : Sheet.num(lowAfter));
		sheet.summary(range, bow ? "combat.arrow_bow" : "combat.arrow_crossbow");

		int count = EnchantmentHelper.processProjectileCount(level, weapon, player, 1);
		int pierce = EnchantmentHelper.getPiercingCount(level, weapon, new ItemStack(Items.ARROW));

		if (CombatSkill.has(player, CombatIds.PIERCING_SHOT, Family.BOW, weapon)) {
			pierce = Math.max(pierce, config.piercingShotLevel);
		}

		if (count > 1) {
			sheet.summary(Sheet.plain(count), "combat.arrows_per_shot");
		}

		if (pierce > 0) {
			sheet.summary(Sheet.plain(pierce), "combat.pierces");
		}

		sheet.summary(Sheet.seconds(bow ? 20 : CrossbowItem.getChargeDuration(weapon, player)), bow ? "combat.draw_time" : "combat.load_time");

		sheet.heading("combat.h.arrow");
		sheet.row(Sheet.plain(2.0), "combat.arrow_base");
		sheet.row(Sheet.plus(base - 2.0), "combat.enchantments");
		sheet.row(Sheet.times(speed), bow ? "combat.arrow_speed_bow" : "combat.arrow_speed_crossbow");
		sheet.total(Sheet.plain(arrow), "combat.arrow_total");

		if (crits) {
			sheet.row(Sheet.plus(critMax - arrow), "combat.arrow_crit");
			sheet.note(bow ? "combat.arrow_crit.note" : "combat.arrow_crit.copycat");
		}

		if (add > 0.0) {
			sheet.row(Sheet.plus(add), "combat.reinforced");
		}

		if (on.marksman()) {
			sheet.row(Sheet.times(1.0 + config.marksmanMaxBonus), "combat.marksman", Sheet.num(config.marksmanFullDistance));
		} else if (CombatSkill.has(player, CombatIds.MARKSMAN, Family.BOW, weapon)) {
			sheet.note("combat.marksman.off", Sheet.num(config.marksmanStartDistance));
		}

		addBonusRows(player, weapon, on, config, sheet, false);
		sheet.total(Sheet.plain(crits ? (Sheet.num(low) + "-" + Sheet.num(high)) : Sheet.num(low)), "combat.arrow_hit");
		armorRows(sheet, armor, toughness, penetration, BloomServices.progression().statValue(BloomAttachments.profile(player), CombatIds.PENETRATION),
				lowAfter, highAfter);

		sheet.heading("combat.h.shots");
		sheet.row(Sheet.plain(count), "combat.arrows_per_shot");
		sheet.row(Sheet.plain(pierce), "combat.pierces");

		if (CombatSkill.has(player, CombatIds.STRAIGHT_SHOT, Family.BOW, weapon)) {
			sheet.row(Sheet.seconds(config.straightShotMaxTicks), "combat.straight_shot");
		}

		if (CombatSkill.has(player, CombatIds.ASSAULT_RIFLE, Family.CROSSBOW, weapon)) {
			sheet.row(Sheet.plain(config.assaultRifleMagazine), "combat.magazine");
		}

		if (!bow && CombatSkill.active(player, CombatIds.CROSSBOW_INFINITY)) {
			sheet.row(Sheet.word("value.free"), "combat.free_arrows");
		}

		if (MiscSkill.rainbowFreeAmmo(weapon, new ItemStack(Items.FIREWORK_ROCKET), player)) {
			sheet.row(Sheet.word("value.free"), "combat.free_ammo");
		}

		return low;
	}

	// ------------------------------------------------------------------ on hit

	private static void onHit(ServerPlayer player, ItemStack weapon, Sheet sheet, Set<Family> families, BloomConfig config, BloomProfile profile,
			ProgressionService service) {
		sheet.heading("combat.h.on_hit");
		int lootingPoints = service.statValue(profile, CombatIds.LOOTING);
		Holder<Enchantment> looting = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOOTING);
		int lootingEnchant = EnchantmentHelper.getItemEnchantmentLevel(looting, weapon);
		sheet.row(Sheet.plus(StatFormulas.virtualLooting(lootingPoints) + lootingEnchant), "combat.looting", Sheet.num(lootingPoints));

		if (CombatSkill.has(player, CombatIds.EXECUTE, Family.SWORD, weapon)) {
			sheet.row(Sheet.pct(config.executeThreshold), "combat.execute", Sheet.num(config.executeBossThreshold * 100.0));
		}

		if (CombatSkill.has(player, CombatIds.ARMOR_BREAK, Family.AXE, weapon)) {
			sheet.row(Sheet.pct(config.armorBreakRatio), "combat.armor_break");
		}

		if (CombatSkill.has(player, CombatIds.IMPALE, Family.SPEAR, weapon)) {
			sheet.row(Sheet.pct(config.impaleChance), "combat.impale", Sheet.num(config.bleedTicks / 20.0));
		}

		if (CombatSkill.has(player, CombatIds.DISARM, Family.UNARMED, weapon)) {
			sheet.row(Sheet.pct(config.disarmChance), "combat.disarm");
		}

		FuelImprint fuel = weapon.isEmpty() ? null : weapon.get(BloomComponents.FUEL);

		if (fuel != null) {
			sheet.row(Sheet.seconds(config.fuelEffectTicks), "combat.fuel", MiscSkill.effectName(fuel.effect(), fuel.amplifier()));
		}
	}

	// ------------------------------------------------------------------ helpers

	/** CombatRules.getDamageAfterAbsorb with Penetration (CombatSkill.penetrate) and the weapon's Breach. */
	static double afterArmor(ServerLevel level, LivingEntity target, ItemStack weapon, DamageSource source, double damage, float armor, float toughness,
			double penetration) {
		if (!(armor > 0.0F) || !(damage > 0.0)) {
			return damage;
		}

		float effective = (float) (armor * (1.0 - penetration));
		float amount = (float) damage;
		float divider = 2.0F + toughness / 4.0F;
		float clamped = Mth.clamp(effective - amount / divider, effective * 0.2F, 20.0F);
		float ratio = Mth.clamp(EnchantmentHelper.modifyArmorEffectiveness(level, weapon, target, source, clamped / 25.0F), 0.0F, 1.0F);
		return amount * (1.0F - ratio);
	}

	private static Component targetName(int index) {
		return Component.translatable("bloom.sheet.target." + TARGET_NAMES[index]);
	}

	/**
	 * The picture: the vanilla sword (and Sharpness level) whose plain hit is closest to {@code damage}; past the
	 * best of them, a Netherite Sword with Sharpness V and how many times stronger the player hits.
	 */
	private static void swordGauge(ServerLevel level, Sheet sheet, LivingEntity mob, DamageSource source, double damage) {
		Holder<Enchantment> sharpness = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS);
		ItemStack best = ItemStack.EMPTY;
		double bestDamage = 0.0;
		int bestLevel = 0;
		ItemStack top = ItemStack.EMPTY;
		double topDamage = 0.0;
		int topLevel = 0;

		for (String id : SWORDS) {
			Optional<Item> item = BuiltInRegistries.ITEM.getOptional(Identifier.withDefaultNamespace(id));

			if (item.isEmpty() || item.get() == Items.AIR) {
				continue;
			}

			for (int enchantLevel = 0; enchantLevel <= 5; enchantLevel++) {
				ItemStack sword = new ItemStack(item.get());

				if (enchantLevel > 0) {
					sword.enchant(sharpness, enchantLevel);
				}

				float base = (float) sword.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
						.compute(Attributes.ATTACK_DAMAGE, 1.0, EquipmentSlot.MAINHAND);
				double hit = EnchantmentHelper.modifyDamage(level, sword, mob, source, base);
				double gap = Math.abs(hit - damage);
				double bestGap = Math.abs(bestDamage - damage);

				// Closest wins; on a tie, the one with less Sharpness (a plain iron sword over a stone one with Sharpness I).
				if (best.isEmpty() || gap < bestGap - 1.0E-4 || (Math.abs(gap - bestGap) <= 1.0E-4 && enchantLevel < bestLevel)) {
					best = sword;
					bestDamage = hit;
					bestLevel = enchantLevel;
				}

				if (hit > topDamage) {
					top = sword;
					topDamage = hit;
					topLevel = enchantLevel;
				}
			}
		}

		if (best.isEmpty()) {
			return;
		}

		boolean past = damage > topDamage + 1.0E-4;
		ItemStack shown = past ? top : best;
		int shownLevel = past ? topLevel : bestLevel;
		double shownDamage = past ? topDamage : bestDamage;
		Component name = shownLevel > 0
				? Component.translatable("bloom.sheet.gauge.with", shown.getHoverName(), sharpness.value().description(),
						Component.translatable("enchantment.level." + shownLevel))
				: shown.getHoverName();
		sheet.gauge = shown;
		sheet.gaugeCaption = Component.translatable(past ? "bloom.sheet.gauge.sword_past" : "bloom.sheet.gauge.sword", name, Sheet.num(shownDamage));
		sheet.gaugeBadge = past ? Sheet.colored("×" + Sheet.num(damage / topDamage), Sheet.GOLD) : Component.empty();
	}
}
