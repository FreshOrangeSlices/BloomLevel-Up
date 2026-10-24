package com.freshorangeslices.bloomlevelup.sheet;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
 * them (armor, Resistance, protection enchantments, then the Armor category's own layers and Absorb), for the
 * kinds of hits that differ (mobs, blades, arrows, fire, explosions, falls, magic), plus the chances to avoid a
 * hit altogether. The steps are vanilla's own code (LivingEntity.getDamageAfterArmorAbsorb / MagicAbsorb) and
 * {@link ArmorSkill#layers}, so the sheet matches a real hit.
 */
final class ArmorSheet {
	private static final float[] HIT_SIZES = {5.0F, 10.0F, 20.0F, 50.0F};
	private static final String[] SET_MATERIALS = {"leather", "chainmail", "copper", "iron", "diamond", "netherite"};
	private static final String[] PIECES = {"helmet", "chestplate", "leggings", "boots"};
	private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private ArmorSheet() {
	}

	/** One kind of incoming hit: its name (bloom.sheet.armor.kind.<key>) and a real damage source for it. */
	private record Kind(String key, DamageSource source) {
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

		List<Component> sizes = new ArrayList<>();

		for (float size : HIT_SIZES) {
			sizes.add(Component.translatable("bloom.sheet.armor.hit_size", Sheet.num(size)));
		}

		float hit = HIT_SIZES[sheet.targets(sizes, 1)];
		LivingEntity zombie = Dummies.of(level, EntityTypes.ZOMBIE);
		List<Kind> kinds = kinds(level, player, zombie, profile, service);

		// ---- summary
		double armor = player.getAttributeValue(Attributes.ARMOR);
		double toughness = player.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
		sheet.summary(Sheet.plain(player.getArmorValue()), "armor.armor_points");

		if (toughness > 0.0) {
			sheet.summary(Sheet.plain(toughness), "armor.toughness");
		}

		sheet.summary(Sheet.plain(player.getMaxHealth()), "armor.max_health");
		sheet.summaryNote("armor.taken", Sheet.num(hit));

		for (Kind kind : kinds) {
			Steps steps = steps(level, player, kind.source(), hit, braced);
			sheet.summary(taken(steps), "armor.kind." + kind.key());
		}

		double dodge = StatFormulas.dodgeChance(service.statValue(profile, ArmorIds.DODGE), config.dodgeChanceCap);
		boolean light = service.isMajorActive(profile, ArmorIds.FITTING_ARMOR) && ArmorStyles.styleOf(player) == ArmorStyle.LIGHT;
		double anyDodge = StatFormulas.anyOf(dodge, light ? 0.10 : 0.0);

		if (anyDodge > 0.0) {
			sheet.summary(Sheet.pct(anyDodge), "armor.dodge_total");
		}

		// ---- breakdown: armor points
		sheet.heading("armor.h.armor");
		double gear = 0.0;
		double gearToughness = 0.0;

		for (EquipmentSlot slot : SLOTS) {
			ItemStack piece = player.getItemBySlot(slot);
			ItemAttributeModifiers modifiers = piece.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
			gear += modifiers.compute(Attributes.ARMOR, 0.0, slot);
			gearToughness += modifiers.compute(Attributes.ARMOR_TOUGHNESS, 0.0, slot);
		}

		double defense = StatFormulas.defenseArmor(service.statValue(profile, ArmorIds.DEFENSE));
		boolean titan = service.isMajorActive(profile, ArmorIds.TITAN_HIDE) && StatEffects.armorSlotsEmpty(player);
		double defenseShown = titan ? (defense + 2.0) * 2.0 : defense;
		double rawArmor = ArmorSkill.armorBeforeCap(player);
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
		sheet.row(Sheet.pct(overcap), "armor.overcap");
		sheet.note("armor.overcap.note");

		// ---- breakdown: one hit, step by step
		Kind first = kinds.get(0);
		Steps steps = steps(level, player, first.source(), hit, braced);
		sheet.heading("armor.h.steps", Sheet.num(hit));
		sheet.row(Sheet.plain(steps.start()), "armor.step.hit");
		stepRow(sheet, "armor.step.armor", steps.start(), steps.afterArmor());
		stepRow(sheet, "armor.step.resistance", steps.afterArmor(), steps.afterResistance());
		stepRow(sheet, "armor.step.protection", steps.afterResistance(), steps.afterProtection());

		for (ArmorSkill.Layer layer : steps.layers()) {
			sheet.row(Sheet.colored("-" + Sheet.num(layer.reduction() * 100.0) + "%", Sheet.GOOD), "layer." + layer.key());
		}

		if (steps.health() < steps.afterLayers()) {
			sheet.row(Sheet.colored("-50%", Sheet.GOOD), "armor.step.absorb");
			sheet.note("armor.step.absorb.note", Sheet.num(Math.floor(steps.afterLayers() * 0.5)));
		}

		sheet.total(Sheet.plain(steps.health()), "armor.step.health");
		sheet.note("armor.step.note");

		// ---- breakdown: other kinds of hits
		sheet.heading("armor.h.kinds", Sheet.num(hit));

		for (Kind kind : kinds) {
			Steps each = steps(level, player, kind.source(), hit, braced);
			sheet.row(taken(each), "armor.kind." + kind.key());
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

		// ---- breakdown: health and the rest
		sheet.heading("armor.h.health");
		double vitality = StatFormulas.vitalityBonusHealth(service.statValue(profile, ArmorIds.VITALITY));
		double protein = service.isMajorActive(profile, ArmorIds.PROTEIN) ? StatFormulas.proteinBonusHealth(profile.counter(ArmorIds.PROTEIN_MEALS)) : 0.0;
		double baseHealth = player.getAttributeBaseValue(Attributes.MAX_HEALTH);
		sheet.row(Sheet.plain(baseHealth), "armor.base_health");
		sheet.row(Sheet.plus(vitality), "armor.vitality", Sheet.num(service.statValue(profile, ArmorIds.VITALITY)));

		if (protein > 0.0) {
			sheet.row(Sheet.plus(protein), "armor.protein", Sheet.num(profile.counter(ArmorIds.PROTEIN_MEALS)));
		}

		double otherHealth = player.getMaxHealth() - baseHealth - vitality - protein;

		if (Math.abs(otherHealth) > 0.005) {
			sheet.row(Sheet.plus(otherHealth), "armor.other_health");
		}

		sheet.total(Sheet.plain(player.getMaxHealth()), "armor.max_health");

		if (player.getAbsorptionAmount() > 0.0F) {
			sheet.row(Sheet.plus(player.getAbsorptionAmount()), "armor.absorption");
		}

		sheet.row(Sheet.pct(Math.min(1.0, player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE))), "armor.knockback");
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

		armorGauge(level, player, zombie, sheet, kinds.get(0).source(), hit, steps.health());
	}

	/** The kinds of hits the sheet compares; blades and blunt weapons only when a Major treats them differently. */
	private static List<Kind> kinds(ServerLevel level, ServerPlayer player, LivingEntity zombie, BloomProfile profile, ProgressionService service) {
		List<Kind> kinds = new ArrayList<>();
		zombie.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		kinds.add(new Kind("mob", source(level, DamageTypes.MOB_ATTACK, zombie, zombie)));

		// A hit's weapon is whatever its attacker holds: a stand-in husk with a sword, a stand-in zombie villager with a mace.
		if (service.isMajorActive(profile, ArmorIds.VIBRANIUM)) {
			LivingEntity swordsman = Dummies.of(level, EntityTypes.HUSK);
			swordsman.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
			kinds.add(new Kind("blade", source(level, DamageTypes.MOB_ATTACK, swordsman, swordsman)));
		}

		if (service.isMajorActive(profile, ArmorIds.ADAMANTIUM)) {
			LivingEntity basher = Dummies.of(level, EntityTypes.ZOMBIE_VILLAGER);
			basher.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.MACE));
			kinds.add(new Kind("blunt", source(level, DamageTypes.MOB_ATTACK, basher, basher)));
		}

		kinds.add(new Kind("arrow", source(level, DamageTypes.ARROW, null, zombie)));
		kinds.add(new Kind("fire", source(level, DamageTypes.IN_FIRE, null, null)));
		kinds.add(new Kind("explosion", source(level, DamageTypes.EXPLOSION, null, null)));
		kinds.add(new Kind("fall", source(level, DamageTypes.FALL, null, null)));
		kinds.add(new Kind("magic", source(level, DamageTypes.MAGIC, null, null)));
		return kinds;
	}

	private static Holder<DamageType> type(ServerLevel level, ResourceKey<DamageType> key) {
		return level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(key);
	}

	private static DamageSource source(ServerLevel level, ResourceKey<DamageType> key, Entity direct, Entity causing) {
		return new DamageSource(type(level, key), direct, causing);
	}

	/**
	 * A hit of {@code amount} through vanilla's armor and magic steps (LivingEntity.getDamageAfterArmorAbsorb and
	 * getDamageAfterMagicAbsorb, without their side effects), then the Armor category's layers and Absorb.
	 */
	private static Steps steps(ServerLevel level, ServerPlayer player, DamageSource source, float amount, boolean braced) {
		float afterArmor = amount;

		if (!source.is(DamageTypeTags.BYPASSES_ARMOR)) {
			afterArmor = CombatRules.getDamageAfterAbsorb(player, amount, source, player.getArmorValue(),
					(float) player.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
		}

		float afterResistance = afterArmor;
		float afterProtection = afterArmor;

		if (!source.is(DamageTypeTags.BYPASSES_EFFECTS)) {
			MobEffectInstance resistance = player.getEffect(MobEffects.RESISTANCE);

			if (resistance != null && !source.is(DamageTypeTags.BYPASSES_RESISTANCE)) {
				int cut = (resistance.getAmplifier() + 1) * 5;
				afterResistance = Math.max(afterArmor * (25 - cut) / 25.0F, 0.0F);
			}

			afterProtection = afterResistance;

			if (afterResistance > 0.0F && !source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)) {
				float protection = EnchantmentHelper.getDamageProtection(level, player, source);

				if (protection > 0.0F) {
					afterProtection = CombatRules.getDamageAfterMagicAbsorb(afterResistance, protection);
				}
			}
		}

		List<ArmorSkill.Layer> layers = ArmorSkill.layers(player, source, braced);
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

	private static void stepRow(Sheet sheet, String key, float before, float after) {
		if (before > 0.0F && after < before - 1.0E-4F) {
			sheet.row(Sheet.colored("-" + Sheet.num((1.0 - after / before) * 100.0) + "%", Sheet.GOOD), key);
		}
	}

	/** "3.2 (-68%)": health lost, and how much of the hit didn't get through. */
	private static Component taken(Steps steps) {
		double blocked = steps.start() > 0.0F ? 1.0 - steps.health() / steps.start() : 0.0;
		return Sheet.colored(Sheet.num(steps.health()) + "  (-" + Math.round(blocked * 100.0) + "%)", Sheet.PLAIN);
	}

	/**
	 * The picture: the full vanilla armor set (and Protection level on every piece) that would let through the
	 * amount closest to what reaches the player's health from the same mob hit; past full Netherite with
	 * Protection IV, how many times less gets through.
	 */
	private static void armorGauge(ServerLevel level, ServerPlayer player, LivingEntity dummy, Sheet sheet, DamageSource source, float hit, double taken) {
		Holder<Enchantment> protection = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION);
		ItemStack best = ItemStack.EMPTY;
		double bestTaken = 0.0;
		int bestLevel = 0;
		ItemStack top = ItemStack.EMPTY;
		double topTaken = Double.MAX_VALUE;
		int topLevel = 0;

		String shownMaterial = "";
		String bestMaterial = "";
		String topMaterial = "";

		for (String material : SET_MATERIALS) {
			Item[] items = new Item[4];
			boolean complete = true;

			for (int i = 0; i < 4; i++) {
				Optional<Item> item = BuiltInRegistries.ITEM.getOptional(Identifier.withDefaultNamespace(material + "_" + PIECES[i]));
				complete &= item.isPresent() && item.get() != Items.AIR;
				items[i] = item.orElse(Items.AIR);
			}

			if (!complete) {
				continue;
			}

			for (int enchantLevel = 0; enchantLevel <= 4; enchantLevel++) {
				float armor = 0.0F;
				float toughness = 0.0F;

				for (int i = 0; i < 4; i++) {
					ItemStack piece = new ItemStack(items[i]);

					if (enchantLevel > 0) {
						piece.enchant(protection, enchantLevel);
					}

					ItemAttributeModifiers modifiers = piece.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
					armor += (float) modifiers.compute(Attributes.ARMOR, 0.0, SLOTS[i]);
					toughness += (float) modifiers.compute(Attributes.ARMOR_TOUGHNESS, 0.0, SLOTS[i]);
					dummy.setItemSlot(SLOTS[i], piece);
				}

				float after = CombatRules.getDamageAfterAbsorb(dummy, hit, source, armor, toughness);
				float epf = EnchantmentHelper.getDamageProtection(level, dummy, source);
				double through = epf > 0.0F ? CombatRules.getDamageAfterMagicAbsorb(after, epf) : after;
				ItemStack chest = dummy.getItemBySlot(EquipmentSlot.CHEST).copy();
				double gap = Math.abs(through - taken);
				double bestGap = Math.abs(bestTaken - taken);

				if (best.isEmpty() || gap < bestGap - 1.0E-4 || (Math.abs(gap - bestGap) <= 1.0E-4 && enchantLevel < bestLevel)) {
					best = chest;
					bestTaken = through;
					bestLevel = enchantLevel;
					bestMaterial = material;
				}

				if (through < topTaken) {
					top = chest;
					topTaken = through;
					topLevel = enchantLevel;
					topMaterial = material;
				}
			}
		}

		for (EquipmentSlot slot : SLOTS) {
			dummy.setItemSlot(slot, ItemStack.EMPTY);
		}

		if (best.isEmpty()) {
			return;
		}

		boolean past = taken < topTaken - 1.0E-4;
		ItemStack shown = past ? top : best;
		int shownLevel = past ? topLevel : bestLevel;
		shownMaterial = past ? topMaterial : bestMaterial;
		Component set = Component.translatable("bloom.sheet.gauge.set." + shownMaterial);
		Component name = shownLevel > 0
				? Component.translatable("bloom.sheet.gauge.with", set, protection.value().description(), Component.translatable("enchantment.level." + shownLevel))
				: set;
		sheet.gauge = shown;
		sheet.gaugeCaption = Component.translatable(past ? "bloom.sheet.gauge.armor_past" : "bloom.sheet.gauge.armor", name,
				Sheet.num(past ? topTaken : bestTaken), Sheet.num(hit));
		sheet.gaugeBadge = past && taken > 0.0 ? Sheet.colored("×" + Sheet.num(topTaken / taken), Sheet.GOLD) : Component.empty();
	}
}
