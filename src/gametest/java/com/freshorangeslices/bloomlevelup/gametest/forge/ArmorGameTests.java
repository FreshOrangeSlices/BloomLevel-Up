package com.freshorangeslices.bloomlevelup.gametest.forge;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.minecraft.gametest.framework.GameTest;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;

import com.freshorangeslices.bloomlevelup.data.BloomServices;
import com.freshorangeslices.bloomlevelup.effect.StatEffects;
import com.freshorangeslices.bloomlevelup.player.BloomAttachments;
import com.freshorangeslices.bloomlevelup.player.BloomProfile;
import com.freshorangeslices.bloomlevelup.progression.ActionResult;
import com.freshorangeslices.bloomlevelup.progression.ProgressionService;
import com.freshorangeslices.bloomlevelup.skill.ConsumeContext;
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
@GameTestHolder(BloomLevelUp.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ArmorGameTests {
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

	@GameTest(template = "empty")
	public static void reflectCatchesArrowsThatCantBePickedUp(GameTestHelper helper) {
		net.minecraft.server.level.ServerLevel level = helper.getLevel();
		net.minecraft.world.entity.projectile.AbstractArrow skeletonArrow =
				net.minecraft.world.entity.EntityType.ARROW.create(level);
		skeletonArrow.pickup = net.minecraft.world.entity.projectile.AbstractArrow.Pickup.DISALLOWED;
		helper.assertTrue(ArmorSkill.catchable(skeletonArrow) != null, "A skeleton's arrow can be caught");

		net.minecraft.world.entity.projectile.AbstractArrow infinity =
				net.minecraft.world.entity.EntityType.ARROW.create(level);
		infinity.pickup = net.minecraft.world.entity.projectile.AbstractArrow.Pickup.CREATIVE_ONLY;
		helper.assertTrue(ArmorSkill.catchable(infinity) == null, "Infinity / creative arrows aren't duplicated");

		net.minecraft.world.entity.projectile.AbstractArrow drowned =
				net.minecraft.world.entity.EntityType.TRIDENT.create(level);
		drowned.pickup = net.minecraft.world.entity.projectile.AbstractArrow.Pickup.DISALLOWED;
		helper.assertTrue(ArmorSkill.catchable(drowned) == null, "A drowned's trident isn't farmable");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void vitalityAndDefenseBecomeAttributes(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		points(player, ArmorIds.VITALITY, 50);
		points(player, ArmorIds.DEFENSE, 50);
		StatEffects.refresh(player);
		near(helper, 30.0, player.getMaxHealth(), "50 Vitality = +5 hearts");
		near(helper, 5.0, player.getAttributeValue(Attributes.ARMOR), "50 Defense = +5 armor");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void toggledOffStatIsFullyOff(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		points(player, ArmorIds.VITALITY, 100);
		BloomServices.progression().toggleStat(BloomAttachments.profile(player), ArmorIds.VITALITY);
		StatEffects.refresh(player);
		near(helper, 20.0, player.getMaxHealth(), "Vitality switched off");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void durabilityBackupSavesAboutThirtyPercent(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		points(player, ArmorIds.DURABILITY, 100);
		int kept = DurabilitySaves.adjust(new ItemStack(Items.IRON_CHESTPLATE), 2000, player);
		// 30% saved on average: 1400 kept, sd ~ 20
		helper.assertTrue(kept > 1300 && kept < 1500, "Durability 100 should keep ~1400 of 2000 damage, kept " + kept);
		int untouched = DurabilitySaves.adjust(new ItemStack(Items.DIAMOND), 50, player);
		helper.assertValueEqual(50, untouched, "Items that aren't armor/shields/tools are untouched");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void dodgeAvoidsAboutThirtyPercent(GameTestHelper helper) {
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

	@GameTest(template = "empty")
	public static void layersMultiplyNotAdd(GameTestHelper helper) {
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

	@GameTest(template = "empty")
	public static void fittingArmorHeavy(GameTestHelper helper) {
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

	@GameTest(template = "empty")
	public static void fittingArmorMixedIsMedium(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		wear(player, new ItemStack(Items.DIAMOND_HELMET), new ItemStack(Items.DIAMOND_CHESTPLATE),
				new ItemStack(Items.LEATHER_LEGGINGS), new ItemStack(Items.LEATHER_BOOTS));
		helper.assertTrue(ArmorStyles.styleOf(player) == ArmorStyle.MEDIUM, "2 diamond + 2 leather averages to Medium");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void fittingArmorMediumHalvesShieldDisable(GameTestHelper helper) {
		for (boolean medium : new boolean[] {false, true}) {
			ServerPlayer player = player(helper);
			grant(player, ArmorIds.FITTING_ARMOR);

			if (medium) {
				wear(player, new ItemStack(Items.IRON_HELMET), new ItemStack(Items.IRON_CHESTPLATE),
						new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.IRON_BOOTS));
			}

			ItemStack shield = new ItemStack(Items.SHIELD);
			player.setItemInHand(InteractionHand.OFF_HAND, shield);
			player.startUsingItem(InteractionHand.OFF_HAND);
			player.disableShield(true); // what an axe hit does to a raised shield
			helper.assertTrue(player.getCooldowns().isOnCooldown(Items.SHIELD), "An axe hit disables the shield");

			for (int tick = 0; tick < 40; tick++) {
				player.getCooldowns().tick();
			}

			// A 5 s disable is 100 ticks; Medium cuts it to 30 (1.5 s), so after 40 ticks only the plain shield is still out.
			helper.assertValueEqual(!medium, player.getCooldowns().isOnCooldown(Items.SHIELD),
					medium ? "Medium: back after 1.5 s" : "Without Medium: still disabled after 2 s");
		}

		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void absorbMovesHalfIntoArmor(GameTestHelper helper) {
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

	@GameTest(template = "empty")
	public static void titanHideNeedsEmptyArmorSlots(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		grant(player, ArmorIds.TITAN_HIDE);
		StatEffects.refresh(player);
		near(helper, 4.0, player.getAttributeValue(Attributes.ARMOR), "(0 Defense + 2) x 2");
		near(helper, 0.3, net.minecraftforge.common.ForgeHooks.onLivingFall(player, 10.0F, 1.0F)[1], "-70% fall damage");

		player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		StatEffects.refresh(player);
		// (The helmet's own armor point only lands on the next entity tick, so this is Titan Hide alone.)
		helper.assertTrue(player.getAttributeValue(Attributes.ARMOR) < 2.0, "Any worn armor turns Titan Hide off");
		near(helper, 1.0, net.minecraftforge.common.ForgeHooks.onLivingFall(player, 10.0F, 1.0F)[1], "...including its fall protection");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void lastBreathSavesTheFirstLethalHit(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		grant(player, ArmorIds.LAST_BREATH, ArmorIds.CASTLE_OF_STONE);
		boolean died = ArmorSkill.allowDeath(player, source(player, DamageTypes.GENERIC));
		helper.assertFalse(died, "First lethal hit in the window is always survived");
		near(helper, 10.0, player.getHealth(), "Back to 5 hearts");
		helper.assertValueEqual(1L, BloomAttachments.profile(player).counter(ArmorIds.LAST_BREATH_ATTEMPTS), "Ladder advanced");

		// Castle of Stone triggered: the next hits are ignored, except damage that bypasses invulnerability.
		helper.assertFalse(ArmorSkill.allowDamage(player, source(player, DamageTypes.GENERIC), 5.0F), "Castle of Stone blocks damage");
		helper.assertTrue(ArmorSkill.allowDamage(player, source(player, DamageTypes.FELL_OUT_OF_WORLD), 5.0F), "...but not the void");
		helper.assertValueEqual(1L, BloomAttachments.profile(player).counter(ArmorIds.CASTLE_SPENT), "Castle needs rest to recharge");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void thickBloodHalvesPoison(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		grant(player, ArmorIds.THICK_BLOOD);
		player.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
		MobEffectInstance poison = player.getEffect(MobEffects.POISON);
		helper.assertTrue(poison != null && poison.getDuration() == 100, "Poison lasts half as long, was "
				+ (poison == null ? "missing" : poison.getDuration()));
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void unyieldingRejectsAboutHalf(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		grant(player, ArmorIds.UNYIELDING);
		int landed = 0;

		for (int i = 0; i < 400; i++) {
			player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);

			if (player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100))) {
				landed++;
			}
		}

		helper.assertTrue(landed > 140 && landed < 260, "About half of 400 Slowness applications should land, landed " + landed);
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void proteinCountsMeatAndGrowsHealth(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		grant(player, ArmorIds.PROTEIN);
		// What finishing a meal does (the completeUsingItem hook brackets it like this).
		ItemStack beef = new ItemStack(Items.COOKED_BEEF, 64);

		for (int i = 0; i < 40; i++) {
			ConsumeContext.begin(player, beef, false);
			player.eat(helper.getLevel(), beef);
			ConsumeContext.end(player);
		}

		ItemStack bread = new ItemStack(Items.BREAD, 64);
		ConsumeContext.begin(player, bread, false);
		player.eat(helper.getLevel(), bread);
		ConsumeContext.end(player);
		helper.assertValueEqual(40L, BloomAttachments.profile(player).counter(ArmorIds.PROTEIN_MEALS), "Only meat counts");
		StatEffects.refresh(player);
		near(helper, 22.0, player.getMaxHealth(), "40 meals = +1 heart");

		BloomServices.progression().refundMajor(BloomAttachments.profile(player), ArmorIds.PROTEIN);
		StatEffects.refresh(player);
		helper.assertValueEqual(0L, BloomAttachments.profile(player).counter(ArmorIds.PROTEIN_MEALS), "Refund resets Protein!");
		near(helper, 20.0, player.getMaxHealth(), "...and its hearts");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void proteinKeepsItsMealsWhileTheOtherBuildOwnsIt(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		grant(player, ArmorIds.PROTEIN);
		BloomProfile profile = BloomAttachments.profile(player);
		profile.setCounter(ArmorIds.PROTEIN_MEALS, 40);
		ProgressionService service = BloomServices.progression();
		long now = helper.getLevel().getGameTime();

		helper.assertTrue(service.switchBuild(profile, now).ok(), "Switch to build 2 (a copy)");
		helper.assertTrue(service.resetCategoryMajors(profile, "bloomlevelup:armor", now).ok(), "Build 2 drops its Armor Majors");
		StatEffects.refresh(player);
		helper.assertValueEqual(40L, profile.counter(ArmorIds.PROTEIN_MEALS), "Build 1 still owns Protein!, so the meals stay");
		near(helper, 20.0, player.getMaxHealth(), "...but only the build in use gets its heart");

		helper.assertTrue(service.switchBuild(profile, now + 100000).ok(), "Back to build 1");
		StatEffects.refresh(player);
		near(helper, 22.0, player.getMaxHealth(), "Build 1 has its +1 heart again");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void parkourIgnoresAboutAThirdOfFalls(GameTestHelper helper) {
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

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void braceTakesHoldAtOnceThenCoolsDown(GameTestHelper helper) {
		var config = BloomServices.config();
		int cooldown = config.braceCooldownTicks;
		config.braceCooldownTicks = 30; // 1.5 s instead of 10, to keep the test short
		int delay = config.braceDelayTicks;
		// 1.20.1 servers see crouching, and the client reports its movement keys (BloomPayloads.MovementInput).
		ServerPlayer player = player(helper);
		grant(player, ArmorIds.BRACE);
		player.setOnGround(true);
		player.setShiftKeyDown(true);
		ArmorSkill.movementInput(player, false);

		// A second player who raises a shield instead of crouching.
		ServerPlayer shield = player(helper);
		grant(shield, ArmorIds.BRACE);
		shield.setOnGround(true);
		shield.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
		shield.startUsingItem(InteractionHand.OFF_HAND);

		helper.runAfterDelay(delay + 2, () -> {
			helper.assertTrue(ArmorSkill.isBraced(player), "Crouched still: braced");
			near(helper, 6.0, ArmorSkill.modifyFinalDamage(player, source(player, DamageTypes.GENERIC), 10.0F), "Braced: Resistance II (-40%)");
			helper.assertTrue(ArmorSkill.isBraced(shield), "A raised shield braces too");
			shield.stopUsingItem();
			player.setOnGround(false); // knocked into the air
		});

		helper.runAfterDelay(delay + 4, () -> {
			helper.assertTrue(ArmorSkill.isBraced(player), "Knockback doesn't end Brace!");
			helper.assertFalse(ArmorSkill.isBraced(shield), "Lowering the shield ends it");
			player.setOnGround(true);
			ArmorSkill.movementInput(player, true); // hops while crouched
		});

		helper.runAfterDelay(delay + 6, () -> {
			helper.assertFalse(ArmorSkill.isBraced(player), "Jumping ends it");
			ArmorSkill.movementInput(player, false); // lands, still crouched
		});

		helper.runAfterDelay(delay + 12, () -> {
			helper.assertFalse(ArmorSkill.isBraced(player), "Landing crouched doesn't brace again during the cooldown");
			near(helper, 10.0, ArmorSkill.modifyFinalDamage(player, source(player, DamageTypes.GENERIC), 10.0F), "Full damage meanwhile");
		});

		helper.runAfterDelay(delay + 6 + 30 + 4, () -> {
			config.braceCooldownTicks = cooldown;
			helper.assertTrue(ArmorSkill.isBraced(player), "After the cooldown, crouching still braces again");
			helper.succeed();
		});
	}

	@GameTest(template = "empty")
	public static void vibraniumCutsBladeHits(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		grant(player, ArmorIds.VIBRANIUM);
		LivingEntity swordsman = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
		swordsman.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		LivingEntity axeman = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 1));
		axeman.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
		LivingEntity fists = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 2, 1));

		near(helper, 7.0, ArmorSkill.modifyFinalDamage(player, player.damageSources().mobAttack(swordsman), 10.0F), "Vibranium: a sword is -30%");
		near(helper, 7.0, ArmorSkill.modifyFinalDamage(player, player.damageSources().mobAttack(axeman), 10.0F), "Vibranium: an axe is a blade too");
		near(helper, 10.0, ArmorSkill.modifyFinalDamage(player, player.damageSources().mobAttack(fists), 10.0F), "Bare fists are neither");
		near(helper, 10.0, ArmorSkill.modifyFinalDamage(player, source(player, DamageTypes.GENERIC), 10.0F), "Nor is anything that isn't a hit");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void reboundThrowsAboutOneMeleeHitInSevenBack(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		grant(player, ArmorIds.REBOUND);
		int rebounds = 0;

		// A fresh zombie per hit: one that was just hurt shrugs off the next hit (vanilla damage cooldown).
		for (int i = 0; i < 300; i++) {
			LivingEntity zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
			ArmorSkill.afterDamage(player, player.damageSources().mobAttack(zombie), 8.0F, 4.0F, false);

			if (zombie.getHealth() < zombie.getMaxHealth()) {
				rebounds++;
			}

			zombie.discard();
		}

		// 15% of 300 = 45 (sd ~6).
		helper.assertTrue(rebounds > 22 && rebounds < 70, "Rebound should hit back ~45 of 300 melee hits, hit back " + rebounds);

		int fromArrows = 0;

		for (int i = 0; i < 60; i++) {
			LivingEntity zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
			AbstractArrow arrow = EntityType.ARROW.create(helper.getLevel());
			ArmorSkill.afterDamage(player, player.damageSources().arrow(arrow, zombie), 8.0F, 4.0F, false);

			if (zombie.getHealth() < zombie.getMaxHealth()) {
				fromArrows++;
			}

			zombie.discard();
		}

		helper.assertValueEqual(0, fromArrows, "Arrows are never thrown back");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void reflectBouncesOrCatchesAboutAThirdOfArrows(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		grant(player, ArmorIds.REFLECT);
		int stopped = 0;

		for (int i = 0; i < 1000; i++) {
			AbstractArrow arrow = EntityType.ARROW.create(helper.getLevel());
			arrow.pickup = AbstractArrow.Pickup.DISALLOWED; // a skeleton's

			if (!ArmorSkill.allowDamage(player, player.damageSources().arrow(arrow, null), 3.0F)) {
				stopped++;
			}
		}

		int caught = 0;

		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);

			if (stack.is(Items.ARROW)) {
				caught += stack.getCount();
			}
		}

		// 10% caught, then 25% of the rest bounce: 32.5% stopped (sd ~15), 10% caught (sd ~9.5).
		helper.assertTrue(stopped > 270 && stopped < 380, "Reflect should stop ~325 of 1000 arrows, stopped " + stopped);
		helper.assertTrue(caught > 60 && caught < 140, "~100 of them land in the inventory, caught " + caught);
		helper.assertTrue(ArmorSkill.allowDamage(player, source(player, DamageTypes.GENERIC), 3.0F), "Reflect only touches projectiles");
		helper.succeed();
	}
}
