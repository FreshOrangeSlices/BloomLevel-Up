package com.freshorangeslices.bloomlevelup.progression;

import static com.freshorangeslices.bloomlevelup.TestDefinitions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.freshorangeslices.bloomlevelup.TestDefinitions;
import com.freshorangeslices.bloomlevelup.config.BloomConfig;
import com.freshorangeslices.bloomlevelup.player.BloomProfile;
import com.freshorangeslices.bloomlevelup.player.CategoryProgress;
import com.freshorangeslices.bloomlevelup.progression.ActionResult.Outcome;
import com.freshorangeslices.bloomlevelup.progression.ProgressionService.Pool;

class ProgressionServiceTest {
	private ProgressionService service;
	private BloomProfile profile;

	@BeforeEach
	void setUp() {
		BloomConfig config = new BloomConfig();
		// Flat 10 XP per level keeps the arithmetic obvious.
		config.xpCurveBase = 10;
		config.xpCurveLinear = 0;
		service = new ProgressionService(TestDefinitions.standard(), config.validated());
		profile = new BloomProfile();
	}

	private void levelTo(String category, int level) {
		service.addXp(profile, category, 10.0 * (level - profile.category(category).level()));
	}

	/** Wallet backed by a plain int, standing in for a player's vanilla XP. */
	private static final class FakeWallet implements VanillaXpWallet {
		int points;

		FakeWallet(int points) {
			this.points = points;
		}

		@Override
		public int totalPoints() {
			return points;
		}

		@Override
		public void spend(int amount) {
			points -= amount;
		}
	}

	// ---------------------------------------------------------------- levels

	@Test
	void levelsGrantStatPointsAndMilestones() {
		XpGainResult result = service.addXp(profile, UTILITY, 105);
		assertEquals(10, result.levelsGained());
		assertEquals(1, result.majorLevelsGranted());
		CategoryProgress utility = profile.category(UTILITY);
		assertEquals(10, utility.level());
		assertEquals(5.0, utility.xp(), 1e-9);
		assertEquals(10, utility.naturalUnspent());
		assertEquals(1, profile.availableMajorLevels());
	}

	@Test
	void capMastersCategoryAndDiscardsExtraXp() {
		XpGainResult result = service.addXp(profile, UTILITY, 99999);
		assertEquals(100, result.newLevel());
		assertTrue(result.masteredNow());
		assertEquals(10, result.majorLevelsGranted());
		assertEquals(0.0, profile.category(UTILITY).xp(), 1e-9);
		assertFalse(service.addXp(profile, UTILITY, 50).leveledUp());
		assertEquals(10, profile.earnedMajorLevels());
	}

	@Test
	void unknownCategoryXpIsIgnored() {
		assertFalse(service.addXp(profile, "other:nope", 100).leveledUp());
	}

	// ---------------------------------------------------------------- stats

	@Test
	void allocateWithinUnspentPoints() {
		levelTo(UTILITY, 5);
		assertEquals(Outcome.NOT_ENOUGH_POINTS, service.allocate(profile, EFFICIENCY, Pool.NATURAL, 6).outcome());
		assertTrue(service.allocate(profile, EFFICIENCY, Pool.NATURAL, 3).ok());
		assertTrue(service.allocate(profile, YIELD, Pool.NATURAL, 2).ok());
		assertEquals(0, profile.category(UTILITY).naturalUnspent());
		assertEquals(3, service.statValue(profile, EFFICIENCY));
	}

	@Test
	void refundRespectsConfig() {
		levelTo(UTILITY, 5);
		service.allocate(profile, EFFICIENCY, Pool.NATURAL, 5);
		assertTrue(service.allocate(profile, EFFICIENCY, Pool.NATURAL, -2).ok());
		assertEquals(Outcome.NOT_ENOUGH_POINTS, service.allocate(profile, EFFICIENCY, Pool.NATURAL, -9).outcome());

		BloomConfig strict = new BloomConfig();
		strict.allowStatRefund = false;
		ProgressionService noRefund = new ProgressionService(service.definitions(), strict.validated());
		assertEquals(Outcome.REFUND_DISABLED, noRefund.allocate(profile, EFFICIENCY, Pool.NATURAL, -1).outcome());
	}

	@Test
	void toggledOffStatKeepsPointsButHasNoEffect() {
		levelTo(UTILITY, 4);
		service.allocate(profile, EFFICIENCY, Pool.NATURAL, 4);
		assertTrue(service.toggleStat(profile, EFFICIENCY).ok());
		assertEquals(0, service.statValue(profile, EFFICIENCY));
		assertEquals(4, profile.category(UTILITY).rawStat(EFFICIENCY));
		service.toggleStat(profile, EFFICIENCY);
		assertEquals(4, service.statValue(profile, EFFICIENCY));
	}

	@Test
	void nonToggleableStatCannotBeToggled() {
		assertEquals(Outcome.NOT_TOGGLEABLE, service.toggleStat(profile, VITALITY).outcome());
	}

	// ---------------------------------------------------------------- Majors

	@Test
	void majorLevelsAreGlobalAcrossCategories() {
		levelTo(ARMOR, 10); // earned from Armor...
		assertTrue(service.purchaseMajor(profile, VEIN_MINING).ok()); // ...spent in Utility
		assertEquals(0, profile.availableMajorLevels());
		assertEquals(1, profile.spentMajorLevels());
	}

	@Test
	void cannotBuyWithoutEnoughMajorLevels() {
		levelTo(ARMOR, 60);
		assertEquals(Outcome.NOT_ENOUGH_MAJOR_LEVELS, service.purchaseMajor(profile, IRON_SKIN).outcome());
		levelTo(ARMOR, 70);
		assertTrue(service.purchaseMajor(profile, IRON_SKIN).ok());
	}

	@Test
	void prerequisiteFailureRevealsNothing() {
		levelTo(ARMOR, 100);
		ActionResult result = service.purchaseMajor(profile, CASTLE_OF_STONE);
		assertEquals(Outcome.PREREQUISITE_REQUIRED, result.outcome());
		assertTrue(result.involved().isEmpty());
		service.purchaseMajor(profile, LAST_BREATH);
		assertTrue(service.purchaseMajor(profile, CASTLE_OF_STONE).ok());
	}

	@Test
	void prerequisiteCanBeToggledOffWithoutDisablingDependent() {
		levelTo(ARMOR, 100);
		service.purchaseMajor(profile, LAST_BREATH);
		service.purchaseMajor(profile, CASTLE_OF_STONE);
		service.toggleMajor(profile, LAST_BREATH);
		assertFalse(service.isMajorActive(profile, LAST_BREATH));
		assertTrue(service.isMajorActive(profile, CASTLE_OF_STONE));
	}

	@Test
	void refundBlockedWhileDependentOwned() {
		levelTo(ARMOR, 100);
		service.purchaseMajor(profile, LAST_BREATH);
		service.purchaseMajor(profile, CASTLE_OF_STONE);
		ActionResult blocked = service.refundMajor(profile, LAST_BREATH);
		assertEquals(Outcome.REQUIRED_BY_OWNED, blocked.outcome());
		assertEquals(List.of(CASTLE_OF_STONE), blocked.involved());
		assertTrue(service.refundMajor(profile, CASTLE_OF_STONE).ok());
		assertTrue(service.refundMajor(profile, LAST_BREATH).ok());
		assertEquals(10, profile.availableMajorLevels());
	}

	@Test
	void conflictOffersSwapAndSwapIsSymmetric() {
		levelTo(ARMOR, 10);
		assertTrue(service.purchaseMajor(profile, HOLD_TO_THROW).ok());
		// HOLD_TO_THROW never declared the conflict; it was made symmetric on load.
		ActionResult conflict = service.purchaseMajor(profile, HOLD_TO_CHARGE);
		assertEquals(Outcome.CONFLICT, conflict.outcome());
		assertEquals(List.of(HOLD_TO_THROW), conflict.involved());
		// Swap works even with 0 available, because the refund covers the cost.
		assertTrue(service.purchaseMajorWithSwap(profile, HOLD_TO_CHARGE).ok());
		assertTrue(profile.owns(HOLD_TO_CHARGE));
		assertFalse(profile.owns(HOLD_TO_THROW));
	}

	@Test
	void weaponTreeMajorsHaveNoOrder() {
		levelTo(COMBAT, 10);
		assertTrue(service.purchaseMajor(profile, EXECUTE).ok()); // no need to own Full Sweep first
	}

	@Test
	void anyMajorInTheTreeUnlocksTheUniversalMajor() {
		levelTo(COMBAT, 30);
		assertEquals(Outcome.PREREQUISITE_REQUIRED, service.purchaseMajor(profile, BATTER_UP).outcome());
		service.purchaseMajor(profile, EXECUTE);
		assertTrue(service.purchaseMajor(profile, BATTER_UP).ok());
	}

	@Test
	void refundKeepsTreePrerequisiteSatisfiedIfAnotherMemberRemains() {
		levelTo(COMBAT, 40);
		service.purchaseMajor(profile, FULL_SWEEP);
		service.purchaseMajor(profile, EXECUTE);
		service.purchaseMajor(profile, BATTER_UP);
		assertTrue(service.refundMajor(profile, FULL_SWEEP).ok()); // Execute still covers the sword tree
		ActionResult last = service.refundMajor(profile, EXECUTE);
		assertEquals(Outcome.REQUIRED_BY_OWNED, last.outcome());
		assertEquals(List.of(BATTER_UP), last.involved());
	}

	@Test
	void nonToggleableMajorAlwaysActive() {
		levelTo(MISC, 10);
		service.purchaseMajor(profile, PASSIVE);
		assertEquals(Outcome.NOT_TOGGLEABLE, service.toggleMajor(profile, PASSIVE).outcome());
		assertTrue(service.isMajorActive(profile, PASSIVE));
	}

	@Test
	void refundReturnsWhatWasPaid() {
		levelTo(UTILITY, 10);
		service.purchaseMajor(profile, VEIN_MINING);
		assertEquals(1, profile.majors().get(VEIN_MINING).paid());
		service.refundMajor(profile, VEIN_MINING);
		assertEquals(1, profile.availableMajorLevels());
	}

	// ---------------------------------------------------------------- funding & investment

	@Test
	void fundingNeverSpendsMoreThanNeededForCap() {
		levelTo(UTILITY, 99);
		FakeWallet wallet = new FakeWallet(1000);
		assertTrue(service.fundWithVanillaXp(profile, UTILITY, 1000, wallet).ok());
		assertEquals(100, profile.category(UTILITY).level());
		assertEquals(990, wallet.points);
		assertEquals(Outcome.AT_LEVEL_CAP, service.fundWithVanillaXp(profile, UTILITY, 5, wallet).outcome());
	}

	@Test
	void investmentLockedUntilMasteredAndOnlyWhereEnabled() {
		FakeWallet wallet = new FakeWallet(100000);
		assertEquals(Outcome.INVESTMENT_LOCKED, service.invest(profile, UTILITY, 1, wallet).outcome());
		levelTo(MISC, 100);
		assertEquals(Outcome.INVESTMENT_LOCKED, service.invest(profile, MISC, 1, wallet).outcome());
		levelTo(UTILITY, 100);
		long cost = service.investmentCost(profile.category(UTILITY), 3);
		assertEquals(100 + 110 + 120, cost);
		assertTrue(service.invest(profile, UTILITY, 3, wallet).ok());
		assertEquals(100000 - cost, wallet.points);
		assertEquals(3, profile.category(UTILITY).investedUnspent());
		assertTrue(service.allocate(profile, EFFICIENCY, Pool.INVESTED, 3).ok());
		assertEquals(3, service.statValue(profile, EFFICIENCY));
	}

	@Test
	void investmentNeedsEnoughXp() {
		levelTo(UTILITY, 100);
		assertEquals(Outcome.NOT_ENOUGH_XP, service.invest(profile, UTILITY, 1, new FakeWallet(99)).outcome());
	}

	// ---------------------------------------------------------------- Bloom+

	@Test
	void bloomResetKeepsPermanentLayersAndRefundsMajors() {
		levelTo(UTILITY, 100);
		service.allocate(profile, EFFICIENCY, Pool.NATURAL, 60);
		service.invest(profile, UTILITY, 2, new FakeWallet(100000));
		service.allocate(profile, YIELD, Pool.INVESTED, 2);
		service.purchaseMajor(profile, VEIN_MINING);

		BloomResetResult result = service.bloomReset(profile);
		CategoryProgress utility = profile.category(UTILITY);

		assertEquals(1, result.majorLevelsRefunded());
		assertEquals(2, result.totalPrestigeAwarded());
		assertEquals(0, utility.level());
		assertEquals(0, utility.rawStat(EFFICIENCY)); // natural allocation reset
		assertEquals(2, utility.rawStat(YIELD)); // invested allocation survives
		assertTrue(utility.mastered()); // investment tab stays unlocked
		assertTrue(profile.majors().isEmpty());
		assertEquals(10 + 2, profile.availableMajorLevels());
	}

	@Test
	void relevelingAfterResetDoesNotReFarmMilestones() {
		levelTo(UTILITY, 100);
		service.bloomReset(profile);
		levelTo(UTILITY, 100);
		assertEquals(10 + 2, profile.earnedMajorLevels());
	}

	@Test
	void prestigeIsPerCategoryAndCapped() {
		levelTo(UTILITY, 49);
		levelTo(ARMOR, 49);
		assertEquals(0, service.bloomReset(profile).totalPrestigeAwarded()); // 49 + 49 never counts as 50

		levelTo(ARMOR, 50);
		assertEquals(1, service.bloomReset(profile).totalPrestigeAwarded());
		levelTo(ARMOR, 100);
		assertEquals(1, service.bloomReset(profile).totalPrestigeAwarded()); // only the unclaimed one
		levelTo(ARMOR, 100);
		assertEquals(0, service.bloomReset(profile).totalPrestigeAwarded()); // per-category cap of 2
	}

	@Test
	void prestigeRespectsTotalCap() {
		BloomConfig config = new BloomConfig();
		config.xpCurveBase = 10;
		config.xpCurveLinear = 0;
		config.prestigeTotalCap = 3;
		service = new ProgressionService(TestDefinitions.standard(), config.validated());
		levelTo(UTILITY, 100);
		levelTo(ARMOR, 100);
		levelTo(MISC, 100);
		assertEquals(3, service.bloomReset(profile).totalPrestigeAwarded());
		assertEquals(3, profile.prestigeMajorLevels());
	}

	@Test
	void anyMasteredDrivesGreenNameplate() {
		assertFalse(profile.anyMastered());
		levelTo(MISC, 100);
		assertTrue(profile.anyMastered());
	}

	// ---------------------------------------------------------------- Copycat

	private void ownCombat(String... majors) {
		levelTo(COMBAT, 100);
		levelTo(UTILITY, 100);

		for (String major : majors) {
			assertEquals(Outcome.SUCCESS, service.purchaseMajor(profile, major).outcome());
		}
	}

	@Test
	void copycatCopiesOneOwnedWeaponMajor() {
		ownCombat(COPYCAT, EXECUTE);
		assertTrue(service.activeCopySource(profile, COPYCAT).isEmpty());
		assertEquals(Outcome.SUCCESS, service.selectCopySource(profile, COPYCAT, EXECUTE).outcome());
		assertEquals(EXECUTE, service.activeCopySource(profile, COPYCAT).orElseThrow());

		// The choice can be changed or cleared freely.
		assertEquals(Outcome.SUCCESS, service.selectCopySource(profile, COPYCAT, "").outcome());
		assertTrue(service.activeCopySource(profile, COPYCAT).isEmpty());
	}

	@Test
	void copycatRejectsUnownedUniversalAndNullListMajors() {
		ownCombat(COPYCAT, FULL_SWEEP, BATTER_UP, ASSAULT_RIFLE);
		assertEquals(Outcome.INVALID_CHOICE, service.selectCopySource(profile, COPYCAT, EXECUTE).outcome()); // not owned
		assertEquals(Outcome.INVALID_CHOICE, service.selectCopySource(profile, COPYCAT, BATTER_UP).outcome()); // universal
		assertEquals(Outcome.INVALID_CHOICE, service.selectCopySource(profile, COPYCAT, ASSAULT_RIFLE).outcome()); // null list
		assertEquals(Outcome.INVALID_CHOICE, service.selectCopySource(profile, FULL_SWEEP, FULL_SWEEP).outcome()); // not a copier
	}

	@Test
	void copycatStopsWhenItsSourceIsSwitchedOff() {
		ownCombat(COPYCAT, EXECUTE);
		service.selectCopySource(profile, COPYCAT, EXECUTE);
		service.toggleMajor(profile, EXECUTE);
		assertTrue(service.activeCopySource(profile, COPYCAT).isEmpty());
		service.toggleMajor(profile, EXECUTE);
		assertEquals(EXECUTE, service.activeCopySource(profile, COPYCAT).orElseThrow());

		// Toggling Copycat keeps its choice.
		service.toggleMajor(profile, COPYCAT);
		service.toggleMajor(profile, COPYCAT);
		assertEquals(EXECUTE, service.activeCopySource(profile, COPYCAT).orElseThrow());

		// Refunding the source leaves Copycat with nothing to copy.
		service.refundMajor(profile, EXECUTE);
		assertTrue(service.activeCopySource(profile, COPYCAT).isEmpty());
	}
}
