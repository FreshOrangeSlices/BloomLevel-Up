package com.freshorangeslices.bloomlevelup.sheet;

import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import com.freshorangeslices.bloomlevelup.config.BloomConfig;
import com.freshorangeslices.bloomlevelup.data.BloomServices;
import com.freshorangeslices.bloomlevelup.effect.StatEffects;
import com.freshorangeslices.bloomlevelup.network.SheetPayloads;
import com.freshorangeslices.bloomlevelup.player.BloomAttachments;
import com.freshorangeslices.bloomlevelup.player.BloomProfile;
import com.freshorangeslices.bloomlevelup.progression.ProgressionService;
import com.freshorangeslices.bloomlevelup.skill.armor.ArmorIds;
import com.freshorangeslices.bloomlevelup.skill.armor.ArmorSkill;
import com.freshorangeslices.bloomlevelup.skill.armor.ArmorStyles;
import com.freshorangeslices.bloomlevelup.stats.StatFormulas;
import com.freshorangeslices.bloomlevelup.stats.StatFormulas.ArmorStyle;

/**
 * The Armor sheet: how much of a hit actually reaches your health, step by step in the order the game applies
 * them (armor, Resistance, protection enchantments, then the Armor category's own layers and Absorb). The hit is
 * set on the sheet itself (FreshOrangeSlices' playtest: the old attacker picker read as too much): how hard it is
 * and what kind (melee, a projectile, magic or a fall), and the Summary says what you'd lose to it. The steps are
 * vanilla 1.20.1's own code (LivingEntity.getDamageAfterArmorAbsorb / MagicAbsorb) and {@link ArmorSkill#layers}, so
 * the sheet matches a real hit. (Forge 1.20.1 build: no blunt hits, Adamantium is gone.)
 */
final class ArmorSheet {
	private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private ArmorSheet() {
	}

	/** The hit set on the sheet (its amount, and its kind by {@link SheetPayloads.ArmorHit#TYPES}), from the request's target. */
	record Hit(int amount, String key) {
		static Hit of(int target) {
			return new Hit(SheetPayloads.ArmorHit.amount(target), SheetPayloads.ArmorHit.TYPES[SheetPayloads.ArmorHit.type(target)]);
		}
	}

	/** The player's defences as the sheet sees them (the build shown, any point not spent yet included). */
	private record Body(int armorValue, double armor, double toughness, double rawArmor, double maxHealth, double knockback) {
		static Body of(Loadout loadout) {
			AttributeInstance armor = loadout.instance(Attributes.ARMOR);
			double raw = armor.getBaseValue();

			for (AttributeModifier modifier : armor.getModifiers()) {
				if (modifier.getOperation() == AttributeModifier.Operation.ADDITION) {
					raw += modifier.getAmount();
				}
			}

			double value = armor.getValue();
			return new Body(Mth.floor(value), value, loadout.value(Attributes.ARMOR_TOUGHNESS), raw, loadout.value(Attributes.MAX_HEALTH),
					loadout.value(Attributes.KNOCKBACK_RESISTANCE));
		}
	}

	/** What's left of a hit after each step. */
	private record Steps(float start, float afterArmor, float afterResistance, float afterProtection, List<ArmorSkill.Layer> layers,
			float afterLayers, float health) {
	}

	static void build(ServerPlayer player, Sheet sheet) {
		ServerLevel level = (ServerLevel) player.level();
		BloomConfig config = BloomServices.config();
		ProgressionService service = BloomServices.progression();
		BloomProfile profile = BloomAttachments.profile(player);
		boolean ownsBrace = service.isMajorActive(profile, ArmorIds.BRACE);
		boolean braced = false;

		if (ownsBrace) {
			boolean now = ArmorSkill.isBraced(player);
			Sheet.Mode mode = sheet.toggle("braced", Sheet.word(now ? "live.on" : "live.off"));
			braced = mode == Sheet.Mode.LIVE ? now : mode == Sheet.Mode.MAX;
		}

		Hit set = Hit.of(sheet.target);
		float hit = set.amount();
		DamageSource source = source(level, set.key());

		// ---- summary
		Body body = Body.of(new Loadout(player, player.getMainHandItem()));
		double armor = body.armor();
		double toughness = body.toughness();
		Steps steps = steps(level, player, body, source, hit, braced);
		sheet.summary(Sheet.plain(body.armorValue()), "armor.armor_points");

		if (toughness > 0.0) {
			sheet.summary(Sheet.plain(toughness), "armor.toughness");
		}

		sheet.summary(Sheet.plain(body.maxHealth()), "armor.max_health");
		sheet.summaryNote("armor.hit.note", Sheet.num(hit), typeName(set));
		sheet.summary(taken(steps), "armor.lost");
		sheet.summary(hitsToDown(body.maxHealth(), steps.health()), "armor.hits_to_down");

		double dodge = StatFormulas.dodgeChance(service.statValue(profile, ArmorIds.DODGE), config.dodgeChanceCap);
		boolean light = service.isMajorActive(profile, ArmorIds.FITTING_ARMOR) && ArmorStyles.styleOf(player) == ArmorStyle.LIGHT;
		double anyDodge = StatFormulas.anyOf(dodge, light ? 0.10 : 0.0);

		if (anyDodge > 0.0) {
			sheet.summary(Sheet.pct(anyDodge), "armor.dodge_total");
		}

		// ---- breakdown: the hit set on the sheet, step by step
		sheet.heading("armor.h.steps", Sheet.num(hit), typeName(set));
		sheet.row(Sheet.plain(steps.start()), "armor.step.hit");
		stepRow(sheet, "armor.step.armor", steps.start(), steps.afterArmor());
		stepRow(sheet, "armor.step.resistance", steps.afterArmor(), steps.afterResistance());
		stepRow(sheet, "armor.step.protection", steps.afterResistance(), steps.afterProtection());

		for (ArmorSkill.Layer layer : steps.layers()) {
			sheet.row(Sheet.colored("-" + Sheet.pctNum(layer.reduction() * 100.0) + "%", Sheet.GOOD), "layer." + layer.key());
		}

		if (steps.health() < steps.afterLayers()) {
			sheet.row(Sheet.colored("-50%", Sheet.GOOD), "armor.step.absorb");
			sheet.note("armor.step.absorb.note", Sheet.num(Math.floor(steps.afterLayers() * 0.5)));
		}

		sheet.total(Sheet.plain(steps.health()), "armor.step.health");
		sheet.row(hitsToDown(body.maxHealth(), steps.health()), "armor.hits_to_down");
		sheet.note("armor.step.note");

		// ---- breakdown: your armor
		sheet.heading("armor.h.armor");
		double gear = 0.0;
		double gearToughness = 0.0;

		for (EquipmentSlot slot : SLOTS) {
			ItemStack piece = player.getItemBySlot(slot);
			gear += added(piece, slot, Attributes.ARMOR);
			gearToughness += added(piece, slot, Attributes.ARMOR_TOUGHNESS);
		}

		double defense = StatFormulas.defenseArmor(service.statValue(profile, ArmorIds.DEFENSE));
		boolean titan = service.isMajorActive(profile, ArmorIds.TITAN_HIDE) && StatEffects.armorSlotsEmpty(player);
		double defenseShown = titan ? (defense + 2.0) * 2.0 : defense;
		double rawArmor = body.rawArmor();
		sheet.row(Sheet.plus(gear), "armor.gear");
		sheet.row(Sheet.plus(defenseShown), titan ? "armor.defense_titan" : "armor.defense", Sheet.num(service.statValue(profile, ArmorIds.DEFENSE)));

		if (Math.abs(rawArmor - gear - defenseShown) > 0.005) {
			sheet.row(Sheet.plus(rawArmor - gear - defenseShown), "armor.other");
		}

		sheet.total(Sheet.plain(armor), "armor.armor_total");

		if (rawArmor > 30.0) {
			sheet.note("armor.cap_note", Sheet.num(rawArmor));
		}

		sheet.row(Sheet.plain(toughness), "armor.toughness");

		if (Math.abs(toughness - gearToughness) > 0.005) {
			sheet.note("armor.toughness.other");
		}

		double overcap = StatFormulas.armorOvercapReduction(rawArmor, config.armorOvercapReductionCap);

		if (overcap > 0.0) {
			sheet.row(Sheet.pct(overcap), "armor.overcap");
			sheet.note("armor.overcap.note");
		}

		// ---- breakdown: your health
		sheet.heading("armor.h.health");
		double vitality = StatFormulas.vitalityBonusHealth(service.statValue(profile, ArmorIds.VITALITY));
		double protein = service.isMajorActive(profile, ArmorIds.PROTEIN) ? StatFormulas.proteinBonusHealth(profile.counter(ArmorIds.PROTEIN_MEALS)) : 0.0;
		double baseHealth = player.getAttributeBaseValue(Attributes.MAX_HEALTH);
		sheet.row(Sheet.plain(baseHealth), "armor.base_health");
		sheet.row(Sheet.plus(vitality), "armor.vitality", Sheet.num(service.statValue(profile, ArmorIds.VITALITY)));

		if (protein > 0.0) {
			sheet.row(Sheet.plus(protein), "armor.protein", Sheet.num(profile.counter(ArmorIds.PROTEIN_MEALS)));
		}

		double otherHealth = body.maxHealth() - baseHealth - vitality - protein;

		if (Math.abs(otherHealth) > 0.005) {
			sheet.row(Sheet.plus(otherHealth), "armor.other_health");
		}

		sheet.total(Sheet.plain(body.maxHealth()), "armor.max_health");

		if (player.getAbsorptionAmount() > 0.0F) {
			sheet.row(Sheet.plus(player.getAbsorptionAmount()), "armor.absorption");
		}

		// ---- breakdown: not getting hit at all
		sheet.heading("armor.h.avoid");
		sheet.row(Sheet.pct(dodge), "armor.dodge", Sheet.num(service.statValue(profile, ArmorIds.DODGE)));

		if (light) {
			sheet.row(Sheet.pct(0.10), "armor.dodge_light");
		}

		sheet.total(Sheet.pct(anyDodge), "armor.dodge_total");
		sheet.note("armor.dodge.note", Sheet.num(config.dodgeChanceCap * 100.0));

		if (service.isMajorActive(profile, ArmorIds.REFLECT)) {
			sheet.row(Sheet.pct(StatFormulas.anyOf(0.10, 0.25)), "armor.reflect");
		}

		if (service.isMajorActive(profile, ArmorIds.PARKOUR)) {
			sheet.row(Sheet.pct(0.35), "armor.parkour");
		}

		if (service.isMajorActive(profile, ArmorIds.LAST_BREATH)) {
			long now = level.getGameTime();
			long start = profile.counter(ArmorIds.LAST_BREATH_WINDOW_START);
			long attempts = profile.counter(ArmorIds.LAST_BREATH_ATTEMPTS);
			boolean fresh = attempts == 0 || now < start || now - start >= config.lastBreathWindowTicks;
			sheet.row(Sheet.pct(fresh ? 1.0 : StatFormulas.lastBreathChance((int) Math.min(attempts, 64))), "armor.last_breath");
		}

		if (service.isMajorActive(profile, ArmorIds.CASTLE_OF_STONE)) {
			sheet.row(Sheet.word(profile.counter(ArmorIds.CASTLE_SPENT) == 0 ? "value.ready" : "value.spent"), "armor.castle");
		}

		// ---- breakdown: knockback, wear and effects
		sheet.heading("armor.h.rest");
		sheet.row(Sheet.pct(Math.min(1.0, body.knockback())), "armor.knockback");
		int durability = service.statValue(profile, ArmorIds.DURABILITY);
		sheet.row(Sheet.pct(StatFormulas.armorDurabilitySaveChance(durability)), "armor.durability_armor", Sheet.num(durability));
		sheet.row(Sheet.pct(StatFormulas.shieldDurabilitySaveChance(durability)), "armor.durability_shield");

		if (service.isMajorActive(profile, ArmorIds.REBOUND)) {
			sheet.row(Sheet.pct(0.15), "armor.rebound");
		}

		if (service.isMajorActive(profile, ArmorIds.UNYIELDING)) {
			sheet.row(Sheet.pct(0.5), "armor.unyielding");
		}

		if (service.isMajorActive(profile, ArmorIds.THICK_BLOOD)) {
			sheet.row(Sheet.times(0.5), "armor.thick_blood");
		}

		// The picture: the kind of hit, and what gets through.
		sheet.gauge = picture(set.key());
		sheet.gaugeCaption = Component.translatable("bloom.sheet.gauge.attacker", Sheet.num(steps.health()), Sheet.num(hit));
	}

	/** The kind of hit, as the sheet names it ("melee", "magic"...). */
	private static Component typeName(Hit set) {
		return Component.translatable("bloom.sheet.armor.type." + set.key() + ".lower");
	}

	/** A real damage source for a kind of hit: a zombie's swing, a skeleton's arrow, magic (Harming) or a fall. */
	private static DamageSource source(ServerLevel level, String type) {
		return switch (type) {
			case "projectile" -> {
				LivingEntity skeleton = Dummies.of(level, EntityType.SKELETON);
				yield source(level, DamageTypes.ARROW, null, skeleton);
			}
			case "magic" -> source(level, DamageTypes.MAGIC, null, null);
			case "fall" -> source(level, DamageTypes.FALL, null, null);
			default -> {
				LivingEntity zombie = Dummies.of(level, EntityType.ZOMBIE);
				zombie.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
				yield source(level, DamageTypes.MOB_ATTACK, zombie, zombie);
			}
		};
	}

	/** What the sheet pictures for a kind of hit. */
	private static ItemStack picture(String type) {
		return switch (type) {
			case "projectile" -> new ItemStack(Items.ARROW);
			case "magic" -> PotionUtils.setPotion(new ItemStack(Items.SPLASH_POTION), Potions.HARMING);
			case "fall" -> new ItemStack(Items.FEATHER);
			default -> new ItemStack(Items.IRON_SWORD);
		};
	}

	/** How many such hits take you from full health to nothing ("Never" when none gets through). */
	private static Component hitsToDown(double maxHealth, double perHit) {
		if (perHit <= 1.0E-4) {
			return Sheet.word("armor.never");
		}

		return Sheet.plain(Math.max(1.0, Math.ceil(maxHealth / perHit - 1.0E-6)));
	}

	private static Holder<DamageType> type(ServerLevel level, ResourceKey<DamageType> key) {
		return level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key);
	}

	private static DamageSource source(ServerLevel level, ResourceKey<DamageType> key, Entity direct, Entity causing) {
		return new DamageSource(type(level, key), direct, causing);
	}

	/**
	 * A hit of {@code amount} through vanilla 1.20.1's armor and magic steps (LivingEntity.getDamageAfterArmorAbsorb and
	 * getDamageAfterMagicAbsorb, without their side effects), then the Armor category's layers and Absorb.
	 */
	private static Steps steps(ServerLevel level, ServerPlayer player, Body body, DamageSource source, float amount, boolean braced) {
		float afterArmor = amount;

		if (!source.is(DamageTypeTags.BYPASSES_ARMOR)) {
			afterArmor = CombatRules.getDamageAfterAbsorb(amount, body.armorValue(), (float) body.toughness());
		}

		float afterResistance = afterArmor;
		float afterProtection = afterArmor;

		if (!source.is(DamageTypeTags.BYPASSES_EFFECTS)) {
			MobEffectInstance resistance = player.getEffect(MobEffects.DAMAGE_RESISTANCE);

			if (resistance != null && !source.is(DamageTypeTags.BYPASSES_RESISTANCE)) {
				int cut = (resistance.getAmplifier() + 1) * 5;
				afterResistance = Math.max(afterArmor * (25 - cut) / 25.0F, 0.0F);
			}

			afterProtection = afterResistance;

			if (afterResistance > 0.0F && !source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)) {
				int protection = EnchantmentHelper.getDamageProtection(player.getArmorSlots(), source);

				if (protection > 0) {
					afterProtection = CombatRules.getDamageAfterMagicAbsorb(afterResistance, protection);
				}
			}
		}

		List<ArmorSkill.Layer> layers = ArmorSkill.layers(player, source, braced, body.rawArmor());
		double[] cuts = new double[layers.size()];

		for (int i = 0; i < cuts.length; i++) {
			cuts[i] = layers.get(i).reduction();
		}

		float afterLayers = (float) StatFormulas.applyReductions(afterProtection, cuts);
		float health = afterLayers > 0.0F && ArmorSkill.absorbs(player) && wearsDamageableArmor(player) ? afterLayers * 0.5F : afterLayers;
		return new Steps(amount, afterArmor, afterResistance, afterProtection, layers, afterLayers, health);
	}

	private static boolean wearsDamageableArmor(ServerPlayer player) {
		for (EquipmentSlot slot : SLOTS) {
			ItemStack piece = player.getItemBySlot(slot);

			if (!piece.isEmpty() && piece.isDamageableItem()) {
				return true;
			}
		}

		return false;
	}

	/** What a piece's own flat modifiers in its slot add to {@code attribute} (armor and toughness are flat in 1.20.1). */
	private static double added(ItemStack piece, EquipmentSlot slot, net.minecraft.world.entity.ai.attributes.Attribute attribute) {
		double total = 0.0;

		for (AttributeModifier modifier : piece.getAttributeModifiers(slot).get(attribute)) {
			if (modifier.getOperation() == AttributeModifier.Operation.ADDITION) {
				total += modifier.getAmount();
			}
		}

		return total;
	}

	private static void stepRow(Sheet sheet, String key, float before, float after) {
		if (before > 0.0F && after < before - 1.0E-4F) {
			sheet.row(Sheet.colored("-" + Sheet.pctNum((1.0 - after / before) * 100.0) + "%", Sheet.GOOD), key);
		}
	}

	/** "3.2 (-68%)": health lost, and how much of the hit didn't get through. */
	private static Component taken(Steps steps) {
		double blocked = steps.start() > 0.0F ? 1.0 - steps.health() / steps.start() : 0.0;
		return Sheet.colored(Sheet.num(steps.health()) + "  (-" + Math.round(blocked * 100.0) + "%)", Sheet.PLAIN);
	}
}
