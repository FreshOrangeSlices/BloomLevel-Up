package com.freshorangeslices.bloomlevelup.progression;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.freshorangeslices.bloomlevelup.config.BloomConfig;
import com.freshorangeslices.bloomlevelup.definition.CategoryDefinition;
import com.freshorangeslices.bloomlevelup.definition.DefinitionSet;
import com.freshorangeslices.bloomlevelup.definition.MajorDefinition;
import com.freshorangeslices.bloomlevelup.definition.StatDefinition;
import com.freshorangeslices.bloomlevelup.player.BloomProfile;
import com.freshorangeslices.bloomlevelup.player.CategoryProgress;
import com.freshorangeslices.bloomlevelup.player.OwnedMajor;
import com.freshorangeslices.bloomlevelup.progression.ActionResult.Outcome;

/**
 * Every rule of the shared framework: levels, XP, stat points, the global Major Level economy,
 * ownership/toggles/prerequisites/conflicts, XP Funding/Investment and Bloom+ resets.
 *
 * <p>Pure Java and stateless apart from its inputs, so it is unit-tested directly. Gameplay code never
 * edits a {@link BloomProfile} by hand; it asks this service, which validates and applies the change.
 */
public final class ProgressionService {
	private final DefinitionSet definitions;
	private final BloomConfig config;

	public ProgressionService(DefinitionSet definitions, BloomConfig config) {
		this.definitions = definitions;
		this.config = config;
	}

	public DefinitionSet definitions() {
		return definitions;
	}

	public BloomConfig config() {
		return config;
	}

	// ------------------------------------------------------------------ levels & XP

	/** XP required to advance from {@code level} to {@code level + 1}. */
	public double xpToNext(int level) {
		return config.xpCurveBase + config.xpCurveLinear * level + config.xpCurveQuadratic * (double) level * level;
	}

	/** Total XP still needed to reach the cap from the current position. */
	public double xpToCap(CategoryProgress progress) {
		if (progress.level() >= config.levelCap) {
			return 0.0;
		}

		double total = xpToNext(progress.level()) - progress.xp();

		for (int l = progress.level() + 1; l < config.levelCap; l++) {
			total += xpToNext(l);
		}

		return Math.max(0.0, total);
	}

	/** Grants category XP from any source (activity, funding, commands). XP at the cap is discarded. */
	public XpGainResult addXp(BloomProfile profile, String categoryId, double amount) {
		if (definitions.category(categoryId).isEmpty() || !(amount > 0.0)) {
			return XpGainResult.none(profile.category(categoryId).level());
		}

		CategoryProgress progress = profile.category(categoryId);

		if (progress.level() >= config.levelCap) {
			progress.setXp(0.0);
			return XpGainResult.none(progress.level());
		}

		int startLevel = progress.level();
		double xp = progress.xp() + amount;
		int level = startLevel;

		while (level < config.levelCap && xp >= xpToNext(level)) {
			xp -= xpToNext(level);
			level++;
		}

		boolean masteredNow = false;

		if (level >= config.levelCap) {
			level = config.levelCap;
			xp = 0.0;

			if (!progress.mastered()) {
				progress.setMastered(true);
				masteredNow = true;
			}
		}

		progress.setLevel(level);
		progress.setXp(xp);
		int granted = claimMilestones(progress);
		return new XpGainResult(level - startLevel, granted, masteredNow, level);
	}

	/** Pays out any one-time Major Level milestones the current level has reached but never claimed. */
	private int claimMilestones(CategoryProgress progress) {
		int maxMilestones = config.levelCap / config.majorLevelEvery;
		int reached = Math.min(maxMilestones, progress.level() / config.majorLevelEvery);

		if (reached > progress.majorMilestonesClaimed()) {
			int granted = reached - progress.majorMilestonesClaimed();
			progress.setMajorMilestonesClaimed(reached);
			return granted;
		}

		return 0;
	}

	/**
	 * XP Funding: spend vanilla XP to advance a category below the cap. Never spends more than is needed
	 * to reach the cap, so nothing is wasted.
	 */
	public ActionResult fundWithVanillaXp(BloomProfile profile, String categoryId, int requestedPoints, VanillaXpWallet wallet) {
		if (definitions.category(categoryId).isEmpty()) {
			return ActionResult.of(Outcome.UNKNOWN_CATEGORY);
		}

		if (requestedPoints <= 0 || config.vanillaXpToSkillXp <= 0.0) {
			return ActionResult.of(Outcome.INVALID_AMOUNT);
		}

		CategoryProgress progress = profile.category(categoryId);

		if (progress.level() >= config.levelCap) {
			return ActionResult.of(Outcome.AT_LEVEL_CAP);
		}

		int points = Math.min(requestedPoints, wallet.totalPoints());

		if (points <= 0) {
			return ActionResult.of(Outcome.NOT_ENOUGH_XP);
		}

		int neededForCap = (int) Math.min(Integer.MAX_VALUE, Math.ceil(xpToCap(progress) / config.vanillaXpToSkillXp));
		points = Math.min(points, Math.max(1, neededForCap));
		wallet.spend(points);
		addXp(profile, categoryId, points * config.vanillaXpToSkillXp);
		return ActionResult.success();
	}

	// ------------------------------------------------------------------ XP Investment (post-cap)

	public boolean investmentUnlocked(BloomProfile profile, String categoryId) {
		Optional<CategoryDefinition> category = definitions.category(categoryId);
		return category.isPresent() && category.get().xpInvestment() && profile.category(categoryId).mastered();
	}

	/** Raw vanilla XP cost of buying {@code count} more Investment points from the current total. */
	public long investmentCost(CategoryProgress progress, int count) {
		long total = 0;

		for (int i = 0; i < count; i++) {
			total += config.investmentBaseCost + (long) config.investmentCostGrowth * (progress.investedPoints() + i);
		}

		return total;
	}

	public ActionResult invest(BloomProfile profile, String categoryId, int count, VanillaXpWallet wallet) {
		if (definitions.category(categoryId).isEmpty()) {
			return ActionResult.of(Outcome.UNKNOWN_CATEGORY);
		}

		if (!investmentUnlocked(profile, categoryId)) {
			return ActionResult.of(Outcome.INVESTMENT_LOCKED);
		}

		if (count <= 0) {
			return ActionResult.of(Outcome.INVALID_AMOUNT);
		}

		CategoryProgress progress = profile.category(categoryId);
		long cost = investmentCost(progress, count);

		if (cost > wallet.totalPoints()) {
			return ActionResult.of(Outcome.NOT_ENOUGH_XP);
		}

		wallet.spend((int) cost);
		progress.setInvestedPoints(progress.investedPoints() + count);
		return ActionResult.success();
	}

	// ------------------------------------------------------------------ stats

	public enum Pool {
		NATURAL,
		INVESTED
	}

	/**
	 * Moves points into ({@code delta > 0}) or out of ({@code delta < 0}) a stat from the chosen pool.
	 */
	public ActionResult allocate(BloomProfile profile, String statId, Pool pool, int delta) {
		Optional<StatDefinition> stat = definitions.stat(statId);

		if (stat.isEmpty()) {
			return ActionResult.of(Outcome.UNKNOWN_STAT);
		}

		if (delta == 0) {
			return ActionResult.of(Outcome.INVALID_AMOUNT);
		}

		String categoryId = stat.get().categoryId();
		CategoryProgress progress = profile.category(categoryId);
		Map<String, Integer> target = pool == Pool.NATURAL ? progress.natural() : progress.invested();
		int current = target.getOrDefault(statId, 0);

		if (delta > 0) {
			int unspent = pool == Pool.NATURAL ? progress.naturalUnspent() : progress.investedUnspent();

			if (delta > unspent) {
				return ActionResult.of(Outcome.NOT_ENOUGH_POINTS);
			}
		} else {
			if (!config.allowStatRefund) {
				return ActionResult.of(Outcome.REFUND_DISABLED);
			}

			if (-delta > current) {
				return ActionResult.of(Outcome.NOT_ENOUGH_POINTS);
			}
		}

		int updated = current + delta;

		if (updated == 0) {
			target.remove(statId);
		} else {
			target.put(statId, updated);
		}

		return ActionResult.success();
	}

	public ActionResult toggleStat(BloomProfile profile, String statId) {
		Optional<StatDefinition> stat = definitions.stat(statId);

		if (stat.isEmpty()) {
			return ActionResult.of(Outcome.UNKNOWN_STAT);
		}

		if (!stat.get().toggleable()) {
			return ActionResult.of(Outcome.NOT_TOGGLEABLE);
		}

		CategoryProgress progress = profile.category(stat.get().categoryId());

		if (!progress.disabledStats().remove(statId)) {
			progress.disabledStats().add(statId);
		}

		return ActionResult.success();
	}

	/** Effective points in a stat: natural + invested, or 0 while the player has it toggled off. */
	public int statValue(BloomProfile profile, String statId) {
		Optional<StatDefinition> stat = definitions.stat(statId);

		if (stat.isEmpty()) {
			return 0;
		}

		CategoryProgress progress = profile.category(stat.get().categoryId());

		if (progress.disabledStats().contains(statId)) {
			return 0;
		}

		return progress.rawStat(statId);
	}

	// ------------------------------------------------------------------ Majors

	/** Owned and switched on (non-toggleable Majors are always on). Special dependency rules layer on top of this. */
	public boolean isMajorActive(BloomProfile profile, String majorId) {
		OwnedMajor owned = profile.majors().get(majorId);

		if (owned == null) {
			return false;
		}

		Optional<MajorDefinition> major = definitions.major(majorId);

		// A Major whose definition vanished (datapack removed) stays owned but dormant.
		return major.isPresent() && (owned.enabled() || !major.get().toggleable());
	}

	/** Whether every prerequisite is owned. The UI only ever shows a generic "Prerequisite Required" state. */
	public boolean prerequisitesMet(BloomProfile profile, MajorDefinition major) {
		return prerequisitesMet(profile.majors().keySet(), major);
	}

	/**
	 * Prerequisite check against an arbitrary set of owned ids. A plain id must be owned; a {@code #group}
	 * entry is met by owning any one Major in that tree.
	 */
	public boolean prerequisitesMet(Set<String> owned, MajorDefinition major) {
		for (String prereq : major.prerequisites()) {
			if (MajorDefinition.isGroupRef(prereq)) {
				boolean any = false;

				for (MajorDefinition member : definitions.groupMembers(MajorDefinition.groupOf(prereq))) {
					if (owned.contains(member.id())) {
						any = true;
						break;
					}
				}

				if (!any) {
					return false;
				}
			} else if (!owned.contains(prereq)) {
				return false;
			}
		}

		return true;
	}

	public List<String> ownedConflicts(BloomProfile profile, MajorDefinition major) {
		List<String> owned = new ArrayList<>();

		for (String conflict : major.conflicts()) {
			if (profile.owns(conflict)) {
				owned.add(conflict);
			}
		}

		return owned;
	}

	/** Buys a Major. Returns {@link Outcome#CONFLICT} (with the clashing ids) instead of silently failing. */
	public ActionResult purchaseMajor(BloomProfile profile, String majorId) {
		return purchase(profile, majorId, false);
	}

	/** Accepts the swap prompt: refunds the conflicting Majors and buys this one, atomically. */
	public ActionResult purchaseMajorWithSwap(BloomProfile profile, String majorId) {
		return purchase(profile, majorId, true);
	}

	private ActionResult purchase(BloomProfile profile, String majorId, boolean swap) {
		Optional<MajorDefinition> found = definitions.major(majorId);

		if (found.isEmpty()) {
			return ActionResult.of(Outcome.UNKNOWN_MAJOR);
		}

		MajorDefinition major = found.get();

		if (profile.owns(majorId)) {
			return ActionResult.of(Outcome.ALREADY_OWNED);
		}

		if (!prerequisitesMet(profile, major)) {
			return ActionResult.of(Outcome.PREREQUISITE_REQUIRED);
		}

		List<String> conflicts = ownedConflicts(profile, major);
		int refund = 0;

		if (!conflicts.isEmpty()) {
			if (!swap) {
				return ActionResult.of(Outcome.CONFLICT, conflicts);
			}

			List<String> broken = brokenBy(profile, Set.copyOf(conflicts), major.id());

			if (!broken.isEmpty()) {
				return ActionResult.of(Outcome.REQUIRED_BY_OWNED, broken);
			}

			for (String conflict : conflicts) {
				refund += profile.majors().get(conflict).paid();
			}
		}

		if (major.cost() > profile.availableMajorLevels() + refund) {
			return ActionResult.of(Outcome.NOT_ENOUGH_MAJOR_LEVELS);
		}

		for (String conflict : conflicts) {
			profile.majors().remove(conflict);
		}

		profile.majors().put(majorId, new OwnedMajor(true, major.cost()));
		return ActionResult.success();
	}

	/** Refunds a Major in full. Blocked while another owned Major lists it as a prerequisite. */
	public ActionResult refundMajor(BloomProfile profile, String majorId) {
		if (!profile.owns(majorId)) {
			return ActionResult.of(Outcome.NOT_OWNED);
		}

		List<String> broken = brokenBy(profile, Set.of(majorId), null);

		if (!broken.isEmpty()) {
			return ActionResult.of(Outcome.REQUIRED_BY_OWNED, broken);
		}

		profile.majors().remove(majorId);
		return ActionResult.success();
	}

	public ActionResult toggleMajor(BloomProfile profile, String majorId) {
		OwnedMajor owned = profile.majors().get(majorId);

		if (owned == null) {
			return ActionResult.of(Outcome.NOT_OWNED);
		}

		Optional<MajorDefinition> major = definitions.major(majorId);

		if (major.isEmpty()) {
			return ActionResult.of(Outcome.UNKNOWN_MAJOR);
		}

		if (!major.get().toggleable()) {
			return ActionResult.of(Outcome.NOT_TOGGLEABLE);
		}

		profile.majors().put(majorId, owned.withEnabled(!owned.enabled()));
		return ActionResult.success();
	}

	/** Whether a Major could be picked as a Copycat source (owned, a weapon Major, not on the null list). */
	public boolean isCopyableSource(BloomProfile profile, String sourceId) {
		Optional<MajorDefinition> source = definitions.major(sourceId);
		return profile.owns(sourceId) && source.isPresent()
				&& source.get().flags().contains(MajorDefinition.FLAG_WEAPON)
				&& !source.get().flags().contains(MajorDefinition.FLAG_COPYCAT_INELIGIBLE);
	}

	/**
	 * Sets which weapon Major a copier (Copycat) spreads. An empty {@code sourceId} clears the choice.
	 * The choice can be changed freely.
	 */
	public ActionResult selectCopySource(BloomProfile profile, String copierId, String sourceId) {
		OwnedMajor copier = profile.majors().get(copierId);

		if (copier == null) {
			return ActionResult.of(Outcome.NOT_OWNED);
		}

		Optional<MajorDefinition> definition = definitions.major(copierId);

		if (definition.isEmpty() || !definition.get().flags().contains(MajorDefinition.FLAG_COPIES_WEAPON_MAJOR)) {
			return ActionResult.of(Outcome.INVALID_CHOICE);
		}

		String choice = sourceId == null ? "" : sourceId;

		if (!choice.isEmpty() && !isCopyableSource(profile, choice)) {
			return ActionResult.of(Outcome.INVALID_CHOICE);
		}

		profile.majors().put(copierId, copier.withOption(choice));
		return ActionResult.success();
	}

	/**
	 * The weapon Major a copier is currently spreading, if it is actually working: the copier is on, the
	 * source is still owned and copyable, and the source itself is switched on (toggling the source off turns
	 * Copycat off too).
	 */
	public Optional<String> activeCopySource(BloomProfile profile, String copierId) {
		OwnedMajor copier = profile.majors().get(copierId);

		if (copier == null || copier.option().isEmpty() || !isMajorActive(profile, copierId)) {
			return Optional.empty();
		}

		String source = copier.option();
		return isCopyableSource(profile, source) && isMajorActive(profile, source) ? Optional.of(source) : Optional.empty();
	}

	/**
	 * Owned Majors whose prerequisites would stop being met if {@code removing} were taken away
	 * (optionally while {@code adding} is bought in the same step).
	 */
	private List<String> brokenBy(BloomProfile profile, Set<String> removing, String adding) {
		Set<String> after = new HashSet<>(profile.majors().keySet());
		after.removeAll(removing);

		if (adding != null) {
			after.add(adding);
		}

		List<String> broken = new ArrayList<>();

		for (String ownedId : after) {
			Optional<MajorDefinition> owned = definitions.major(ownedId);

			if (owned.isPresent() && !ownedId.equals(adding) && !prerequisitesMet(after, owned.get())) {
				broken.add(ownedId);
			}
		}

		broken.sort(null);
		return broken;
	}

	// ------------------------------------------------------------------ Bloom+

	/** Prestige bonus each category would award if the player reset right now (respecting both caps). */
	public Map<String, Integer> previewPrestige(BloomProfile profile) {
		Map<String, Integer> awards = new LinkedHashMap<>();
		int budget = Math.max(0, config.prestigeTotalCap - profile.prestigeMajorLevels());

		for (CategoryDefinition category : definitions.categories()) {
			CategoryProgress progress = profile.category(category.id());
			int target = Math.min(config.prestigePerCategoryCap, progress.level() / config.prestigeEvery);
			int award = Math.min(budget, Math.max(0, target - progress.prestigeClaimed()));

			if (award > 0) {
				awards.put(category.id(), award);
				budget -= award;
			}
		}

		return awards;
	}

	/**
	 * Bloom+ full reset. Resets: natural levels, XP and natural allocations. Refunds: every owned Major.
	 * Keeps: claimed milestones (no re-farming), mastery/Investment unlock, invested points and their
	 * allocation, toggles, counters. Awards: capped prestige bonus Major Levels.
	 */
	public BloomResetResult bloomReset(BloomProfile profile) {
		Map<String, Integer> awards = previewPrestige(profile);

		for (Map.Entry<String, Integer> award : awards.entrySet()) {
			CategoryProgress progress = profile.category(award.getKey());
			progress.setPrestigeClaimed(progress.prestigeClaimed() + award.getValue());
		}

		for (CategoryProgress progress : profile.categories().values()) {
			progress.setLevel(0);
			progress.setXp(0.0);
			progress.natural().clear();
		}

		int refunded = profile.spentMajorLevels();
		profile.majors().clear();
		profile.setBloomCount(profile.bloomCount() + 1);
		return new BloomResetResult(awards, refunded, profile.bloomCount());
	}
}
