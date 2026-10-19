package com.freshorangeslices.bloomlevelup.gametest;

import java.util.List;

import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import com.freshorangeslices.bloomlevelup.data.BloomServices;
import com.freshorangeslices.bloomlevelup.effect.StatEffects;
import com.freshorangeslices.bloomlevelup.player.BloomAttachments;
import com.freshorangeslices.bloomlevelup.player.BloomProfile;
import com.freshorangeslices.bloomlevelup.progression.ActionResult;
import com.freshorangeslices.bloomlevelup.progression.ProgressionService;
import com.freshorangeslices.bloomlevelup.skill.DurabilitySaves;
import com.freshorangeslices.bloomlevelup.skill.SkillXp;
import com.freshorangeslices.bloomlevelup.skill.armor.ArmorIds;
import com.freshorangeslices.bloomlevelup.skill.armor.ArmorSkill;
import com.freshorangeslices.bloomlevelup.skill.armor.ArmorStyles;
import com.freshorangeslices.bloomlevelup.stats.StatFormulas.ArmorStyle;

/**
 * Armor mechanics on a real server: real tags, real damage sources, real attributes and items.
 * Deterministic rules are checked exactly; random rolls are checked statistically over many trials.
 */
public class ArmorGameTests {
	private static final double EPS = 1e-3;

	// ------------------------------------------------------------------ helpers

	private static ServerPlayer player(GameTestHelper helper) {
		return helper.makeMockServerPlayerInLevel();
	}

	/** Max out every category (40 Major Levels) and buy the given Majors. */
	private static void grant(ServerPlayer player, String... majors) {
		ProgressionService service = BloomServices.progression();
		BloomProfile profile = BloomAttachments.profile(player);

		for (String category : List.of(SkillXp.UTILITY, SkillXp.COMBAT, SkillXp.ARMOR, SkillXp.MISC)) {
			service.addXp(profile, category, 1.0e9);
		}

		for (String major : majors) {
			ActionResult result = service.purchaseMajor(profile, major);

			if (!result.ok()) {
				throw new IllegalStateException("Couldn't buy " + major + ": " + result.outcome());
			}
		}
	}

	private static void points(ServerPlayer player, String stat, int amount) {
		ProgressionService service = BloomServices.progression();
		BloomProfile profile = BloomAttachments.profile(player);

		if (profile.category(SkillXp.ARMOR).level() == 0) {
			grant(player);
		}

		ActionResult result = service.allocate(profile, stat, ProgressionService.Pool.NATURAL, amount);

		if (!result.ok()) {
			throw new IllegalStateException("Couldn't allocate " + stat + ": " + result.outcome());
		}
	}

	private static DamageSource source(ServerPlayer player, net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType> type) {
		return player.damageSources().source(type);
	}

	private static void near(GameTestHelper helper, double expected, double actual, String what) {
		helper.assertTrue(Math.abs(expected - actual) < EPS, what + ": expected " + expected + " but was " + actual);
	}

	private static void wear(ServerPlayer player, ItemStack head, ItemStack chest, ItemStack legs, ItemStack feet) {
		player.setItemSlot(EquipmentSlot.HEAD, head);
		player.setItemSlot(EquipmentSlot.CHEST, chest);
		player.setItemSlot(EquipmentSlot.LEGS, legs);
		player.setItemSlot(EquipmentSlot.FEET, feet);
	}

	// ------------------------------------------------------------------ stats

	@GameTest
	public void vitalityAndDefenseBecomeAttributes(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		points(player, ArmorIds.VITALITY, 50);
		points(player, ArmorIds.DEFENSE, 50);
		StatEffects.refresh(player);
		near(helper, 30.0, player.getMaxHealth(), "50 Vitality = +5 hearts");
		near(helper, 5.0, player.getAttributeValue(Attributes.ARMOR), "50 Defense = +5 armor");
		helper.succeed();
	}

	@GameTest
	public void toggledOffStatIsFullyOff(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		points(player, ArmorIds.VITALITY, 100);
		BloomServices.progression().toggleStat(BloomAttachments.profile(player), ArmorIds.VITALITY);
		StatEffects.refresh(player);
		near(helper, 20.0, player.getMaxHealth(), "Vitality switched off");
		helper.succeed();
	}

	@GameTest
	public void durabilityBackupSavesAboutThirtyPercent(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		points(player, ArmorIds.DURABILITY, 100);
		int kept = DurabilitySaves.adjust(new ItemStack(Items.IRON_CHESTPLATE), 2000, player);
		// 30% saved on average: 1400 kept, sd ~ 20
		helper.assertTrue(kept > 1300 && kept < 1500, "Durability 100 should keep ~1400 of 2000 damage, kept " + kept);
		int untouched = DurabilitySaves.adjust(new ItemStack(Items.DIAMOND), 50, player);
		helper.assertValueEqual(50, untouched, "Items that aren't armor/shields/tools are untouched");
		helper.succeed();
	}

	@GameTest
	public void dodgeAvoidsAboutThirtyPercent(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		points(player, ArmorIds.DODGE, 100);
		DamageSource hit = source(player, DamageTypes.MOB_ATTACK);
		int dodged = 0;

		for (int i = 0; i < 2000; i++) {
			if (!ArmorSkill.allowDamage(player, hit, 2.0F)) {
				dodged++;
			}
		}

		helper.assertTrue(dodged > 500 && dodged < 700, "100 Dodge should avoid ~600 of 2000 hits, avoided " + dodged);

		int fallDodged = 0;

		for (int i = 0; i < 200; i++) {
			if (!ArmorSkill.allowDamage(player, source(player, DamageTypes.FALL), 2.0F)) {
				fallDodged++;
			}
		}

		helper.assertValueEqual(0, fallDodged, "Dodge never avoids fall damage");
		helper.succeed();
	}

	// ------------------------------------------------------------------ layers

	@GameTest
	public void layersMultiplyNotAdd(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		grant(player, ArmorIds.IRON_SKIN, ArmorIds.ELEMENTAL_SKIN);
		near(helper, 4.0, ArmorSkill.modifyFinalDamage(player, source(player, DamageTypes.ON_FIRE), 10.0F),
				"Fire: 10 x 0.8 (Iron Skin) x 0.5 (Elemental Skin)");
		near(helper, 8.0, ArmorSkill.modifyFinalDamage(player, source(player, DamageTypes.GENERIC), 10.0F),
				"Generic: only Iron Skin");
		near(helper, 10.0, ArmorSkill.modifyFinalDamage(player, source(player, DamageTypes.FELL_OUT_OF_WORLD), 10.0F),
				"The void ignores Resistance-style layers");
		helper.succeed();
	}

	@GameTest
	public void fittingArmorHeavy(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		grant(player, ArmorIds.FITTING_ARMOR);
		wear(player, new ItemStack(Items.DIAMOND_HELMET), new ItemStack(Items.DIAMOND_CHESTPLATE),
				new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(Items.DIAMOND_BOOTS));
		helper.assertTrue(ArmorStyles.styleOf(player) == ArmorStyle.HEAVY, "Full diamond is Heavy");
		near(helper, 7.0, ArmorSkill.modifyFinalDamage(player, source(player, DamageTypes.GENERIC), 10.0F), "Heavy: -30%");
		StatEffects.refresh(player);
		near(helper, 0.5, player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), "Heavy: 50% knockback resistance");

		player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
		helper.assertTrue(ArmorStyles.styleOf(player) == ArmorStyle.NONE, "An empty slot means no style");
		helper.succeed();
	}

	@GameTest
	public void fittingArmorMixedIsMedium(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		wear(player, new ItemStack(Items.DIAMOND_HELMET), new ItemStack(Items.DIAMOND_CHESTPLATE),
				new ItemStack(Items.LEATHER_LEGGINGS), new ItemStack(Items.LEATHER_BOOTS));
		helper.assertTrue(ArmorStyles.styleOf(player) == ArmorStyle.MEDIUM, "2 diamond + 2 leather averages to Medium");
		helper.succeed();
	}

	@GameTest
	public void absorbMovesHalfIntoArmor(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		grant(player, ArmorIds.ABSORB);
		ItemStack chest = new ItemStack(Items.IRON_CHESTPLATE);
		player.setItemSlot(EquipmentSlot.CHEST, chest);
		near(helper, 5.0, ArmorSkill.modifyFinalDamage(player, source(player, DamageTypes.GENERIC), 10.0F), "Player takes half");
		helper.assertValueEqual(5, player.getItemBySlot(EquipmentSlot.CHEST).getDamageValue(), "Armor took the other half");

		ServerPlayer naked = player(helper);
		grant(naked, ArmorIds.ABSORB);
		near(helper, 10.0, ArmorSkill.modifyFinalDamage(naked, source(naked, DamageTypes.GENERIC), 10.0F), "No armor: Absorb does nothing");
		helper.succeed();
	}

	// ------------------------------------------------------------------ Majors

	@GameTest
	public void titanHideNeedsEmptyArmorSlots(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		grant(player, ArmorIds.TITAN_HIDE);
		StatEffects.refresh(player);
		near(helper, 4.0, player.getAttributeValue(Attributes.ARMOR), "(0 Defense + 2) x 2");
		near(helper, 0.3, player.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER), "-70% fall damage");

		player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		StatEffects.refresh(player);
		// (The helmet's own armor point only lands on the next entity tick, so this is Titan Hide alone.)
		helper.assertTrue(player.getAttributeValue(Attributes.ARMOR) < 2.0, "Any worn armor turns Titan Hide off");
		near(helper, 1.0, player.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER), "...including its fall protection");
		helper.succeed();
	}

	@GameTest
	public void lastBreathSavesTheFirstLethalHit(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		grant(player, ArmorIds.LAST_BREATH, ArmorIds.CASTLE_OF_STONE);
		boolean died = ArmorSkill.allowDeath(player, source(player, DamageTypes.GENERIC), 100.0F);
		helper.assertFalse(died, "First lethal hit in the window is always survived");
		near(helper, 10.0, player.getHealth(), "Back to 5 hearts");
		helper.assertValueEqual(1L, BloomAttachments.profile(player).counter(ArmorIds.LAST_BREATH_ATTEMPTS), "Ladder advanced");

		// Castle of Stone triggered: the next hits are ignored, except damage that bypasses invulnerability.
		helper.assertFalse(ArmorSkill.allowDamage(player, source(player, DamageTypes.GENERIC), 5.0F), "Castle of Stone blocks damage");
		helper.assertTrue(ArmorSkill.allowDamage(player, source(player, DamageTypes.FELL_OUT_OF_WORLD), 5.0F), "...but not the void");
		helper.assertValueEqual(1L, BloomAttachments.profile(player).counter(ArmorIds.CASTLE_SPENT), "Castle needs rest to recharge");
		helper.succeed();
	}

	@GameTest
	public void thickBloodHalvesPoison(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		grant(player, ArmorIds.THICK_BLOOD);
		player.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
		MobEffectInstance poison = player.getEffect(MobEffects.POISON);
		helper.assertTrue(poison != null && poison.getDuration() == 100, "Poison lasts half as long, was "
				+ (poison == null ? "missing" : poison.getDuration()));
		helper.succeed();
	}

	@GameTest
	public void unyieldingRejectsAboutHalf(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		grant(player, ArmorIds.UNYIELDING);
		int landed = 0;

		for (int i = 0; i < 400; i++) {
			player.removeEffect(MobEffects.SLOWNESS);

			if (player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100))) {
				landed++;
			}
		}

		helper.assertTrue(landed > 140 && landed < 260, "About half of 400 Slowness applications should land, landed " + landed);
		helper.succeed();
	}

	@GameTest
	public void proteinCountsMeatAndGrowsHealth(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		grant(player, ArmorIds.PROTEIN);
		ItemStack beef = new ItemStack(Items.COOKED_BEEF, 64);
		Consumable consumable = beef.get(DataComponents.CONSUMABLE);
		helper.assertTrue(consumable != null, "Cooked beef is consumable");

		for (int i = 0; i < 40; i++) {
			consumable.onConsume(helper.getLevel(), player, beef);
		}

		ItemStack bread = new ItemStack(Items.BREAD, 64);
		bread.get(DataComponents.CONSUMABLE).onConsume(helper.getLevel(), player, bread);
		helper.assertValueEqual(40L, BloomAttachments.profile(player).counter(ArmorIds.PROTEIN_MEALS), "Only meat counts");
		StatEffects.refresh(player);
		near(helper, 22.0, player.getMaxHealth(), "40 meals = +1 heart");

		BloomServices.progression().refundMajor(BloomAttachments.profile(player), ArmorIds.PROTEIN);
		StatEffects.refresh(player);
		helper.assertValueEqual(0L, BloomAttachments.profile(player).counter(ArmorIds.PROTEIN_MEALS), "Refund resets Protein!");
		near(helper, 20.0, player.getMaxHealth(), "...and its hearts");
		helper.succeed();
	}

	@GameTest
	public void parkourIgnoresAboutAThirdOfFalls(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		grant(player, ArmorIds.PARKOUR);
		int negated = 0;

		for (int i = 0; i < 1000; i++) {
			if (!ArmorSkill.allowDamage(player, source(player, DamageTypes.FALL), 4.0F)) {
				negated++;
			}
		}

		helper.assertTrue(negated > 280 && negated < 420, "Parkour should negate ~350 of 1000 falls, negated " + negated);
		helper.succeed();
	}
}
