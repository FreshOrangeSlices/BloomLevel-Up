package com.freshorangeslices.bloomlevelup.sheet;

import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.SweepingEdgeEnchantment;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.ToolActions;

import com.freshorangeslices.bloomlevelup.compat.BetterCombat;
import com.freshorangeslices.bloomlevelup.config.BloomConfig;
import com.freshorangeslices.bloomlevelup.data.BloomServices;
import com.freshorangeslices.bloomlevelup.effect.StatEffects;
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
 * game applies them. Vanilla 1.20.1's part comes from the game itself (attributes with the weapon swapped in, the
 * enchantments' own damage bonus by mob type, the armor formula), and Bloom's part follows {@link CombatSkill}'s
 * hooks step for step, so the sheet and a real hit agree (a game test checks it).
 *
 * <p>Forge 1.20.1 build: no maces or spears, so no smash, Stored Impact or reach weapons; the sword sweep is
 * vanilla 1.20.1's (1 + Sweeping Edge's ratio x the whole hit). With Better Combat, a weapon it handles shows its
 * combo and its 1.20.1 crowd rule (Sweeping Edge gives some of the penalty back) in place of the sweep.
 */
final class CombatSheet {
	/** How far the "falling" switch falls. */
	static final double FALL_BLOCKS = 10.0;
	/** The target picker: armor points and toughness of a full set. */
	private static final float[][] TARGET_ARMOR = {{0.0F, 0.0F}, {15.0F, 0.0F}, {20.0F, 8.0F}, {20.0F, 12.0F}};
	private static final String[] TARGET_NAMES = {"none", "iron", "diamond", "netherite"};
	/** The swords the picture compares to, weakest first. */
	private static final ItemStack[] SWORDS = {new ItemStack(Items.WOODEN_SWORD), new ItemStack(Items.STONE_SWORD), new ItemStack(Items.IRON_SWORD),
			new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.NETHERITE_SWORD)};
	/** Vanilla 1.20.1's thrown trident hit, before its enchantments (ThrownTrident.onHitEntity). */
	private static final float TRIDENT_DAMAGE = 8.0F;

	private CombatSheet() {
	}

	/** Bloom's own part of a hit: everything added, then everything multiplied (CombatSkill.applyBonuses). */
	private record Bonus(double add, double multiplier) {
		double apply(double amount) {
			return (amount + add) * multiplier;
		}
	}

	/**
	 * What Bloom's own effects come in with, from the switches: Live is the player's real state right now (an armed
	 * charge, the fall in progress, the stacks Combo, Predator and Bloodlust have), Max the most, Off none.
	 */
	private record Switches(boolean charged, boolean chargedThrow, double fall, int combo, boolean firstStrike, int predator, int bloodlust,
			boolean marksman) {
	}

	/** Mobs some enchantments are for, each with its own colour on the sheet: undead (Smite), arthropods (Bane), water mobs (Impaling). */
	private enum Kind {
		UNDEAD("undead", 0x55FF55, MobType.UNDEAD),
		ARTHROPOD("arthropod", 0xFF55FF, MobType.ARTHROPOD),
		WATER("water", 0x55FFFF, MobType.WATER);

		final String key;
		final int color;
		final MobType type;

		Kind(String key, int color, MobType type) {
			this.key = key;
			this.color = color;
			this.type = type;
		}
	}

	static void build(ServerPlayer player, ItemStack weapon, Sheet sheet) {
		BloomConfig config = BloomServices.config();
		ProgressionService service = BloomServices.progression();
		BloomProfile profile = BloomAttachments.profile(player);
		Loadout loadout = new Loadout(player, weapon);
		Set<Family> families = Weapons.families(weapon);
		boolean melee = Weapons.isMeleeWeapon(weapon);
		boolean bow = families.contains(Family.BOW);
		boolean crossbow = families.contains(Family.CROSSBOW);
		boolean trident = families.contains(Family.TRIDENT);
		boolean throwable = CombatAbilities.throwable(weapon) && CombatSkill.active(player, CombatIds.CATCH);
		Switches on = switches(player, weapon, sheet, config, melee, bow || crossbow, throwable);

		// ---- the target
		int target = sheet.targets(List.of(targetName(0), targetName(1), targetName(2), targetName(3)));
		float armor = TARGET_ARMOR[target][0];
		float toughness = TARGET_ARMOR[target][1];
		double penetration = StatFormulas.penetration(service.statValue(profile, CombatIds.PENETRATION));
		int critPoints = service.statValue(profile, CombatIds.CRITICAL);
		double critMultiplier = StatFormulas.criticalMultiplier(critPoints);

		double gaugeDamage;

		if (bow || crossbow) {
			gaugeDamage = ranged(player, weapon, sheet, on, bow, armor, toughness, penetration, config);
		} else {
			gaugeDamage = melee(player, loadout, weapon, sheet, on, armor, toughness, penetration, critPoints, critMultiplier, config, profile, service);
		}

		if (throwable || trident) {
			thrown(player, weapon, sheet, on, trident && !throwable, armor, toughness, penetration, config);
		}

		onHit(player, weapon, sheet, config, profile, service);
		swordGauge(sheet, gaugeDamage);

		// What the coloured "+x" mean, once, under everything in the summary.
		for (Kind kind : Kind.values()) {
			if (sheet.summaryKeys.contains(kind.key)) {
				sheet.summaryNoteLabel(Component.translatable("bloom.sheet.combat.extra." + kind.key).withStyle(style -> style.withColor(kind.color)));
			}
		}
	}

	/** The switches, only for effects this player owns (and that work with this weapon). */
	private static Switches switches(ServerPlayer player, ItemStack weapon, Sheet sheet, BloomConfig config, boolean melee, boolean ranged,
			boolean throwable) {
		boolean charged = false;

		if (melee && CombatAbilities.canCharge(player, weapon)) {
			boolean armed = CombatAbilities.chargeArmed(player);
			charged = pick(sheet.toggle("charged", Sheet.word(armed ? "live.armed" : "live.not_armed"), Sheet.num(config.chargeAttackMultiplier)), armed);
		}

		boolean chargedThrow = false;

		if (throwable && CombatAbilities.masterOfArms(player) && CombatAbilities.canCharge(player, weapon)) {
			chargedThrow = pick(sheet.toggle("charged_throw", Sheet.word("live.not_now"), Sheet.num(config.chargeAttackMultiplier)), false);
		}

		double fall = 0.0;

		if (melee && CombatSkill.active(player, CombatIds.DEATH_FROM_ABOVE)) {
			// Death From Above counts any drop (the mace's smash rule, which needs more than 1.5 blocks).
			double now = player.fallDistance > 1.5 ? player.fallDistance : 0.0;
			Sheet.Mode mode = sheet.toggle("fall", now > 0.0 ? Sheet.word("live.blocks", Sheet.num(now)) : Sheet.word("live.not_falling"),
					Sheet.num(FALL_BLOCKS));
			fall = mode == Sheet.Mode.LIVE ? now : mode == Sheet.Mode.MAX ? FALL_BLOCKS : 0.0;
		}

		int combo = 0;

		if (CombatSkill.has(player, CombatIds.COMBO, Family.UNARMED, weapon)) {
			int now = CombatSkill.comboStacksNow(player);
			int most = (int) Math.ceil(config.comboCap / Math.max(1.0E-6, config.comboPerHit));
			Sheet.Mode mode = sheet.toggle("combo", Sheet.word("live.stacks", Sheet.num(now)), Sheet.num(config.comboCap));
			combo = mode == Sheet.Mode.LIVE ? now : mode == Sheet.Mode.MAX ? most : 0;
		}

		boolean firstStrike = CombatSkill.active(player, CombatIds.FIRST_STRIKE)
				&& pick(sheet.toggle("first_strike", Sheet.word("live.not_now"), Sheet.num(config.firstStrikeBonus * 100.0)), false);
		int predator = 0;

		if (CombatSkill.active(player, CombatIds.PREDATOR)) {
			int now = CombatSkill.predatorStacksNow(player);
			Sheet.Mode mode = sheet.toggle("predator", Sheet.word("live.stacks", Sheet.num(now)), Sheet.num(config.predatorMaxStacks));
			predator = mode == Sheet.Mode.LIVE ? now : mode == Sheet.Mode.MAX ? config.predatorMaxStacks : 0;
		}

		int bloodlust = 0;

		if (CombatSkill.active(player, CombatIds.BLOODLUST)) {
			int now = CombatSkill.bloodlustStacksNow(player);
			Sheet.Mode mode = sheet.toggle("bloodlust", Sheet.word("live.stacks", Sheet.num(now)), Sheet.num(config.bloodlustMaxStacks));
			bloodlust = mode == Sheet.Mode.LIVE ? now : mode == Sheet.Mode.MAX ? config.bloodlustMaxStacks : 0;
		}

		boolean marksman = ranged && CombatSkill.has(player, CombatIds.MARKSMAN, Family.BOW, weapon)
				&& pick(sheet.toggle("marksman", Sheet.word("live.not_now"), Sheet.num(config.marksmanFullDistance)), false);
		return new Switches(charged, chargedThrow, fall, combo, firstStrike, predator, bloodlust, marksman);
	}

	/** An on/off effect: Live is what the player has right now. */
	private static boolean pick(Sheet.Mode mode, boolean live) {
		return mode == Sheet.Mode.LIVE ? live : mode == Sheet.Mode.MAX;
	}

	// ------------------------------------------------------------------ melee

	private static double melee(ServerPlayer player, Loadout loadout, ItemStack weapon, Sheet sheet, Switches on, float armor, float toughness,
			double penetration, int critPoints, double critMultiplier, BloomConfig config, BloomProfile profile, ProgressionService service) {
		float attack = (float) loadout.value(Attributes.ATTACK_DAMAGE);
		// Player.attack (1.20.1): the enchantments' bonus for the target's mob type is added after the crit's 1.5x.
		float enchant = EnchantmentHelper.getDamageBonus(weapon, MobType.UNDEFINED);
		double[] extra = extraEnchantment(weapon, enchant);
		double vanillaHit = attack + enchant;
		double vanillaCrit = attack * 1.5 + enchant;
		boolean guaranteed = CombatSkill.has(player, CombatIds.GUARANTEED_CRITICAL, Family.AXE, weapon);
		Bonus bonus = primaryBonus(player, weapon, on, config);
		double hitMultiplier = guaranteed ? critMultiplier : 1.0;
		double hit = bonus.apply(vanillaHit) * hitMultiplier;
		double crit = bonus.apply(vanillaCrit) * critMultiplier / 1.5;
		double speed = loadout.value(Attributes.ATTACK_SPEED);
		double hitAfter = afterArmor(hit, armor, toughness, penetration);
		double critAfter = afterArmor(crit, armor, toughness, penetration);
		double[] hitExtra = new double[Kind.values().length];
		double[] critExtra = new double[Kind.values().length];
		double[] dpsExtra = new double[Kind.values().length];

		for (Kind kind : Kind.values()) {
			double more = extra[kind.ordinal()];

			if (more > 0.0) {
				hitExtra[kind.ordinal()] = afterArmor(bonus.apply(vanillaHit + more) * hitMultiplier, armor, toughness, penetration) - hitAfter;
				critExtra[kind.ordinal()] = afterArmor(bonus.apply(vanillaCrit + more) * critMultiplier / 1.5, armor, toughness, penetration) - critAfter;
				dpsExtra[kind.ordinal()] = hitExtra[kind.ordinal()] * speed;
			}
		}

		// ---- summary
		sheet.summary(withExtras(hitAfter, hitExtra), guaranteed ? "combat.hit_always_crit" : "combat.hit");

		if (!guaranteed) {
			sheet.summary(withExtras(critAfter, critExtra), "combat.crit");
		}

		sheet.summary(Sheet.plain(speed), "combat.speed");
		sheet.summary(withExtras(hitAfter * speed, dpsExtra), "combat.dps");

		// ---- breakdown: attack damage
		sheet.heading("combat.h.attack");
		double base = loadout.base(Attributes.ATTACK_DAMAGE);
		double weaponPart = Loadout.withItem(weapon, Attributes.ATTACK_DAMAGE, base) - base;
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
		enchantmentRows(weapon, sheet);
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
		double weaponSpeed = Loadout.withItem(weapon, Attributes.ATTACK_SPEED, baseSpeed);
		int tempo = service.statValue(profile, CombatIds.TEMPO);
		sheet.row(Sheet.plain(weaponSpeed), "combat.weapon_speed");
		sheet.row(Sheet.plusPct(tempo / 100.0), "combat.tempo", Sheet.num(tempo));

		if (Math.abs(speed - weaponSpeed * (1.0 + tempo / 100.0)) > 0.005) {
			sheet.note("combat.speed.other");
		}

		sheet.total(Sheet.plain(speed), "combat.speed_total");
		sheet.row(Sheet.seconds(20.0 / Math.max(0.01, speed)), "combat.cooldown");
		sheet.row(Sheet.plain(hitAfter * speed), "combat.dps_total");
		double reach = loadout.value(ForgeMod.ENTITY_REACH.get());
		sheet.row(Sheet.colored(Sheet.num(reach), Sheet.PLAIN), "combat.reach");
		sheet.summary(Sheet.plain(reach), "combat.reach");

		// ---- breakdown: hitting more than one
		areaRows(player, weapon, sheet, on, attack, enchant, extra, hit, armor, toughness, penetration, config);
		extrasKey(sheet, extra);
		return bonus.apply(vanillaHit) * hitMultiplier;
	}

	/** Bloom's bonuses on a swing's own target, with the switches. */
	private static Bonus primaryBonus(ServerPlayer player, ItemStack weapon, Switches on, BloomConfig config) {
		double add = 0.0;
		double multiplier = 1.0;

		if (on.charged()) {
			multiplier *= config.chargeAttackMultiplier;
		}

		if (on.fall() > 0.0 && CombatSkill.active(player, CombatIds.DEATH_FROM_ABOVE) && Weapons.isMeleeWeapon(weapon)) {
			add += StatFormulas.maceFallBonus(on.fall());
		}

		add += StatFormulas.comboBonus(on.combo(), config.comboPerHit, config.comboCap);
		return new Bonus(add, multiplier * shared(on, config, true));
	}

	/** First Strike, Predator (single-target hits only) and Bloodlust: every kind of weapon hit. */
	private static double shared(Switches on, BloomConfig config, boolean singleTarget) {
		double multiplier = 1.0;

		if (on.firstStrike()) {
			multiplier *= 1.0 + config.firstStrikeBonus;
		}

		if (singleTarget) {
			multiplier *= StatFormulas.stackMultiplier(on.predator(), config.predatorPerStack, config.predatorMaxStacks);
		}

		return multiplier * StatFormulas.stackMultiplier(on.bloodlust(), config.bloodlustPerStack, config.bloodlustMaxStacks);
	}

	/** The rows for Bloom's bonuses on a swing (or, {@code swing} false, on a projectile), in the order they apply. */
	private static void addBonusRows(ServerPlayer player, ItemStack weapon, Switches on, BloomConfig config, Sheet sheet, boolean swing) {
		if (swing) {
			if (on.fall() > 0.0 && CombatSkill.active(player, CombatIds.DEATH_FROM_ABOVE)) {
				sheet.row(Sheet.plus(StatFormulas.maceFallBonus(on.fall())), "combat.death_from_above", Sheet.num(on.fall()));
			}

			if (on.combo() > 0) {
				sheet.row(Sheet.plus(StatFormulas.comboBonus(on.combo(), config.comboPerHit, config.comboCap)), "combat.combo", Sheet.num(on.combo()));
			}

			if (on.charged()) {
				sheet.row(Sheet.times(config.chargeAttackMultiplier), "combat.charged");
			}
		}

		if (on.firstStrike()) {
			sheet.row(Sheet.times(1.0 + config.firstStrikeBonus), "combat.first_strike");
		}

		if (on.predator() > 0) {
			sheet.row(Sheet.times(StatFormulas.stackMultiplier(on.predator(), config.predatorPerStack, config.predatorMaxStacks)), "combat.predator",
					Sheet.num(on.predator()));
		}

		if (on.bloodlust() > 0) {
			sheet.row(Sheet.times(StatFormulas.stackMultiplier(on.bloodlust(), config.bloodlustPerStack, config.bloodlustMaxStacks)), "combat.bloodlust",
					Sheet.num(on.bloodlust()));
		}
	}

	/** How much more a weapon's enchantments add against each kind of mob than against an ordinary one. */
	private static double[] extraEnchantment(ItemStack weapon, float general) {
		double[] extra = new double[Kind.values().length];

		for (Kind kind : Kind.values()) {
			extra[kind.ordinal()] = Math.max(0.0, EnchantmentHelper.getDamageBonus(weapon, kind.type) - general);
		}

		return extra;
	}

	/** A number, then "+x" in each mob kind's colour for what it adds against them only. */
	private static Component withExtras(double value, double[] extras) {
		MutableComponent out = Sheet.plain(value).copy();

		for (Kind kind : Kind.values()) {
			if (extras[kind.ordinal()] > 0.005) {
				out.append(Sheet.colored(" +" + Sheet.num(extras[kind.ordinal()]), kind.color));
			}
		}

		return out;
	}

	/** Notes which coloured "+x" the summary uses, to explain each once at the end. */
	private static void extrasKey(Sheet sheet, double[] extras) {
		for (Kind kind : Kind.values()) {
			if (extras[kind.ordinal()] > 0.005) {
				sheet.summaryKeys.add(kind.key);
			}
		}
	}

	/**
	 * Each enchantment that adds damage, on its own line: what it adds against every mob, and in its mob kind's
	 * colour what it adds against those only (Smite against undead, and so on).
	 */
	private static void enchantmentRows(ItemStack weapon, Sheet sheet) {
		double total = 0.0;
		boolean any = false;

		for (Map.Entry<Enchantment, Integer> entry : EnchantmentHelper.getEnchantments(weapon).entrySet()) {
			Enchantment enchantment = entry.getKey();
			int enchantLevel = entry.getValue();
			ItemStack single = new ItemStack(weapon.getItem());
			single.enchant(enchantment, enchantLevel);
			MutableComponent name = Component.translatable(enchantment.getDescriptionId()).append(" ")
					.append(Component.translatable("enchantment.level." + enchantLevel));
			float general = EnchantmentHelper.getDamageBonus(single, MobType.UNDEFINED);

			if (Math.abs(general) > 0.005F) {
				sheet.rowLabel(Sheet.plus(general), name.copy().withStyle(style -> style.withColor(0xEFE6D2)));
				total += general;
				any = true;
			}

			for (Kind kind : Kind.values()) {
				float against = EnchantmentHelper.getDamageBonus(single, kind.type) - general;

				if (against > 0.005F) {
					sheet.rowLabel(Sheet.colored("+" + Sheet.num(against), kind.color),
							Component.translatable("bloom.sheet.combat.enchant_vs." + kind.key, name).withStyle(style -> style.withColor(kind.color)));
					any = true;
				}
			}
		}

		if (!any) {
			sheet.row(Sheet.plus(0.0), "combat.enchantments");
		} else if (total > 0.005) {
			sheet.note("combat.enchantments.note");
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
	private static void areaRows(ServerPlayer player, ItemStack weapon, Sheet sheet, Switches on, float attack, float enchant, double[] extra,
			double hit, float armor, float toughness, double penetration, BloomConfig config) {
		boolean swordSweep = weapon.canPerformAction(ToolActions.SWORD_SWEEP);
		boolean batterUp = !swordSweep && Weapons.isMeleeWeapon(weapon) && CombatSkill.active(player, CombatIds.BATTER_UP);
		boolean aura = CombatSkill.active(player, CombatIds.AURA_SWORD);
		boolean shock = CombatSkill.has(player, CombatIds.LIGHTNING_SHOCK, Family.TRIDENT, weapon);
		boolean overkill = CombatSkill.active(player, CombatIds.OVERKILL);
		// Better Combat runs this weapon's swings: its combo and its rule for hitting several mobs replace the sweep.
		List<BetterCombat.Swing> combo = BetterCombat.swings(player, weapon);

		if (!combo.isEmpty()) {
			betterCombatRows(player, weapon, sheet, attack, enchant, hit, armor, toughness, penetration, combo);

			if (!BetterCombat.crowdRule().vanillaSweeping()) {
				swordSweep = false;
				batterUp = false;
			}
		}

		if (!swordSweep && !batterUp && !aura && !shock && !overkill) {
			return;
		}

		sheet.heading("combat.h.area");
		// Sweeps and Aura waves get First Strike and Bloodlust, never Predator.
		double spread = shared(on, config, false);

		if (swordSweep || batterUp) {
			double ratio = sweepRatio(player, weapon);
			// Player.attack (1.20.1) and Batter Up! alike: 1 + ratio x the whole hit (attack and enchantments). The
			// enchantments are the main target's, so the coloured part is what it adds when that target is one of them.
			double sweep = 1.0 + ratio * (attack + enchant);
			double sweepAfter = afterArmor(sweep * spread, armor, toughness, penetration);
			double[] sweepExtra = new double[Kind.values().length];

			for (Kind kind : Kind.values()) {
				double more = ratio * extra[kind.ordinal()];

				if (more > 0.005) {
					sweepExtra[kind.ordinal()] = afterArmor((sweep + more) * spread, armor, toughness, penetration) - sweepAfter;
				}
			}

			sheet.row(Sheet.pct(ratio), "combat.sweep_ratio");
			sheet.total(withExtras(sweepAfter, sweepExtra), batterUp ? "combat.sweep_batter" : "combat.sweep");
			sheet.note("combat.sweep.note");
			sheet.summary(withExtras(sweepAfter, sweepExtra), "combat.sweep");
		}

		if (aura) {
			double wave = attack * spread;
			sheet.row(Sheet.plain(afterArmor(wave, armor, toughness, penetration)), "combat.aura", Sheet.num(config.auraSwordHits));
		}

		if (shock) {
			sheet.row(Sheet.plain(hit * config.lightningShockRatio), "combat.shock", Sheet.num(config.lightningShockSparks),
					Sheet.num(config.lightningShockJumps));
		}

		if (overkill) {
			sheet.row(Sheet.pct(config.overkillRatio), "combat.overkill", Sheet.num(config.overkillMaxTargets), Sheet.num(config.overkillRadius));
		}
	}

	/**
	 * Better Combat: each swing of the weapon's combo (its shape, arc and damage), then what a swing does to each mob
	 * when it hits several at once. Its combo multiplier and crowd rule change the attack damage only, so the
	 * enchantments' part of the hit stays as it is.
	 */
	private static void betterCombatRows(ServerPlayer player, ItemStack weapon, Sheet sheet, float attack, float enchant, double hit, float armor,
			float toughness, double penetration, List<BetterCombat.Swing> combo) {
		sheet.heading("combat.h.bettercombat");

		for (int i = 0; i < combo.size(); i++) {
			BetterCombat.Swing step = combo.get(i);
			double damage = afterArmor(attackScaled(hit, attack, enchant, step.damageMultiplier()), armor, toughness, penetration);
			Component shape = Component.translatable("bloom.sheet.combat.bc.shape." + step.shape());
			String key = step.angle() > 0.0 ? "combat.bc.swing" : "combat.bc.swing_straight";

			if (Math.abs(step.damageMultiplier() - 1.0) > 0.005) {
				sheet.row(Sheet.plain(damage), key + "_times", Sheet.num(i + 1), shape, Sheet.num(step.angle()), Sheet.num(step.damageMultiplier()));
			} else {
				sheet.row(Sheet.plain(damage), key, Sheet.num(i + 1), shape, Sheet.num(step.angle()));
			}
		}

		if (combo.size() > 1) {
			sheet.note("combat.bc.combo.note");
		}

		BetterCombat.CrowdRule rule = BetterCombat.crowdRule();

		if (!rule.enabled() || rule.extraTargets() <= 0) {
			return;
		}

		boolean fullSweep = CombatSkill.has(player, CombatIds.FULL_SWEEP, Family.SWORD, weapon);
		boolean batterUp = !Weapons.is(weapon, Family.SWORD) && Weapons.isMeleeWeapon(weapon) && CombatSkill.active(player, CombatIds.BATTER_UP);
		int sweeping = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SWEEPING_EDGE, weapon);
		double single = afterArmor(hit, armor, toughness, penetration);

		for (int mobs = 2; mobs <= rule.extraTargets() + 1; mobs++) {
			// Full Sweep and Batter Up! take the penalty away (BetterCombat.crowdCounter).
			double multiplier = fullSweep || batterUp ? 1.0 : rule.multiplier(mobs, sweeping);
			double each = afterArmor(attackScaled(hit, attack, enchant, multiplier), armor, toughness, penetration);
			double change = single > 0.0 ? each / single - 1.0 : 0.0;
			Component value = Sheet.colored(Sheet.num(each) + "  (" + (change < 0.0 ? "-" : "+") + Math.round(Math.abs(change) * 100.0) + "%)",
					Sheet.PLAIN);
			sheet.row(value, "combat.bc.crowd", Sheet.num(mobs));

			if (mobs == 3) {
				sheet.summary(value, "combat.bc.crowd_summary", Sheet.num(mobs));
			}
		}

		sheet.note("combat.bc.crowd.note", Sheet.num(rule.maxPenalty() * 100.0), Sheet.num(rule.extraTargets() + 1));

		if (fullSweep || batterUp) {
			sheet.note(fullSweep ? "combat.bc.crowd.full_sweep" : "combat.bc.crowd.batter_up");
		}
	}

	/** {@code hit} with only its attack damage part multiplied ({@code hit} is (attack + enchantments) after Bloom's bonuses). */
	private static double attackScaled(double hit, float attack, float enchant, double attackMultiplier) {
		double plain = attack + enchant;
		return plain <= 0.0 ? hit : hit * (attack * attackMultiplier + enchant) / plain;
	}

	/** {@link CombatSkill#sweepRatio} for an item that may not be in hand: Full Sweep's whole blow, else Sweeping Edge's share. */
	private static double sweepRatio(ServerPlayer player, ItemStack weapon) {
		if (CombatSkill.has(player, CombatIds.FULL_SWEEP, Family.SWORD, weapon)) {
			return 1.0;
		}

		int level = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SWEEPING_EDGE, weapon);
		return level > 0 ? SweepingEdgeEnchantment.getSweepingDamageRatio(level) : 0.0;
	}

	// ------------------------------------------------------------------ thrown weapons and tridents

	private static void thrown(ServerPlayer player, ItemStack weapon, Sheet sheet, Switches on, boolean tridentOnly, float armor, float toughness,
			double penetration, BloomConfig config) {
		float bonus = EnchantmentHelper.getDamageBonus(weapon, MobType.UNDEFINED);
		float vanilla = TRIDENT_DAMAGE + bonus;
		// CombatAbilities.thrownDamage: the weapon's own damage on your base (no Power or Strength), plus its enchantments.
		double base = tridentOnly ? 0.0 : Loadout.withItem(weapon, Attributes.ATTACK_DAMAGE, player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE));
		double charge = !tridentOnly && on.chargedThrow() ? config.chargeAttackMultiplier : 1.0;
		double damage = tridentOnly ? vanilla : (base + Math.max(0.0, bonus)) * charge;
		double multiplier = shared(on, config, true);
		double after = afterArmor(damage * multiplier, armor, toughness, penetration);
		double[] extras = new double[Kind.values().length];

		for (Kind kind : Kind.values()) {
			double more = EnchantmentHelper.getDamageBonus(weapon, kind.type) - bonus;

			if (more > 0.005) {
				extras[kind.ordinal()] = afterArmor((damage + more * charge) * multiplier, armor, toughness, penetration) - after;
			}
		}

		sheet.heading("combat.h.thrown");
		sheet.row(Sheet.plain(damage), tridentOnly ? "combat.trident_throw" : on.chargedThrow() ? "combat.charged_throw" : "combat.catch_throw");

		if (!tridentOnly) {
			sheet.note("combat.catch_throw.note");
		}

		addBonusRows(player, weapon, new Switches(false, false, 0.0, 0, on.firstStrike(), on.predator(), on.bloodlust(), false), config, sheet, false);
		sheet.total(withExtras(after, extras), "combat.thrown_after");
		sheet.summary(withExtras(after, extras), "combat.thrown");
		extrasKey(sheet, extras);
	}

	// ------------------------------------------------------------------ bows and crossbows

	private static double ranged(ServerPlayer player, ItemStack weapon, Sheet sheet, Switches on, boolean bow, float armor, float toughness,
			double penetration, BloomConfig config) {
		// BowItem.releaseUsing: Power raises the arrow's base 2 by 0.5 per level plus 0.5 (crossbows can't have Power).
		int power = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.POWER_ARROWS, weapon);
		float base = 2.0F + (bow && power > 0 ? power * 0.5F + 0.5F : 0.0F);
		// AbstractArrow.onHitEntity: base times its speed, rounded up. A full draw is 3; a crossbow fires arrows at 3.15.
		double speed = bow ? 3.0 : 3.15;
		int arrow = Mth.ceil(Mth.clamp(speed * base, 0.0, Integer.MAX_VALUE));
		// A fully drawn bow shot is a crit, and so is every crossbow shot a player fires (CrossbowItem.getArrow).
		int critMax = arrow + arrow / 2 + 1;
		double add = CombatSkill.has(player, CombatIds.REINFORCED_SHOT, Family.CROSSBOW, weapon) ? config.reinforcedShotDamage : 0.0;
		double multiplier = shared(on, config, true);

		if (on.marksman()) {
			multiplier *= 1.0 + config.marksmanMaxBonus;
		}

		double low = (arrow + add) * multiplier;
		double high = (critMax + add) * multiplier;
		double lowAfter = afterArmor(low, armor, toughness, penetration);
		double highAfter = afterArmor(high, armor, toughness, penetration);
		sheet.summary(Sheet.plain(Sheet.num(lowAfter) + "-" + Sheet.num(highAfter)), bow ? "combat.arrow_bow" : "combat.arrow_crossbow");

		int count = !bow && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.MULTISHOT, weapon) > 0 ? 3 : 1;
		int pierce = bow ? 0 : EnchantmentHelper.getItemEnchantmentLevel(Enchantments.PIERCING, weapon);

		if (CombatSkill.has(player, CombatIds.PIERCING_SHOT, Family.BOW, weapon)) {
			pierce = Math.max(pierce, config.piercingShotLevel);
		}

		if (count > 1) {
			sheet.summary(Sheet.plain(count), "combat.arrows_per_shot");
		}

		if (pierce > 0) {
			sheet.summary(Sheet.plain(pierce), "combat.pierces");
		}

		sheet.summary(Sheet.seconds(bow ? 20 : CrossbowItem.getChargeDuration(weapon)), bow ? "combat.draw_time" : "combat.load_time");

		sheet.heading("combat.h.arrow");
		sheet.row(Sheet.plain(2.0), "combat.arrow_base");
		sheet.row(Sheet.plus(base - 2.0), "combat.enchantments");
		sheet.row(Sheet.times(speed), bow ? "combat.arrow_speed_bow" : "combat.arrow_speed_crossbow");
		sheet.total(Sheet.plain(arrow), "combat.arrow_total");
		sheet.row(Sheet.plus(critMax - arrow), "combat.arrow_crit");
		sheet.note(bow ? "combat.arrow_crit.note" : "combat.arrow_crit.crossbow");

		if (add > 0.0) {
			sheet.row(Sheet.plus(add), "combat.reinforced");
		}

		if (on.marksman()) {
			sheet.row(Sheet.times(1.0 + config.marksmanMaxBonus), "combat.marksman", Sheet.num(config.marksmanFullDistance));
		} else if (CombatSkill.has(player, CombatIds.MARKSMAN, Family.BOW, weapon)) {
			sheet.note("combat.marksman.off", Sheet.num(config.marksmanStartDistance));
		}

		addBonusRows(player, weapon, on, config, sheet, false);
		sheet.total(Sheet.plain(Sheet.num(low) + "-" + Sheet.num(high)), "combat.arrow_hit");
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

	private static void onHit(ServerPlayer player, ItemStack weapon, Sheet sheet, BloomConfig config, BloomProfile profile, ProgressionService service) {
		sheet.heading("combat.h.on_hit");
		int lootingPoints = service.statValue(profile, CombatIds.LOOTING);
		int lootingEnchant = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.MOB_LOOTING, weapon);
		sheet.row(Sheet.plus(StatFormulas.virtualLooting(lootingPoints) + lootingEnchant), "combat.looting", Sheet.num(lootingPoints));

		if (CombatSkill.has(player, CombatIds.EXECUTE, Family.SWORD, weapon)) {
			sheet.row(Sheet.pct(config.executeThreshold), "combat.execute", Sheet.num(config.executeBossThreshold * 100.0));
		}

		if (CombatSkill.has(player, CombatIds.ARMOR_BREAK, Family.AXE, weapon)) {
			sheet.row(Sheet.pct(config.armorBreakRatio), "combat.armor_break");
		}

		if (CombatSkill.has(player, CombatIds.SLASH, Family.SWORD, weapon)) {
			sheet.row(Sheet.pct(config.slashChance), "combat.slash", Sheet.num(config.bleedTicks / 20.0));
		}

		if (CombatSkill.has(player, CombatIds.DISARM, Family.UNARMED, weapon)) {
			sheet.row(Sheet.pct(config.disarmChance), "combat.disarm");
		}

		FuelImprint fuel = weapon.isEmpty() ? null : FuelImprint.get(weapon);

		if (fuel != null) {
			sheet.row(Sheet.seconds(config.fuelEffectTicks), "combat.fuel", MiscSkill.effectName(fuel.effect(), fuel.amplifier()));
		}
	}

	// ------------------------------------------------------------------ helpers

	/** CombatRules.getDamageAfterAbsorb (1.20.1) with Penetration taken off the armor first (CombatSkill.penetrate). */
	static double afterArmor(double damage, float armor, float toughness, double penetration) {
		if (!(armor > 0.0F) || !(damage > 0.0)) {
			return damage;
		}

		float effective = (float) (armor * (1.0 - penetration));
		float amount = (float) damage;
		float divider = 2.0F + toughness / 4.0F;
		float clamped = Mth.clamp(effective - amount / divider, effective * 0.2F, 20.0F);
		return amount * (1.0F - clamped / 25.0F);
	}

	private static Component targetName(int index) {
		return Component.translatable("bloom.sheet.target." + TARGET_NAMES[index]);
	}

	/**
	 * The picture: the vanilla sword (and Sharpness level) whose plain hit is closest to {@code damage}; past the
	 * best of them, a Netherite Sword with Sharpness V and how many times stronger the player hits.
	 */
	private static void swordGauge(Sheet sheet, double damage) {
		ItemStack best = ItemStack.EMPTY;
		double bestDamage = 0.0;
		int bestLevel = 0;
		ItemStack top = ItemStack.EMPTY;
		double topDamage = 0.0;
		int topLevel = 0;

		for (ItemStack plain : SWORDS) {
			for (int enchantLevel = 0; enchantLevel <= 5; enchantLevel++) {
				ItemStack sword = plain.copy();

				if (enchantLevel > 0) {
					sword.enchant(Enchantments.SHARPNESS, enchantLevel);
				}

				double hit = Loadout.withItem(sword, Attributes.ATTACK_DAMAGE, 1.0) + EnchantmentHelper.getDamageBonus(sword, MobType.UNDEFINED);
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

		boolean past = damage > topDamage + 1.0E-4;
		ItemStack shown = past ? top : best;
		int shownLevel = past ? topLevel : bestLevel;
		double shownDamage = past ? topDamage : bestDamage;
		Component name = shownLevel > 0
				? Component.translatable("bloom.sheet.gauge.with", shown.getHoverName(), Component.translatable(Enchantments.SHARPNESS.getDescriptionId()),
						Component.translatable("enchantment.level." + shownLevel))
				: shown.getHoverName();
		sheet.gauge = shown;
		sheet.gaugeCaption = Component.translatable(past ? "bloom.sheet.gauge.sword_past" : "bloom.sheet.gauge.sword", name, Sheet.num(shownDamage));
		sheet.gaugeBadge = past ? Sheet.colored("×" + Sheet.num(damage / topDamage), Sheet.GOLD) : Component.empty();
	}
}
