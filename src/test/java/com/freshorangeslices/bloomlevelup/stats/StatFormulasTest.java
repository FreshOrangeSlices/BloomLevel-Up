package com.freshorangeslices.bloomlevelup.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

/** Each assertion is a worked example from the Utility / Armor design documents. */
class StatFormulasTest {
	@Test
	void efficiency() {
		assertEquals(2.0, StatFormulas.efficiencyMultiplier(100), 1e-9);
		assertEquals(6.0, StatFormulas.efficiencyMultiplier(500), 1e-9);
	}

	@Test
	void yieldGuaranteedCopies() {
		SplittableRandom random = new SplittableRandom(1);
		assertEquals(1, StatFormulas.yieldExtraCopies(100, random));
		assertEquals(3, StatFormulas.yieldExtraCopies(300, random));
		assertEquals(0, StatFormulas.yieldExtraCopies(0, random));
	}

	@Test
	void yieldRemainderIsAChance() {
		SplittableRandom random = new SplittableRandom(7);
		int extra = 0;

		for (int i = 0; i < 100_000; i++) {
			extra += StatFormulas.yieldExtraCopies(150, random);
		}

		assertEquals(1.5, extra / 100_000.0, 0.01);
	}

	@Test
	void preservationCurve() {
		assertEquals(0.5, StatFormulas.preservationSaveChance(100), 1e-9);
		assertEquals(0.75, StatFormulas.preservationSaveChance(300), 1e-9);
		assertEquals(0.2, StatFormulas.preservationSaveChance(25), 1e-9);
	}

	@Test
	void vitalityAndDefense() {
		assertEquals(20.0, StatFormulas.vitalityBonusHealth(100), 1e-9); // +10 hearts
		assertEquals(10.0, StatFormulas.defenseArmor(100), 1e-9);
		assertEquals(2.0, StatFormulas.defenseArmor(20), 1e-9);
	}

	@Test
	void armorOvercap() {
		assertEquals(0.01, StatFormulas.armorOvercapReduction(22, 0.9), 1e-9);
		assertEquals(0.10, StatFormulas.armorOvercapReduction(40, 0.9), 1e-9);
		assertEquals(0.0, StatFormulas.armorOvercapReduction(15, 0.9), 1e-9);
		assertEquals(0.9, StatFormulas.armorOvercapReduction(10_000, 0.9), 1e-9);
	}

	@Test
	void dodgeSoftCap() {
		assertEquals(0.30, StatFormulas.dodgeChance(100), 1e-9);
		assertEquals(0.40, StatFormulas.dodgeChance(134), 0.001); // soft cap is reached at ~133.3 points
		// 100 points past the soft cap only add 3 percentage points.
		assertEquals(0.43, StatFormulas.dodgeChance((int) Math.round(400.0 / 3 + 100)), 0.001);
	}

	@Test
	void reductionsMultiply() {
		assertEquals(63.0, StatFormulas.applyReductions(100, 0.30, 0.10), 1e-9); // Heavy + 10% overcap
		assertEquals(42.0, StatFormulas.applyReductions(100, 0.30, 0.40), 1e-9); // Heavy + Brace
	}

	@Test
	void lightBackupDodge() {
		assertEquals(0.37, StatFormulas.anyOf(0.30, 0.10), 1e-9);
	}

	@Test
	void dodgeCap() {
		assertEquals(0.90, StatFormulas.dodgeChance(100_000, 0.90), 1e-9);
		assertEquals(0.30, StatFormulas.dodgeChance(100, 0.90), 1e-9);
	}

	@Test
	void durabilityAt100IsLikeUnbreakingThree() {
		assertEquals(0.30, StatFormulas.armorDurabilitySaveChance(100), 1e-9); // armor curve
		assertEquals(0.75, StatFormulas.shieldDurabilitySaveChance(100), 1e-9); // shield curve
		assertEquals(0.0, StatFormulas.armorDurabilitySaveChance(0), 1e-9);
		// keeps growing past 100 but never reaches 40% (armor) / 100% (shield)
		assertTrue(StatFormulas.armorDurabilitySaveChance(1000) < 0.40);
		assertTrue(StatFormulas.armorDurabilitySaveChance(200) > StatFormulas.armorDurabilitySaveChance(100));
	}

	@Test
	void lastBreathLadderHalves() {
		assertEquals(1.0, StatFormulas.lastBreathChance(0), 1e-9);
		assertEquals(0.5, StatFormulas.lastBreathChance(1), 1e-9);
		assertEquals(0.03125, StatFormulas.lastBreathChance(5), 1e-9);
	}

	@Test
	void proteinGrowsEveryTwentyMealsUpToTenHearts() {
		assertEquals(0.0, StatFormulas.proteinBonusHealth(19), 1e-9);
		assertEquals(1.0, StatFormulas.proteinBonusHealth(20), 1e-9);
		assertEquals(20.0, StatFormulas.proteinBonusHealth(400), 1e-9);
		assertEquals(20.0, StatFormulas.proteinBonusHealth(5000), 1e-9);
	}

	@Test
	void heavyKnockbackTapers() {
		assertEquals(0.5, StatFormulas.heavyKnockbackResistance(0.0), 1e-9);
		assertEquals(0.3, StatFormulas.heavyKnockbackResistance(0.4), 1e-9); // netherite set: 0.4 + 0.3 = 0.7
		assertEquals(0.0, StatFormulas.heavyKnockbackResistance(1.0), 1e-9);
	}

	@Test
	void fittingStyleAverages() {
		assertEquals(StatFormulas.ArmorStyle.HEAVY, StatFormulas.fittingStyle(new int[] {3, 3, 3, 3}, 4));
		assertEquals(StatFormulas.ArmorStyle.MEDIUM, StatFormulas.fittingStyle(new int[] {3, 3, 1, 1}, 4)); // 2.0
		assertEquals(StatFormulas.ArmorStyle.LIGHT, StatFormulas.fittingStyle(new int[] {1, 1, 1, 2}, 4)); // 1.25
		assertEquals(StatFormulas.ArmorStyle.HEAVY, StatFormulas.fittingStyle(new int[] {3, 3, 2, 2}, 4)); // 2.5
		assertEquals(StatFormulas.ArmorStyle.NONE, StatFormulas.fittingStyle(new int[] {3, 3, 3}, 3)); // empty slot
		assertEquals(StatFormulas.ArmorStyle.NONE, StatFormulas.fittingStyle(new int[] {}, 4)); // nothing classified
	}

	@Test
	void rollsFromChanceSplitsWholeAndFraction() {
		SplittableRandom random = new SplittableRandom(3);
		assertEquals(0, StatFormulas.rollsFromChance(0.0, random));
		assertEquals(2, StatFormulas.rollsFromChance(2.0, random));
		int total = 0;

		for (int i = 0; i < 100_000; i++) {
			total += StatFormulas.rollsFromChance(0.25, random);
		}

		assertEquals(0.25, total / 100_000.0, 0.01);
	}

	@Test
	void shovelMasteryBoost() {
		assertEquals(120, StatFormulas.boosted(100, 0.20));
		assertEquals(0, StatFormulas.boosted(0, 0.20));
		assertEquals(6, StatFormulas.boosted(5, 0.20));
	}

	@Test
	void criticalPenetrationLooting() {
		assertEquals(1.5, StatFormulas.criticalMultiplier(0), 1e-9);
		assertEquals(4.0, StatFormulas.criticalMultiplier(100), 1e-9);
		assertEquals(0.5, StatFormulas.penetration(100), 1e-9);
		assertTrue(StatFormulas.penetration(1000) < 1.0);
		assertEquals(3.0, StatFormulas.virtualLooting(100), 1e-9);
	}

	@Test
	void marksmanRamp() {
		assertEquals(0.0, StatFormulas.marksmanBonus(8, 10, 40, 0.5), 1e-9);
		assertEquals(0.25, StatFormulas.marksmanBonus(25, 10, 40, 0.5), 1e-9);
		assertEquals(0.5, StatFormulas.marksmanBonus(100, 10, 40, 0.5), 1e-9);
	}

	@Test
	void maceFallBonusMatchesVanilla() {
		assertEquals(0.0, StatFormulas.maceFallBonus(1.0), 1e-9);
		assertEquals(12.0, StatFormulas.maceFallBonus(3.0), 1e-9);
		assertEquals(22.0, StatFormulas.maceFallBonus(8.0), 1e-9);
		assertEquals(32.0, StatFormulas.maceFallBonus(18.0), 1e-9);
	}

	@Test
	void stacks() {
		assertEquals(1.5, StatFormulas.stackMultiplier(5, 0.10, 5), 1e-9);
		assertEquals(1.5, StatFormulas.stackMultiplier(9, 0.10, 5), 1e-9);
		assertEquals(5.0, StatFormulas.comboBonus(40, 0.25, 5.0), 1e-9);
		assertEquals(1.0, StatFormulas.comboBonus(4, 0.25, 5.0), 1e-9);
	}

	@Test
	void lightningChainShapes() {
		assertEquals(0, StatFormulas.lightningChain(1).length);
		org.junit.jupiter.api.Assertions.assertArrayEquals(new int[] {1, 0, 1}, StatFormulas.lightningChain(2));
		org.junit.jupiter.api.Assertions.assertArrayEquals(new int[] {0, 1, 2}, StatFormulas.lightningChain(7));
	}

	@Test
	void combatXpIsCappedByRemainingHealth() {
		assertEquals(5.0, StatFormulas.combatHitXp(30, 10, 0.5, 1.0), 1e-9);
		assertEquals(30.0, StatFormulas.combatHitXp(6, 20, 0.5, 10.0), 1e-9);
	}

	@Test
	void wisdomAndEndurance() {
		assertEquals(1.0, StatFormulas.wisdomMultiplier(0), 1e-9);
		assertEquals(3.0, StatFormulas.wisdomMultiplier(50), 1e-9);
		assertEquals(5.0, StatFormulas.wisdomMultiplier(100), 1e-9);
		assertEquals(15, StatFormulas.scaledXp(3, 5.0, new java.util.Random(1)));
		assertEquals(0, StatFormulas.scaledXp(0, 5.0, new java.util.Random(1)));
		assertEquals(1.0, StatFormulas.enduranceExhaustionFactor(0), 1e-9);
		assertEquals(1.0 / 1.5, StatFormulas.enduranceExhaustionFactor(100), 1e-9);
	}

	@Test
	void vanillaXpTotalsMatchTheWiki() {
		assertEquals(7, StatFormulas.vanillaXpForLevel(0));
		assertEquals(42, StatFormulas.vanillaXpForLevel(16));
		assertEquals(112, StatFormulas.vanillaXpForLevel(30));
		assertEquals(0L, StatFormulas.vanillaTotalXp(0, 0.0F));
		assertEquals(352L, StatFormulas.vanillaTotalXp(16, 0.0F));
		assertEquals(394L, StatFormulas.vanillaTotalXp(17, 0.0F));
		assertEquals(1395L, StatFormulas.vanillaTotalXp(30, 0.0F));
		assertEquals(1507L, StatFormulas.vanillaTotalXp(31, 0.0F));
		assertEquals(1395L + 56L, StatFormulas.vanillaTotalXp(30, 0.5F));
	}

	@Test
	void xpInsuranceReturnsHalfOfWhatIsLost() {
		// Level 30: 1395 XP held, 100 dropped as orbs, 1295 lost -> 647 back.
		assertEquals(647, StatFormulas.insuredXp(30, 0.0F, 0.5));
		// Level 2: 16 XP held, 14 dropped -> 2 lost -> 1 back.
		assertEquals(1, StatFormulas.insuredXp(2, 0.0F, 0.5));
		assertEquals(0, StatFormulas.insuredXp(0, 0.0F, 0.5));
	}

	@Test
	void overdoseClimbsTwoLevelsAboveTheSource() {
		assertEquals(1, StatFormulas.overdoseAmplifier(0, 0, 2));
		assertEquals(2, StatFormulas.overdoseAmplifier(1, 0, 2));
		assertEquals(2, StatFormulas.overdoseAmplifier(2, 0, 2));
		assertEquals(3, StatFormulas.overdoseAmplifier(1, 3, 2));
		assertEquals(0, StatFormulas.overdoseAmplifier(5, 0, 2));
		assertEquals(0, StatFormulas.overdoseAmplifier(0, 0, 0));
	}
}
