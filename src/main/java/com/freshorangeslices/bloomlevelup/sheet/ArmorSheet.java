package com.freshorangeslices.bloomlevelup.sheet;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
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
 * them (armor, Resistance, protection enchantments, then the Armor category's own layers and Absorb). The hit is
 * picked by who deals it (a zombie, a skeleton's arrow, a creeper, a player with an iron sword, a diamond one with
 * Sharpness V, a netherite one with Sharpness V and Strength II), so the numbers read like real fights; the same
 * amount is then shown for the other kinds of hits (fire, falls, magic...), plus the chances to avoid a hit
 * altogether. The steps are vanilla's own code (LivingEntity.getDamageAfterArmorAbsorb / MagicAbsorb) and
 * {@link ArmorSkill#layers}, so the sheet matches a real hit.
 */
final class ArmorSheet {
	/** The picker, in its order (bloom.sheet.armor.attacker.<key>); the zombie is shown first. */
	private static final String[] ATTACKERS = {"zombie", "skeleton", "creeper", "iron_sword", "diamond_sword", "netherite_sword"};
	/** A skeleton's arrow up close: it leaves the bow at 1.6 with about 2.2 base damage, rounded up (before difficulty). */
	private static final float SKELETON_ARROW = 4.0F;
	/** How far the creeper is when it goes off. */
	private static final double CREEPER_DISTANCE = 2.0;
	/** Strength's attack damage per level (vanilla: +3 for each). */
	private static final float STRENGTH_PER_LEVEL = 3.0F;
	private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private ArmorSheet() {
	}

	/** One kind of incoming hit: its name (bloom.sheet.armor.kind.<key>) and a real damage source for it. */
	private record Kind(String key, DamageSource source) {
	}

	/**
	 * Who's hitting: its key (bloom.sheet.armor.attacker.<key> on the picker, .who in sentences), the item pictured
	 * for it, the hit as it lands on this difficulty and a real damage source for it (the swordsmen hold their
	 * sword, so Vibranium sees a blade).
	 */
	private record Attacker(String key, ItemStack picture, float hit, DamageSource source, boolean mob) {
		Component who() {
			return Component.translatable("bloom.sheet.armor.attacker." + key + ".who");
		}
	}

	/** The player's defences as the sheet sees them (the build shown, any point not spent yet included). */
	private record Body(int armorValue, double armor, double toughness, double rawArmor, double maxHealth, double knockback) {
		static Body of(Loadout loadout) {
			AttributeInstance armor = loadout.instance(Attributes.ARMOR);
			double raw = armor.getBaseValue();

			for (AttributeModifier modifier : armor.getModifiers()) {
				if (modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
					raw += modifier.amount();
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

		List<Component> names = new ArrayList<>();

		for (String key : ATTACKERS) {
			names.add(Component.translatable("bloom.sheet.armor.attacker." + key));
		}

		Difficulty difficulty = shownDifficulty(level);
		Attacker attacker = attacker(level, player, ATTACKERS[sheet.targets(names, 0)], difficulty);
		float hit = attacker.hit();
		LivingEntity zombie = Dummies.of(level, EntityTypes.ZOMBIE);
		List<Kind> kinds = kinds(level, player, zombie, profile, service);

		// ---- summary
		Body body = Body.of(new Loadout(player, player.getMainHandItem()));
		double armor = body.armor();
		double toughness = body.toughness();
		Steps hitSteps = steps(level, player, body, attacker.source(), hit, braced);
		sheet.summary(Sheet.plain(body.armorValue()), "armor.armor_points");

		if (toughness > 0.0) {
			sheet.summary(Sheet.plain(toughness), "armor.toughness");
		}

		sheet.summary(Sheet.plain(body.maxHealth()), "armor.max_health");
		attackerNote(sheet, attacker, difficulty);
		sheet.summary(taken(hitSteps), "armor.lost");
		sheet.summary(hitsToDown(body.maxHealth(), hitSteps.health()), "armor.hits_to_down");

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
		sheet.row(Sheet.pct(overcap), "armor.overcap");
		sheet.note("armor.overcap.note");

		// ---- breakdown: the picked hit, step by step
		Steps steps = hitSteps;
		sheet.heading("armor.h.steps", attacker.who(), Sheet.num(hit));
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
		sheet.row(hitsToDown(body.maxHealth(), steps.health()), "armor.hits_to_down");
		sheet.note("armor.hits_to_down.note");

		// ---- breakdown: other kinds of hits
		sheet.heading("armor.h.kinds", Sheet.num(hit));

		for (Kind kind : kinds) {
			Steps each = steps(level, player, body, kind.source(), hit, braced);
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

		double otherHealth = body.maxHealth() - baseHealth - vitality - protein;

		if (Math.abs(otherHealth) > 0.005) {
			sheet.row(Sheet.plus(otherHealth), "armor.other_health");
		}

		sheet.total(Sheet.plain(body.maxHealth()), "armor.max_health");

		if (player.getAbsorptionAmount() > 0.0F) {
			sheet.row(Sheet.plus(player.getAbsorptionAmount()), "armor.absorption");
		}

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

		// The picture: who's hitting, and what gets through.
		sheet.gauge = attacker.picture();
		sheet.gaugeCaption = Component.translatable("bloom.sheet.gauge.attacker", attacker.who(), Sheet.num(hit), Sheet.num(steps.health()));
	}

	/**
	 * The picked attacker. Mobs hit as they would on this difficulty (Player.hurtServer: half plus one on Easy, half
	 * again on Hard); a player's sword doesn't change with difficulty. Swords count a fully charged swing, no crit.
	 */
	private static Attacker attacker(ServerLevel level, ServerPlayer player, String key, Difficulty difficulty) {
		return switch (key) {
			case "skeleton" -> {
				LivingEntity skeleton = Dummies.of(level, EntityTypes.SKELETON);
				yield mob(key, new ItemStack(Items.SKELETON_SKULL), SKELETON_ARROW, source(level, DamageTypes.ARROW, null, skeleton), difficulty);
			}
			case "creeper" -> {
				LivingEntity creeper = Dummies.of(level, EntityTypes.CREEPER);
				yield mob(key, new ItemStack(Items.CREEPER_HEAD), (float) creeperBlast(CREEPER_DISTANCE),
						source(level, DamageTypes.EXPLOSION, creeper, creeper), difficulty);
			}
			case "iron_sword" -> sword(level, player, key, Items.IRON_SWORD, 0, 0);
			case "diamond_sword" -> sword(level, player, key, Items.DIAMOND_SWORD, 5, 0);
			case "netherite_sword" -> sword(level, player, key, Items.NETHERITE_SWORD, 5, 2);
			default -> {
				LivingEntity zombie = Dummies.of(level, EntityTypes.ZOMBIE);
				zombie.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
				yield mob("zombie", new ItemStack(Items.ZOMBIE_HEAD), (float) zombie.getAttributeValue(Attributes.ATTACK_DAMAGE),
						source(level, DamageTypes.MOB_ATTACK, zombie, zombie), difficulty);
			}
		};
	}

	private static Attacker mob(String key, ItemStack picture, float hit, DamageSource source, Difficulty difficulty) {
		return new Attacker(key, picture, source.scalesWithDifficulty() ? scaled(hit, difficulty) : hit, source, true);
	}

	/** A player's swing: 1 + the sword's attack damage + Strength, then what Sharpness adds. */
	private static Attacker sword(ServerLevel level, ServerPlayer player, String key, Item item, int sharpness, int strength) {
		ItemStack sword = new ItemStack(item);

		if (sharpness > 0) {
			sword.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS), sharpness);
		}

		// The swing comes from a stand-in holding the sword (the sheet's player can't hit itself), as a player's attack.
		LivingEntity swordsman = Dummies.of(level, EntityTypes.VINDICATOR);
		swordsman.setItemSlot(EquipmentSlot.MAINHAND, sword);
		DamageSource source = source(level, DamageTypes.PLAYER_ATTACK, swordsman, swordsman);
		float base = (float) sword.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
				.compute(Attributes.ATTACK_DAMAGE, 1.0, EquipmentSlot.MAINHAND) + strength * STRENGTH_PER_LEVEL;
		float hit = EnchantmentHelper.modifyDamage(level, sword, player, source, base);
		return new Attacker(key, sword, hit, source, false);
	}

	/** Vanilla's explosion damage for a creeper's blast (radius 3) at {@code distance} blocks, in plain sight. */
	static double creeperBlast(double distance) {
		double size = 3.0 * 2.0;
		double impact = Math.max(0.0, 1.0 - distance / size);
		return (impact * impact + impact) / 2.0 * 7.0 * size + 1.0;
	}

	/** Player.hurtServer's difficulty scaling for hits that scale with it. */
	static float scaled(float amount, Difficulty difficulty) {
		return switch (difficulty) {
			case EASY -> Math.min(amount / 2.0F + 1.0F, amount);
			case HARD -> amount * 3.0F / 2.0F;
			default -> amount;
		};
	}

	/** The world's difficulty; Normal's numbers on Peaceful, where mobs don't fight. */
	private static Difficulty shownDifficulty(ServerLevel level) {
		Difficulty difficulty = level.getDifficulty();
		return difficulty == Difficulty.PEACEFUL ? Difficulty.NORMAL : difficulty;
	}

	/** "Hit by a zombie for 3 (on Normal):" over the lines it's about. */
	private static void attackerNote(Sheet sheet, Attacker attacker, Difficulty difficulty) {
		if (attacker.mob()) {
			sheet.summaryNote("armor.attacker.note_mob", attacker.who(), Sheet.num(attacker.hit()),
					Component.translatable("options.difficulty." + difficulty.name().toLowerCase(Locale.ROOT)));
		} else {
			sheet.summaryNote("armor.attacker.note", attacker.who(), Sheet.num(attacker.hit()));
		}
	}

	/** How many such hits take you from full health to nothing ("Never" when none gets through). */
	private static Component hitsToDown(double maxHealth, double perHit) {
		if (perHit <= 1.0E-4) {
			return Sheet.word("armor.never");
		}

		return Sheet.plain(Math.max(1.0, Math.ceil(maxHealth / perHit - 1.0E-6)));
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
	private static Steps steps(ServerLevel level, ServerPlayer player, Body body, DamageSource source, float amount, boolean braced) {
		float afterArmor = amount;

		if (!source.is(DamageTypeTags.BYPASSES_ARMOR)) {
			afterArmor = CombatRules.getDamageAfterAbsorb(player, amount, source, body.armorValue(), (float) body.toughness());
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
}
