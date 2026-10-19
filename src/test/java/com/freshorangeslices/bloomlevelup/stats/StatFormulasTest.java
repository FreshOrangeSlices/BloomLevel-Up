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
}
