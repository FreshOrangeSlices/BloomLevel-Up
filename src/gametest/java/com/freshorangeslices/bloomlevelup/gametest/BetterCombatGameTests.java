package com.freshorangeslices.bloomlevelup.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;

import com.freshorangeslices.bloomlevelup.compat.BetterCombat;
import com.freshorangeslices.bloomlevelup.config.BloomConfig;
import com.freshorangeslices.bloomlevelup.data.BloomServices;
import com.freshorangeslices.bloomlevelup.effect.StatEffects;
import com.freshorangeslices.bloomlevelup.network.SheetPayloads;
import com.freshorangeslices.bloomlevelup.player.BloomAttachments;
import com.freshorangeslices.bloomlevelup.player.BloomProfile;
import com.freshorangeslices.bloomlevelup.progression.ActionResult;
import com.freshorangeslices.bloomlevelup.progression.ProgressionService;
import com.freshorangeslices.bloomlevelup.sheet.StatSheets;
import com.freshorangeslices.bloomlevelup.skill.SkillXp;
import com.freshorangeslices.bloomlevelup.skill.combat.CombatAbilities;
import com.freshorangeslices.bloomlevelup.skill.combat.CombatIds;
import com.freshorangeslices.bloomlevelup.skill.combat.CombatSkill;
import com.freshorangeslices.bloomlevelup.stats.StatFormulas;

/**
 * Bloom with Better Combat. CI runs the game tests twice: without Better Combat (these check that nothing of it
 * shows) and with it loaded (runGameTestBetterCombat: these drive its real server handler, the way its client does
 * after a swing's wind-up, and check Bloom's rules on top).
 */
public class BetterCombatGameTests {
	/** Health is compared after armor (the zombies' 2 points cut every hit by the same share), so a little slack. */
	private static final double EPS = 0.02;

	private static ServerPlayer player(GameTestHelper helper, ItemStack weapon, String... majors) {
		ServerPlayer player = FakePlayer.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "bloom-bc"));
		BlockPos at = helper.absolutePos(new BlockPos(4, 1, 4));
		player.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		player.setItemInHand(InteractionHand.MAIN_HAND, weapon);
		player.setOnGround(true); // Batter Up! only sweeps from the ground
		// Fake players never tick, so the weapon's own attack modifiers are put on by hand.
		weapon.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
			if (player.getAttribute(attribute) != null) {
				player.getAttribute(attribute).addOrUpdateTransientModifier(modifier);
			}
		});
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

		StatEffects.refresh(player);
		return player;
	}

	private static List<LivingEntity> zombies(GameTestHelper helper, int count, int row) {
		List<LivingEntity> out = new ArrayList<>();

		for (int i = 0; i < count; i++) {
			// Spawned as is (no random baby or gear: that comes with natural spawning).
			out.add(helper.spawn(EntityTypes.ZOMBIE, new BlockPos(2 + i, 2, row)));
		}

		return out;
	}

	private static double lost(LivingEntity mob) {
		return mob.getMaxHealth() - mob.getHealth();
	}

	private static void near(GameTestHelper helper, double expected, double actual, String what) {
		helper.assertTrue(Math.abs(expected - actual) < EPS, what + ": expected " + expected + " but was " + actual);
	}

	private static boolean hasLine(SheetPayloads.View view, String key) {
		return view.summary().stream().anyMatch(row -> row.label().getContents() instanceof TranslatableContents contents
				&& contents.getKey().equals("bloom.sheet." + key));
	}

	@GameTest
	public void onlyWithBetterCombatInstalled(GameTestHelper helper) {
		ServerPlayer player = player(helper, new ItemStack(Items.DIAMOND_SWORD), CombatIds.FULL_SWEEP);
		SheetPayloads.View sheet = StatSheets.build(player, new SheetPayloads.Request(1, SkillXp.COMBAT, -1, 0, List.of()));

		if (BetterCombat.installed()) {
			helper.assertTrue(hasLine(sheet, "combat.bc.crowd_summary") && !hasLine(sheet, "combat.sweep"),
					"With Better Combat the sheet shows its crowd rule instead of the vanilla sweep: " + sheet.summary());
			helper.assertFalse(BetterCombat.swings(new ItemStack(Items.DIAMOND_SWORD)).isEmpty(), "A diamond sword has a Better Combat combo");
		} else {
			helper.assertTrue(hasLine(sheet, "combat.sweep") && !hasLine(sheet, "combat.bc.crowd_summary"),
					"Without Better Combat the sheet is as it was: " + sheet.summary());
			helper.assertFalse(BetterCombat.inSwing(player), "No Better Combat swing without Better Combat");
			helper.assertTrue(BetterCombat.swings(new ItemStack(Items.DIAMOND_SWORD)).isEmpty(), "No combo without Better Combat");
		}

		helper.succeed();
	}

	@GameTest
	public void fullSweepCancelsTheCrowdPenalty(GameTestHelper helper) {
		if (!BetterCombat.installed()) {
			helper.succeed();
			return;
		}

		BetterCombat.CrowdRule rule = BetterCombat.crowdRule();
		ServerPlayer plain = player(helper, new ItemStack(Items.DIAMOND_SWORD));
		List<LivingEntity> one = zombies(helper, 1, 1);
		BetterCombatSwings.swing(plain, one);
		double single = lost(one.get(0));
		helper.assertTrue(single > 1.0, "The swing landed (" + single + ")");

		// Three mobs at once, no Full Sweep: Better Combat's rule weakens the whole swing.
		List<LivingEntity> three = zombies(helper, 3, 3);
		BetterCombatSwings.swing(plain, three);
		double weaker = single * (1.0 + rule.term(3, 0.0));

		for (LivingEntity zombie : three) {
			near(helper, weaker, lost(zombie), "Each of 3 mobs without Full Sweep");
		}

		// With Full Sweep: every mob takes the whole hit (no penalty, and no bonus from Full Sweep's own sweep ratio).
		ServerPlayer sweeper = player(helper, new ItemStack(Items.DIAMOND_SWORD), CombatIds.FULL_SWEEP);
		List<LivingEntity> others = zombies(helper, 3, 5);
		BetterCombatSwings.swing(sweeper, others);

		for (LivingEntity zombie : others) {
			near(helper, single, lost(zombie), "Each of 3 mobs with Full Sweep");
		}

		BetterCombat.clearForTests(sweeper);
		helper.assertTrue(sweeper.getAttribute(Attributes.ATTACK_DAMAGE).getModifier(BetterCombat.CROWD_MOD) == null,
				"The counter doesn't outlive the swing");
		helper.succeed();
	}

	@GameTest
	public void batterUpReplacesItsSweepWithNoPenalty(GameTestHelper helper) {
		if (!BetterCombat.installed()) {
			helper.succeed();
			return;
		}

		ServerPlayer player = player(helper, new ItemStack(Items.IRON_AXE), CombatIds.FULL_SWEEP, CombatIds.BATTER_UP);
		List<LivingEntity> one = zombies(helper, 1, 1);
		BetterCombatSwings.swing(player, one);
		double single = lost(one.get(0));

		// Two mobs in the swing, a third right beside them that Better Combat didn't target.
		List<LivingEntity> row = zombies(helper, 3, 3);
		BetterCombatSwings.swing(player, row.subList(0, 2));
		CombatSkill.runQueuedForTests(); // Bloom's own sweep would be queued for the end of the tick
		near(helper, single, lost(row.get(0)), "First mob of two (Batter Up!: no crowd penalty)");
		near(helper, single, lost(row.get(1)), "Second mob of two");
		helper.assertTrue(lost(row.get(2)) < 1.0E-4, "Bloom's own Batter Up! sweep doesn't add hits under Better Combat (lost "
				+ lost(row.get(2)) + ")");
		helper.succeed();
	}

	/**
	 * Batter Up! gives maces the sword's combo and spears (held in two hands) the claymore's; their own speed stays.
	 * Without it they keep their own swings, and a falling mace keeps its slam.
	 */
	@GameTest
	public void batterUpSwingsMacesAndSpearsLikeSwords(GameTestHelper helper) {
		if (!BetterCombat.installed()) {
			helper.assertTrue(BetterCombat.attackAnimation(player(helper, new ItemStack(Items.MACE)), 0) == null, "No swings without Better Combat");
			helper.succeed();
			return;
		}

		List<String> sword = combo(player(helper, new ItemStack(Items.DIAMOND_SWORD)));
		String maceOwn = BetterCombat.attackAnimation(player(helper, new ItemStack(Items.MACE), CombatIds.FULL_SWEEP), 0);
		ServerPlayer batter = player(helper, new ItemStack(Items.MACE), CombatIds.FULL_SWEEP, CombatIds.BATTER_UP);
		helper.assertTrue(maceOwn != null && !sword.contains(maceOwn), "A mace has its own swing: " + maceOwn + " vs " + sword);
		helper.assertTrue(combo(batter).equals(sword), "Batter Up!: the mace swings the sword's combo " + sword + ", got " + combo(batter));

		batter.setOnGround(false);
		batter.fallDistance = 3;
		helper.assertTrue(maceOwn.equals(BetterCombat.attackAnimation(batter, 0)), "Falling, the mace keeps its own slam (the smash)");
		batter.setOnGround(true);
		batter.fallDistance = 0;

		String spearOwn = BetterCombat.attackAnimation(player(helper, new ItemStack(Items.IRON_SPEAR), CombatIds.FULL_SWEEP), 0);
		String spearBatter = BetterCombat.attackAnimation(player(helper, new ItemStack(Items.IRON_SPEAR), CombatIds.FULL_SWEEP, CombatIds.BATTER_UP), 0);

		if (spearOwn != null) {
			helper.assertTrue(!spearOwn.equals(spearBatter) && spearBatter != null && spearBatter.startsWith("bettercombat:two_handed_slash"),
					"Batter Up!: a two-handed spear swings the claymore's combo (" + spearOwn + " -> " + spearBatter + ")");
		}

		helper.assertTrue(BetterCombat.swings(batter, batter.getMainHandItem()).size() == sword.size(),
				"The stat sheet shows the mace's sword combo too");
		helper.succeed();
	}

	private static List<String> combo(ServerPlayer player) {
		List<String> out = new ArrayList<>();

		for (int i = 0; i < 3; i++) {
			out.add(BetterCombat.attackAnimation(player, i));
		}

		return out;
	}

	@GameTest
	public void predatorBuildsOnAnyMob(GameTestHelper helper) {
		if (!BetterCombat.installed()) {
			helper.succeed();
			return;
		}

		BloomConfig config = BloomServices.config();
		ServerPlayer player = player(helper, new ItemStack(Items.IRON_SWORD), CombatIds.PREDATOR);
		List<LivingEntity> mobs = zombies(helper, 2, 1);
		BetterCombatSwings.swing(player, mobs.subList(0, 1));
		BetterCombatSwings.swing(player, mobs.subList(1, 2));
		double first = lost(mobs.get(0));
		double expected = first * StatFormulas.stackMultiplier(1, config.predatorPerStack, config.predatorMaxStacks);
		near(helper, expected, lost(mobs.get(1)), "A different mob still gets the stack from the first");
		helper.succeed();
	}

	@GameTest
	public void chargeLandsThroughBetterCombat(GameTestHelper helper) {
		if (!BetterCombat.installed()) {
			helper.succeed();
			return;
		}

		BloomConfig config = BloomServices.config();
		ServerPlayer player = player(helper, new ItemStack(Items.IRON_AXE), CombatIds.CHARGE_ATTACK);
		List<LivingEntity> mobs = zombies(helper, 2, 1);
		BetterCombatSwings.swing(player, mobs.subList(0, 1));
		CombatAbilities.armChargeForTests(player);
		helper.assertTrue(CombatAbilities.chargeArmed(player), "Charge armed");
		BetterCombatSwings.swing(player, mobs.subList(1, 2));
		double plain = lost(mobs.get(0));
		helper.assertTrue(lost(mobs.get(1)) > plain * (config.chargeAttackMultiplier - 0.1),
				"The charged swing hits about x" + config.chargeAttackMultiplier + " (" + plain + " then " + lost(mobs.get(1)) + ")");
		helper.assertFalse(CombatAbilities.chargeArmed(player), "The charge is spent");
		helper.succeed();
	}
}
