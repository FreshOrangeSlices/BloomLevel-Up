package com.freshorangeslices.bloomlevelup.progression;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

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

		long points = Math.min(requestedPoints, wallet.totalPoints());

		if (points <= 0) {
			return ActionResult.of(Outcome.NOT_ENOUGH_XP);
		}

		long neededForCap = (long) Math.min(Long.MAX_VALUE, Math.ceil(xpToCap(progress) / config.vanillaXpToSkillXp));
		points = Math.min(points, Math.max(1L, neededForCap));
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

		wallet.spend(cost);
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

			// Early on, where natural points go is a commitment; once the category is at 100 they can be moved around.
			// XP-invested points can always be moved.
			if (pool == Pool.NATURAL && config.statRefundAtCapOnly && progress.level() < config.levelCap) {
				return ActionResult.of(Outcome.STAT_REFUND_LOCKED);
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

	/**
	 * Switches a stat's XP-invested (post-100) points off or on without touching its natural points, so a
	 * player can drop the overcap and keep the base stat. Any stat with invested points can do this.
	 */
	public ActionResult toggleInvested(BloomProfile profile, String statId) {
		Optional<StatDefinition> stat = definitions.stat(statId);

		if (stat.isEmpty()) {
			return ActionResult.of(Outcome.UNKNOWN_STAT);
		}

		CategoryProgress progress = profile.category(stat.get().categoryId());

		if (!progress.disabledInvested().remove(statId)) {
			progress.disabledInvested().add(statId);
		}

		return ActionResult.success();
	}

	/**
	 * Effective points in a stat: natural + invested, or 0 while the player has it toggled off. The invested
	 * part can also be switched off on its own.
	 */
	public int statValue(BloomProfile profile, String statId) {
		Optional<StatDefinition> stat = definitions.stat(statId);

		if (stat.isEmpty()) {
			return 0;
		}

		CategoryProgress progress = profile.category(stat.get().categoryId());

		if (progress.disabledStats().contains(statId)) {
			return 0;
		}

		if (progress.disabledInvested().contains(statId)) {
			return progress.natural().getOrDefault(statId, 0);
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

	/** Owned Majors that clash with {@code major}, leaving out pairs an owned waiver (Master of Arms) allows. */
	public List<String> ownedConflicts(BloomProfile profile, MajorDefinition major) {
		List<String> owned = new ArrayList<>();

		for (String conflict : major.conflicts()) {
			if (profile.owns(conflict) && !waived(profile.majors().keySet(), major.id(), conflict)) {
				owned.add(conflict);
			}
		}

		return owned;
	}

	/** Whether any Major in {@code owned} waives the conflict between {@code a} and {@code b}. */
	private boolean waived(Set<String> owned, String a, String b) {
		for (String ownedId : owned) {
			Optional<MajorDefinition> waiver = definitions.major(ownedId);

			if (waiver.isPresent() && waiver.get().waivesPair(a, b)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Everything that has to be refunded along with {@code removing} (while {@code adding}, if not null, is
	 * bought in the same step): owned Majors whose prerequisites stop being met (Batter Up! once its last Sword
	 * Major goes), and both Majors of every pair that was only allowed by a waiver that goes (Master of Arms),
	 * repeated until nothing else falls. Sorted; leaves out {@code removing} itself.
	 */
	public List<String> refundCascade(BloomProfile profile, Set<String> removing, String adding) {
		return refundCascade(profile.majors().keySet(), removing, adding);
	}

	/** {@link #refundCascade(BloomProfile, Set, String)} for any set of owned Majors (the spare build's, say). */
	public List<String> refundCascade(Set<String> ownedIds, Set<String> removing, String adding) {
		Set<String> before = new HashSet<>(ownedIds);

		if (adding != null) {
			before.add(adding);
		}

		Set<String> gone = new HashSet<>(removing);
		boolean changed = true;

		while (changed) {
			changed = false;
			Set<String> after = new HashSet<>(before);
			after.removeAll(gone);

			for (String ownedId : after) {
				Optional<MajorDefinition> owned = definitions.major(ownedId);

				if (owned.isEmpty() || ownedId.equals(adding) || gone.contains(ownedId)) {
					continue;
				}

				if (!prerequisitesMet(after, owned.get())) {
					changed |= gone.add(ownedId);
					continue;
				}

				for (String conflict : owned.get().conflicts()) {
					if (after.contains(conflict) && waived(before, ownedId, conflict) && !waived(after, ownedId, conflict)) {
						changed |= gone.add(ownedId);

						if (!conflict.equals(adding)) {
							changed |= gone.add(conflict);
						}
					}
				}
			}
		}

		gone.removeAll(removing);
		List<String> out = new ArrayList<>(gone);
		out.sort(null);
		return out;
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

		if (!conflicts.isEmpty()) {
			if (!swap) {
				return ActionResult.of(Outcome.CONFLICT, conflicts);
			}

			// Whatever depends on the swapped-out Majors goes with them.
			conflicts = new ArrayList<>(conflicts);
			conflicts.addAll(refundCascade(profile, Set.copyOf(conflicts), major.id()));
		}

		// A swap trades clashing Majors, but only if the new one can be paid for up front (the refund comes back after).
		if (major.cost() > profile.availableMajorLevels()) {
			return ActionResult.of(Outcome.NOT_ENOUGH_MAJOR_LEVELS);
		}

		for (String conflict : conflicts) {
			profile.majors().remove(conflict);
		}

		profile.majors().put(majorId, new OwnedMajor(true, major.cost()));
		return ActionResult.success();
	}

	/**
	 * Refunds a Major in full. If other owned Majors would have to go with it (see {@link #refundCascade}) it
	 * refuses with {@link Outcome#REQUIRED_BY_OWNED} listing them; {@link #refundMajorWithDependents} confirms.
	 */
	public ActionResult refundMajor(BloomProfile profile, String majorId) {
		if (!profile.owns(majorId)) {
			return ActionResult.of(Outcome.NOT_OWNED);
		}

		List<String> dependents = refundCascade(profile, Set.of(majorId), null);

		if (!dependents.isEmpty()) {
			return ActionResult.of(Outcome.REQUIRED_BY_OWNED, dependents);
		}

		profile.majors().remove(majorId);
		return ActionResult.success();
	}

	/** Refunds a Major and everything that depends on it, in full. {@code involved} lists the extra refunds. */
	public ActionResult refundMajorWithDependents(BloomProfile profile, String majorId) {
		if (!profile.owns(majorId)) {
			return ActionResult.of(Outcome.NOT_OWNED);
		}

		List<String> dependents = refundCascade(profile, Set.of(majorId), null);
		profile.majors().remove(majorId);

		for (String dependent : dependents) {
			profile.majors().remove(dependent);
		}

		return new ActionResult(Outcome.SUCCESS, dependents);
	}

	// ------------------------------------------------------------------ builds (the second preset)

	/** Profile counter holding the game time from which the player can switch builds again. */
	public static final String BUILD_SWITCH_KEY = "bloomlevelup:build#switch_ready_at";

	/** The second build unlocks the first time any category reaches the level cap, and stays unlocked. */
	public boolean buildsUnlocked(BloomProfile profile) {
		return profile.anyMastered();
	}

	/**
	 * Switches to the other build: Majors (with their switches and options), natural stat points and stat switches
	 * swap; XP-invested points, levels and every Major's own progress (counters) stay. The first switch starts the
	 * second build as a copy of the first. Then a wait ({@code buildSwitchCooldownTicks}) before the next.
	 */
	public ActionResult switchBuild(BloomProfile profile, long now) {
		if (!buildsUnlocked(profile)) {
			return ActionResult.of(Outcome.BUILD_LOCKED);
		}

		if (config.waitsEnabled && now < profile.counter(BUILD_SWITCH_KEY)) {
			return ActionResult.of(Outcome.COOLDOWN);
		}

		com.freshorangeslices.bloomlevelup.player.SavedBuild current = com.freshorangeslices.bloomlevelup.player.SavedBuild.capture(profile);
		com.freshorangeslices.bloomlevelup.player.SavedBuild next = profile.spareBuild().orElse(current).copy();
		next.applyTo(profile);
		profile.setSpareBuild(current.copy());
		profile.setActiveBuild(1 - profile.activeBuild());
		profile.setCounter(BUILD_SWITCH_KEY, now + config.buildSwitchCooldownTicks);
		return ActionResult.success();
	}

	/**
	 * Runs {@code look} with the other build in place for a moment (its Majors, natural points and stat switches),
	 * then puts the build in use back exactly as it was. Only for read-only "what would it be" questions (the stat
	 * sheet's build compare): nothing may be saved or sent while {@code look} runs. With no second build yet, the
	 * other build is the same as this one.
	 */
	public <T> T withOtherBuild(BloomProfile profile, Supplier<T> look) {
		Optional<com.freshorangeslices.bloomlevelup.player.SavedBuild> spare = profile.spareBuild();

		if (spare.isEmpty()) {
			return look.get();
		}

		com.freshorangeslices.bloomlevelup.player.SavedBuild inUse = com.freshorangeslices.bloomlevelup.player.SavedBuild.capture(profile);
		spare.get().copy().applyTo(profile);

		try {
			return look.get();
		} finally {
			inUse.applyTo(profile);
		}
	}

	/**
	 * Runs {@code look} with one more natural point in {@code statId} (even past the points earned), then takes it
	 * back. Read-only like {@link #withOtherBuild}: the stat sheet's "what would one more point do" preview.
	 */
	public <T> T withExtraPoint(BloomProfile profile, String statId, Supplier<T> look) {
		Optional<StatDefinition> stat = definitions.stat(statId);

		if (stat.isEmpty()) {
			return look.get();
		}

		Map<String, Integer> natural = profile.category(stat.get().categoryId()).natural();
		Integer was = natural.get(statId);
		natural.put(statId, was == null ? 1 : was + 1);

		try {
			return look.get();
		} finally {
			if (was == null) {
				natural.remove(statId);
			} else {
				natural.put(statId, was);
			}
		}
	}

	/** Whether stat points in this category can be taken back out right now (the hub shows its minus buttons). */
	public boolean canRefundStats(BloomProfile profile, String categoryId) {
		return config.allowStatRefund && (!config.statRefundAtCapOnly || profile.category(categoryId).level() >= config.levelCap);
	}

	/** Profile counter holding the game time from which a category's Majors can be reset again. */
	public static String majorResetKey(String categoryId) {
		return categoryId + "#majors_reset_at";
	}

	/** Owned Majors in a category, sorted. */
	public List<String> ownedMajorsIn(BloomProfile profile, String categoryId) {
		List<String> owned = new ArrayList<>();

		for (MajorDefinition major : definitions.majorsIn(categoryId)) {
			if (profile.owns(major.id())) {
				owned.add(major.id());
			}
		}

		owned.sort(null);
		return owned;
	}

	/**
	 * A tab's "reset Majors" button: refunds every owned Major in one category in full, plus any Major elsewhere
	 * that needs one of them (see {@link #refundCascade}), then cools down for {@code majorResetCooldownTicks}.
	 * Gives nothing extra (Bloom+ is the reset that pays). {@code now} is the game time; {@code involved} lists
	 * everything refunded.
	 */
	public ActionResult resetCategoryMajors(BloomProfile profile, String categoryId, long now) {
		if (definitions.category(categoryId).isEmpty()) {
			return ActionResult.of(Outcome.UNKNOWN_CATEGORY);
		}

		if (config.waitsEnabled && now < profile.counter(majorResetKey(categoryId))) {
			return ActionResult.of(Outcome.COOLDOWN);
		}

		List<String> owned = ownedMajorsIn(profile, categoryId);

		if (owned.isEmpty()) {
			return ActionResult.of(Outcome.NOT_OWNED);
		}

		List<String> refunded = new ArrayList<>(owned);
		refunded.addAll(refundCascade(profile, Set.copyOf(owned), null));

		for (String id : refunded) {
			profile.majors().remove(id);
		}

		profile.setCounter(majorResetKey(categoryId), now + config.majorResetCooldownTicks);
		return new ActionResult(Outcome.SUCCESS, refunded);
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

	/** The prestige bonus one category would award if it were Bloom+ reset right now (both caps respected). */
	public int previewPrestige(BloomProfile profile, String categoryId) {
		CategoryProgress progress = profile.category(categoryId);
		int budget = Math.max(0, config.prestigeTotalCap - profile.prestigeMajorLevels());
		int target = Math.min(config.prestigePerCategoryCap, progress.level() / config.prestigeEvery);
		return Math.min(budget, Math.max(0, target - progress.prestigeClaimed()));
	}

	/**
	 * The hub's red Bloom+ button, for one tab: that category goes back to level 0 with its natural stat points
	 * back, and its Majors are refunded in both builds (with any Major elsewhere that needs one of them). Keeps:
	 * claimed milestones, mastery (XP mode and the second build stay unlocked), invested points. Awards: that
	 * category's prestige bonus (+1 per {@code prestigeEvery} levels, capped).
	 */
	public BloomResetResult bloomResetCategory(BloomProfile profile, String categoryId) {
		Map<String, Integer> awards = new LinkedHashMap<>();

		if (definitions.category(categoryId).isEmpty()) {
			return new BloomResetResult(awards, 0, profile.bloomCount());
		}

		int award = previewPrestige(profile, categoryId);
		CategoryProgress progress = profile.category(categoryId);

		if (award > 0) {
			progress.setPrestigeClaimed(progress.prestigeClaimed() + award);
			awards.put(categoryId, award);
		}

		progress.setLevel(0);
		progress.setXp(0.0);
		progress.natural().clear();

		List<String> owned = ownedMajorsIn(profile, categoryId);
		List<String> gone = new ArrayList<>(owned);
		gone.addAll(refundCascade(profile, Set.copyOf(owned), null));
		int refunded = 0;

		for (String id : gone) {
			OwnedMajor removed = profile.majors().remove(id);
			refunded += removed == null ? 0 : removed.paid();
		}

		profile.spareBuild().ifPresent(spare -> {
			List<String> spareGone = new ArrayList<>();

			for (String id : spare.majors().keySet()) {
				if (definitions.major(id).map(m -> m.categoryId().equals(categoryId)).orElse(false)) {
					spareGone.add(id);
				}
			}

			spareGone.addAll(refundCascade(spare.majors().keySet(), Set.copyOf(spareGone), null));
			spare.clearCategory(categoryId, spareGone);
		});

		profile.setBloomCount(profile.bloomCount() + 1);
		return new BloomResetResult(awards, refunded, profile.bloomCount());
	}

	/**
	 * Bloom+ full reset (every category at once). Resets: natural levels, XP and natural allocations. Refunds: every owned Major.
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
		profile.setSpareBuild(null);
		profile.setBloomCount(profile.bloomCount() + 1);
		return new BloomResetResult(awards, refunded, profile.bloomCount());
	}
}
