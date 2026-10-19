package com.freshorangeslices.bloomlevelup.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
