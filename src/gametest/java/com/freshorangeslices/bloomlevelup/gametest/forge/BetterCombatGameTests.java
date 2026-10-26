package com.freshorangeslices.bloomlevelup.gametest.forge;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;
import com.freshorangeslices.bloomlevelup.compat.BetterCombat;
import com.freshorangeslices.bloomlevelup.config.BloomConfig;
import com.freshorangeslices.bloomlevelup.data.BloomServices;
import com.freshorangeslices.bloomlevelup.effect.StatEffects;
import com.freshorangeslices.bloomlevelup.network.SheetPayloads;
import com.freshorangeslices.bloomlevelup.sheet.StatSheets;
import com.freshorangeslices.bloomlevelup.skill.SkillXp;
import com.freshorangeslices.bloomlevelup.skill.combat.CombatAbilities;
import com.freshorangeslices.bloomlevelup.skill.combat.CombatIds;
import com.freshorangeslices.bloomlevelup.skill.combat.CombatSkill;
import com.freshorangeslices.bloomlevelup.stats.StatFormulas;

/**
 * Bloom with Better Combat (1.20.1). CI runs the game tests twice: without Better Combat (these check that nothing of
 * it shows) and with it loaded (`-PwithBetterCombat`: these drive its real server handler, the way its client does
 * after a swing's wind-up, and check Bloom's rules on top).
 */
@GameTestHolder(BloomLevelUp.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BetterCombatGameTests {
	/** Health is compared after armor (the zombies' 2 points cut every hit by the same share), so a little slack. */
	private static final double EPS = 0.02;

	private static ServerPlayer player(GameTestHelper helper, ItemStack weapon, String... majors) {
		ServerPlayer player = TestPlayers.player(helper, weapon);
		player.setOnGround(true); // Batter Up! only sweeps from the ground
		// Fake players never tick, so the weapon's own attack modifiers are put on by hand.
		player.getAttributes().addTransientAttributeModifiers(weapon.getAttributeModifiers(EquipmentSlot.MAINHAND));
		TestPlayers.grant(player, List.of(SkillXp.UTILITY, SkillXp.COMBAT, SkillXp.ARMOR, SkillXp.MISC), majors);
		StatEffects.refresh(player);
		return player;
	}

	private static List<LivingEntity> zombies(GameTestHelper helper, int count, int row) {
		List<LivingEntity> out = new ArrayList<>();

		for (int i = 0; i < count; i++) {
			// Spawned as is (no random baby or gear: that comes with natural spawning).
			out.add(helper.spawn(EntityType.ZOMBIE, new BlockPos(2 + i, 2, row)));
		}

		return out;
	}

	private static double lost(LivingEntity mob) {
		return mob.getMaxHealth() - mob.getHealth();
	}

	private static void near(GameTestHelper helper, double expected, double actual, String what) {
		helper.assertTrue(Math.abs(expected - actual) < EPS, what + ": expected " + expected + " but was " + actual);
	}

	private static boolean isKey(SheetPayloads.Row row, String key) {
		return row.label().getContents() instanceof TranslatableContents contents && contents.getKey().equals("bloom.sheet." + key);
	}

	private static boolean hasLine(SheetPayloads.View view, String key) {
		return view.summary().stream().anyMatch(row -> isKey(row, key));
	}

	/** The number on a summary line ("3.2  (-25%)" reads as 3.2). */
	private static double value(SheetPayloads.View view, String key) {
		for (SheetPayloads.Row row : view.summary()) {
			if (isKey(row, key)) {
				String text = row.value().getString().trim();
				int space = text.indexOf(' ');
				return Double.parseDouble(space > 0 ? text.substring(0, space) : text);
			}
		}

		throw new IllegalStateException("The sheet has no " + key + " line: " + view.summary());
	}

	private static SheetPayloads.View sheet(ServerPlayer player) {
		return StatSheets.build(player, new SheetPayloads.Request(1, SkillXp.COMBAT, -1, 0, List.of()));
	}

	@GameTest(template = "empty")
	public static void onlyWithBetterCombatInstalled(GameTestHelper helper) {
		ServerPlayer player = player(helper, new ItemStack(Items.DIAMOND_SWORD), CombatIds.FULL_SWEEP);
		SheetPayloads.View view = sheet(player);

		if (BetterCombat.installed()) {
			helper.assertTrue(hasLine(view, "combat.bc.crowd_summary") && !hasLine(view, "combat.sweep"),
					"With Better Combat the sheet shows its crowd rule instead of the vanilla sweep: " + view.summary());
			helper.assertFalse(BetterCombat.swings(new ItemStack(Items.DIAMOND_SWORD)).isEmpty(), "A diamond sword has a Better Combat combo");
			helper.assertFalse(BetterCombat.inSwing(player), "Not mid-swing outside Better Combat's handler");
		} else {
			helper.assertTrue(hasLine(view, "combat.sweep") && !hasLine(view, "combat.bc.crowd_summary"),
					"Without Better Combat the sheet is as it was: " + view.summary());
			helper.assertFalse(BetterCombat.inSwing(player), "No Better Combat swing without Better Combat");
			helper.assertTrue(BetterCombat.swings(new ItemStack(Items.DIAMOND_SWORD)).isEmpty(), "No combo without Better Combat");
		}

		helper.succeed();
	}

	/** The sheet's crowd line follows Better Combat's 1.20.1 rule: -25% at 3 mobs, Sweeping Edge I gives back a sixth of 50%. */
	@GameTest(template = "empty")
	public static void sheetFollowsTheCrowdRule(GameTestHelper helper) {
		if (!BetterCombat.installed()) {
			helper.succeed();
			return;
		}

		BetterCombat.CrowdRule rule = BetterCombat.crowdRule();
		ServerPlayer plain = player(helper, new ItemStack(Items.DIAMOND_SWORD));
		SheetPayloads.View view = sheet(plain);
		near(helper, value(view, "combat.hit") * rule.multiplier(3, 0), value(view, "combat.bc.crowd_summary"), "3 mobs, no Sweeping Edge");

		ItemStack swept = new ItemStack(Items.DIAMOND_SWORD);
		swept.enchant(Enchantments.SWEEPING_EDGE, 1);
		ServerPlayer edge = player(helper, swept);
		SheetPayloads.View withEdge = sheet(edge);
		near(helper, value(withEdge, "combat.hit") * rule.multiplier(3, 1), value(withEdge, "combat.bc.crowd_summary"), "3 mobs, Sweeping Edge I");

		ServerPlayer sweeper = player(helper, new ItemStack(Items.DIAMOND_SWORD), CombatIds.FULL_SWEEP);
		SheetPayloads.View full = sheet(sweeper);
		near(helper, value(full, "combat.hit"), value(full, "combat.bc.crowd_summary"), "3 mobs, Full Sweep");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void fullSweepCancelsTheCrowdPenalty(GameTestHelper helper) {
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
		double weaker = single * rule.multiplier(3, 0);

		for (LivingEntity zombie : three) {
			near(helper, weaker, lost(zombie), "Each of 3 mobs without Full Sweep");
		}

		// With Full Sweep: every mob takes the whole hit.
		ServerPlayer sweeper = player(helper, new ItemStack(Items.DIAMOND_SWORD), CombatIds.FULL_SWEEP);
		List<LivingEntity> others = zombies(helper, 3, 5);
		BetterCombatSwings.swing(sweeper, others);

		for (LivingEntity zombie : others) {
			near(helper, single, lost(zombie), "Each of 3 mobs with Full Sweep");
		}

		helper.assertTrue(sweeper.getAttribute(Attributes.ATTACK_DAMAGE).getModifier(BetterCombat.CROWD_MOD.uuid()) == null,
				"The counter doesn't outlive the hit");
		helper.assertFalse(BetterCombat.inSwing(sweeper), "The swing is over once Better Combat's handler is done");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void batterUpReplacesItsSweepWithNoPenalty(GameTestHelper helper) {
		if (!BetterCombat.installed()) {
			helper.succeed();
			return;
		}

		ServerPlayer player = player(helper, new ItemStack(Items.IRON_AXE), CombatIds.FULL_SWEEP, CombatIds.BATTER_UP);
		List<LivingEntity> one = zombies(helper, 1, 1);
		BetterCombatSwings.swing(player, one);
		double single = lost(one.get(0));
		helper.assertTrue(single > 1.0, "The axe swing landed (" + single + ")");

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

	@GameTest(template = "empty")
	public static void predatorBuildsOnAnyMob(GameTestHelper helper) {
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

	@GameTest(template = "empty")
	public static void chargeLandsThroughBetterCombat(GameTestHelper helper) {
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
