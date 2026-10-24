package com.freshorangeslices.bloomlevelup.config;

/**
 * Server-side tuning values. Loaded from {@code config/bloomlevelup.json}; every field has a default so
 * an empty or partial file still works. Plain public fields keep Gson (de)serialisation trivial.
 *
 * <p>Values marked TBD in the design docs are given working defaults here so they can be tuned in play
 * without code changes.
 */
public final class BloomConfig {
	/** The defaults this build ships with. Files from older builds are moved onto changed defaults by {@link #migrate()}. */
	public static final int CURRENT_VERSION = 6;

	/** Which build's defaults the file was written with (0 = before versioning). */
	public int configVersion;

	/** Natural level cap per category. */
	public int levelCap = 100;

	/** A global Major Level is granted every this many natural levels in a category (one time only). */
	public int majorLevelEvery = 10;

	/** Bloom+ prestige: one bonus Major Level per this many levels reached in a single category before a reset. */
	public int prestigeEvery = 50;

	/** Lifetime prestige bonus cap per category. */
	public int prestigePerCategoryCap = 2;

	/** Lifetime prestige bonus cap across all categories (8: 2 per tab, confirmed in round 7). */
	public int prestigeTotalCap = 8;

	/** XP needed to go from level L to L+1 = base + linear * L + quadratic * L^2 (TBD curve). */
	public double xpCurveBase = 50.0;
	public double xpCurveLinear = 10.0;
	public double xpCurveQuadratic = 0.0;

	/** Skill XP granted per raw vanilla XP point spent on funding a category below the cap (TBD). */
	public double vanillaXpToSkillXp = 1.0;

	/** Raw vanilla XP cost of the n-th post-cap Investment point = base + growth * n (TBD curve). */
	public int investmentBaseCost = 100;
	public int investmentCostGrowth = 10;

	/** Whether naturally earned stat points may be pulled back out and re-allocated freely. */
	public boolean allowStatRefund = true;
	/** Refunding a single Major (right-click in the hub, {@code /bloom refund}). Off: only a tab's reset button refunds. */
	public boolean allowMajorRefund = false;
	/** The waits on the tab reset button and on switching builds ({@code /bloom admin waits on|off}). */
	public boolean waitsEnabled = true;
	/** How long after switching builds before you can switch again (2 minutes of game time). */
	public int buildSwitchCooldownTicks = 2400;
	/** Stat points can only be taken back out (the hub's minus button) once that category is at the level cap. */
	public boolean statRefundAtCapOnly = true;
	/** A tab's "reset Majors" button: how long before that tab can be reset again (20 minutes of game time). */
	public int majorResetCooldownTicks = 24000;

	/** Safety ceiling for the armor-overcap damage-reduction layer so it can never reach immunity. */
	public double armorOvercapReductionCap = 0.90;

	// ---------------------------------------------------------------- XP sources

	/** Multiplies every XP award per category, for pacing. 1.0 = as computed. */
	public double utilityXpMultiplier = 1.0;
	public double combatXpMultiplier = 1.0;
	public double armorXpMultiplier = 1.0;
	public double miscXpMultiplier = 1.0;

	/** Armor XP per point of incoming damage (one point = half a heart), before the event multiplier. */
	public double armorXpPerDamage = 4.0;
	/** Locked by the Armor doc: blocked hits 1.5x, dodges 2.0x. */
	public double armorBlockXpMultiplier = 1.5;
	public double armorDodgeXpMultiplier = 2.0;
	/** Utility XP per successful fishing catch and per successful shear (block XP lives in data: bloom/xp/blocks). */
	public double utilityFishingXp = 8.0;
	public double utilityShearXp = 2.5;

	// ---------------------------------------------------------------- Armor tuning

	/** Safety ceiling for the main Dodge roll so no investment reaches literal immunity. */
	public double dodgeChanceCap = 0.90;
	/** Last Breath: length of the diminishing window in ticks (20 minutes) and health restored (5 hearts). */
	public int lastBreathWindowTicks = 24_000;
	public float lastBreathHealth = 10.0F;
	/** Castle of Stone: invincibility length and bed rest needed to recharge (4 seconds, confirmed). */
	public int castleOfStoneTicks = 200;
	public int castleOfStoneRestTicks = 80;
	/** Brace!: how long the player must crouch still (or hold a shield up) before bracing; 1 = at once. */
	public int braceDelayTicks = 1;
	/** Brace!: once it ends, how long before it can take hold again (10 seconds). */
	public int braceCooldownTicks = 200;

	// ---------------------------------------------------------------- Utility tuning

	/** Vein Mining / Timber safety limits (blocks processed per activation). */
	public int veinMiningMaxBlocks = 64;
	public int timberMaxLogs = 256;
	/** Holding sneak skips Vein Mining and Timber for that block, without opening the hub. */
	public boolean chainBreakSneakBypass = true;
	/** Shovel Mastery: Utility stats count this much more while using a shovel (0.20 = +20%). */
	public double shovelMasteryBonus = 0.20;
	/** Shovel Mastery treasure: chance per natural dug block for every 100 (boosted) Yield. */
	public double excavationTreasureChancePer100Yield = 0.02;
	/** Shovel Mastery treasure: chance per natural dug block before any Yield. */
	public double excavationTreasureBaseChance = 0.10;
	/** Growth Aura: radius (horizontal / vertical) and the chance per crop per pulse for an extra growth tick. */
	public int growthAuraRadius = 4;
	public int growthAuraHeight = 2;
	public int growthAuraIntervalTicks = 20;
	public double growthAuraChance = 0.20;
	/** Growth Aura: growth ticks per pulse for plants that aren't crops (cactus, cane, berries, saplings...). */
	public int growthAuraRandomTicks = 4;
	/** Master Shearing: chance the target stays shearable; extra durability per shear. */
	public double masterShearingChance = 0.5;
	public int masterShearingExtraDurability = 1;
	/** Triple Catch: total loot rolls per catch and extra rod durability per catch. */
	public int tripleCatchRolls = 3;
	public int tripleCatchExtraDurability = 2;

	// ---------------------------------------------------------------- Combat

	/** Combat XP per point of real health damage dealt (capped by the target's remaining health). */
	public double combatXpPerDamage = 0.5;
	/** Kill bonus: this share of the damage XP the killer earned on that target. */
	public double combatKillBonus = 0.5;
	/** Combat stats and Majors work against players; and whether hitting players gives Combat XP. */
	public boolean combatPvp = true;
	public boolean combatPvpXp = true;
	/** Combat XP multipliers per entity type id (bosses, dangerous modded mobs). */
	public java.util.Map<String, Double> combatEntityXpMultipliers = new java.util.LinkedHashMap<>(java.util.Map.of(
			"minecraft:wither", 10.0,
			"minecraft:ender_dragon", 12.0,
			"minecraft:warden", 5.0,
			"minecraft:elder_guardian", 5.0));

	public double executeThreshold = 0.30;
	public double executeBossThreshold = 0.10;
	public double armorBreakRatio = 0.5;
	public double guaranteedCriticalPenalty = 2.0;
	public double marksmanStartDistance = 10.0;
	public double marksmanFullDistance = 40.0;
	public double marksmanMaxBonus = 0.5;
	public int piercingShotLevel = 4;
	public int straightShotMaxTicks = 100;
	public double reinforcedShotDamage = 3.0;
	public double lightningShockRatio = 0.5;
	public double lightningShockRadius = 6.0;
	public int lightningShockCooldownTicks = 40;
	/** Lightning Shock: how long each jump takes. */
	public int lightningShockBounceTicks = 2;
	/** Lightning Shock: sparks that leave the struck target at once, and how many enemies each one jumps through. */
	public int lightningShockSparks = 3;
	public int lightningShockJumps = 3;
	public double storedImpactRatio = 0.5;
	public double storedImpactCap = 20.0;
	/** Stored Impact: one storage window opened by the first stored fall; later falls add to it until it closes. */
	public int storedImpactWindowTicks = 6000;
	public double windImpactRadius = 3.5;
	/** Wind Impact: right-click the ground with a mace for a wind-charge burst; cooldown between bursts. */
	public int windImpactCooldownTicks = 20;
	/** Wind Impact: mace durability each burst costs. */
	public int windImpactDurabilityCost = 3;
	/** Fitting Armor / Medium: how much of an axe's shield-disable time is left (0.3 = an axe's 5 s becomes 1.5 s). */
	public double mediumShieldDisableRatio = 0.3;
	public double windImpactStrength = 1.2;
	public double brawlerDamage = 6.0;
	public double comboPerHit = 0.25;
	public double comboCap = 5.0;
	public int comboResetTicks = 60;
	public double disarmChance = 0.30;
	public double longReachBlocks = 2.0;
	public double impaleChance = 0.50;
	public int bleedTicks = 60;
	public int auraSwordHits = 5;
	public double auraSwordRadius = 4.0;
	/** Aura Sword: ticks between its three outward waves. */
	public int auraSwordPulseGapTicks = 9;
	public double predatorPerStack = 0.10;
	public int predatorMaxStacks = 5;
	public int predatorResetTicks = 100;
	public double bloodlustPerStack = 0.08;
	public int bloodlustMaxStacks = 5;
	public int bloodlustTicks = 200;
	public double overkillRatio = 0.6;
	public double overkillRadius = 4.0;
	public int overkillMaxTargets = 3;
	public double firstStrikeBonus = 0.5;
	public double unrestrictedRiptideStrength = 3.0;
	/** Charge Attack / Heavy Hands: hold the attack key this long (after the swing has recovered) to charge. */
	public int chargeAttackTicks = 20;
	public double chargeAttackMultiplier = 2.0;
	/** Lightning Summon: reach, durability cost (share of max durability) and cooldown. */
	public double lightningSummonRange = 48.0;
	public double lightningSummonCostRatio = 0.10;
	public int lightningSummonCooldownTicks = 20;
	/** Catch!: throw speed (a trident is 2.5) and durability per throw (a weapon too worn to pay isn't thrown). */
	public double catchThrowSpeed = 2.5;
	public int catchThrowDurabilityCost = 3;
	/** Charge Attack / Heavy Hands: total durability a charged swing costs (including the hit's own wear). */
	public int chargedSwingDurabilityCost = 2;
	/** Assault Rifle: shots per magazine. */
	public int assaultRifleMagazine = 16;
	/** Master of Arms / Charged Throw (and Gale Throw): speed multiplier, mobs it goes through, durability (damage uses chargeAttackMultiplier). */
	public double chargedThrowSpeedMultiplier = 1.4;
	public int chargedThrowPierce = 3;
	public int chargedThrowDurabilityCost = 8;
	/**
	 * Master of Arms / Charged Burst vs a normal burst: blast and push radius, push strength, your upward launch
	 * speed (a normal burst is about 1.2: ~8 blocks; 1.65: ~14 blocks) and extra mace durability.
	 */
	public double chargedBurstScale = 1.3;
	public double chargedBurstPushScale = 1.7;
	public double chargedBurstLaunchSpeed = 1.65;
	public int chargedBurstExtraDurability = 2;
	/** Master of Arms / Storm Call: bolts, their distance from the aim point, ticks between bolts, and cost/cooldown vs one bolt. */
	public int stormCallBolts = 3;
	public double stormCallRadius = 2.5;
	public int stormCallGapTicks = 3;
	public double stormCallCostMultiplier = 2.0;
	/** Master of Arms / Gale Throw: a charged mace throw's landing pulls mobs in from this many blocks away, then bursts. */
	public double galeThrowPullRadius = 6.5;
	public double galeThrowPullStrength = 0.9;
	public int galeThrowPullTicks = 8;
	public double galeThrowBurstScale = 1.4;

	// ---- Misc
	/** Misc XP per point of vanilla XP collected from orbs (before Wisdom and Mending). */
	public double miscXpPerOrbPoint = 0.25;
	/** Iron Stomach: the nutrition/saturation floor for unsafe food (cooked chicken). */
	public int ironStomachNutrition = 6;
	public double ironStomachSaturation = 7.2;
	/** XP Insurance: share of the XP lost on death that comes back. */
	public double xpInsuranceShare = 0.5;
	/** Water to Wine: minimum duration of new timed effects, and what's left when it's switched off. */
	public int waterToWineMinTicks = 12000;
	public int waterToWineCutTicks = 2400;
	/** Overdose: how many levels an effect can climb above its source. */
	public int overdoseMaxBoost = 2;
	/** Become My Fuel: how long the imprinted effect lasts on each hit. */
	public int fuelEffectTicks = 100;
	/** Overenchant: highest enchantment level the anvil can reach. */
	public int overenchantMaxLevel = 8;
	/** Stack Overflow: stack size in your own inventory and pocket (at most 300, what the save format allows). */
	public int stackOverflowMax = 300;

	/**
	 * Moves an older file onto this build's changed defaults, but only for values the server owner never
	 * changed (still equal to the old default). Returns this.
	 */
	public BloomConfig migrate() {
		if (configVersion < 1) {
			// Playtest round 1 (2026-10-19).
			impaleChance = moved(impaleChance, 0.40, 0.50);
			auraSwordRadius = moved(auraSwordRadius, 3.5, 3.0);
			auraSwordPulseGapTicks = auraSwordPulseGapTicks == 10 ? 4 : auraSwordPulseGapTicks;
			growthAuraChance = moved(growthAuraChance, 0.15, 0.35);
			excavationTreasureChancePer100Yield = moved(excavationTreasureChancePer100Yield, 0.04, 0.05);
		}

		if (configVersion < 2) {
			// Playtest round 2 (2026-10-21). Version-1 files carry round 1's defaults; older ones were just moved there.
			fuelEffectTicks = fuelEffectTicks == 200 ? 100 : fuelEffectTicks;
			lightningShockBounceTicks = lightningShockBounceTicks == 20 ? 10 : lightningShockBounceTicks;
			growthAuraChance = moved(growthAuraChance, 0.35, 0.20);
		}

		if (configVersion < 3) {
			// Playtest round 2b: Shovel Mastery's treasure is a flat 10% plus a little from Yield.
			excavationTreasureBaseChance = moved(excavationTreasureBaseChance, 0.03, 0.10);
			excavationTreasureChancePer100Yield = moved(excavationTreasureChancePer100Yield, 0.05, 0.02);
		}

		if (configVersion < 4) {
			// Playtest round 3 (2026-10-22): faster Lightning jumps, a wider and slower Aura Sword.
			lightningShockBounceTicks = lightningShockBounceTicks == 10 ? 2 : lightningShockBounceTicks;
			auraSwordRadius = moved(auraSwordRadius, 3.0, 4.0);
			auraSwordPulseGapTicks = auraSwordPulseGapTicks == 4 ? 9 : auraSwordPulseGapTicks;
		}

		if (configVersion < 5) {
			// Playtest round 6 (2026-10-22): Brace! takes hold at once.
			braceDelayTicks = braceDelayTicks == 20 ? 1 : braceDelayTicks;
		}

		if (configVersion < 6) {
			// Round 7 (2026-10-23): the Bloom+ bonus can reach all 8 (2 per tab).
			prestigeTotalCap = prestigeTotalCap == 6 ? 8 : prestigeTotalCap;
		}

		configVersion = CURRENT_VERSION;
		return this;
	}

	private static double moved(double value, double oldDefault, double newDefault) {
		return Math.abs(value - oldDefault) < 1.0E-9 ? newDefault : value;
	}

	public BloomConfig validated() {
		levelCap = Math.max(1, levelCap);
		majorLevelEvery = Math.max(1, majorLevelEvery);
		prestigeEvery = Math.max(1, prestigeEvery);
		majorResetCooldownTicks = Math.max(0, majorResetCooldownTicks);
		buildSwitchCooldownTicks = Math.max(0, buildSwitchCooldownTicks);
		prestigePerCategoryCap = Math.max(0, prestigePerCategoryCap);
		prestigeTotalCap = Math.max(0, prestigeTotalCap);
		xpCurveBase = Math.max(1.0, xpCurveBase);
		xpCurveLinear = Math.max(0.0, xpCurveLinear);
		xpCurveQuadratic = Math.max(0.0, xpCurveQuadratic);
		vanillaXpToSkillXp = Math.max(0.0, vanillaXpToSkillXp);
		investmentBaseCost = Math.max(1, investmentBaseCost);
		investmentCostGrowth = Math.max(0, investmentCostGrowth);
		armorOvercapReductionCap = Math.min(0.99, Math.max(0.0, armorOvercapReductionCap));
		utilityXpMultiplier = Math.max(0.0, utilityXpMultiplier);
		combatXpMultiplier = Math.max(0.0, combatXpMultiplier);
		armorXpMultiplier = Math.max(0.0, armorXpMultiplier);
		miscXpMultiplier = Math.max(0.0, miscXpMultiplier);
		armorXpPerDamage = Math.max(0.0, armorXpPerDamage);
		dodgeChanceCap = Math.min(0.99, Math.max(0.0, dodgeChanceCap));
		lastBreathWindowTicks = Math.max(1, lastBreathWindowTicks);
		lastBreathHealth = Math.max(1.0F, lastBreathHealth);
		castleOfStoneTicks = Math.max(0, castleOfStoneTicks);
		castleOfStoneRestTicks = Math.max(1, castleOfStoneRestTicks);
		braceDelayTicks = Math.max(1, braceDelayTicks);
		braceCooldownTicks = Math.max(0, braceCooldownTicks);
		utilityFishingXp = Math.max(0.0, utilityFishingXp);
		utilityShearXp = Math.max(0.0, utilityShearXp);
		veinMiningMaxBlocks = Math.max(1, Math.min(4096, veinMiningMaxBlocks));
		timberMaxLogs = Math.max(1, Math.min(4096, timberMaxLogs));
		shovelMasteryBonus = Math.max(0.0, shovelMasteryBonus);
		excavationTreasureChancePer100Yield = Math.max(0.0, excavationTreasureChancePer100Yield);
		excavationTreasureBaseChance = Math.min(1.0, Math.max(0.0, excavationTreasureBaseChance));
		growthAuraRadius = Math.max(0, Math.min(16, growthAuraRadius));
		growthAuraHeight = Math.max(0, Math.min(8, growthAuraHeight));
		growthAuraIntervalTicks = Math.max(1, growthAuraIntervalTicks);
		growthAuraChance = Math.min(1.0, Math.max(0.0, growthAuraChance));
		growthAuraRandomTicks = Math.max(1, Math.min(16, growthAuraRandomTicks));
		masterShearingChance = Math.min(1.0, Math.max(0.0, masterShearingChance));
		masterShearingExtraDurability = Math.max(0, masterShearingExtraDurability);
		tripleCatchRolls = Math.max(1, Math.min(10, tripleCatchRolls));
		tripleCatchExtraDurability = Math.max(0, tripleCatchExtraDurability);
		combatXpPerDamage = Math.max(0.0, combatXpPerDamage);
		combatKillBonus = Math.max(0.0, combatKillBonus);

		if (combatEntityXpMultipliers == null) {
			combatEntityXpMultipliers = new java.util.LinkedHashMap<>();
		}

		executeThreshold = clamp01(executeThreshold);
		executeBossThreshold = clamp01(executeBossThreshold);
		armorBreakRatio = Math.max(0.0, armorBreakRatio);
		guaranteedCriticalPenalty = Math.max(0.0, guaranteedCriticalPenalty);
		marksmanStartDistance = Math.max(0.0, marksmanStartDistance);
		marksmanFullDistance = Math.max(marksmanStartDistance + 1.0, marksmanFullDistance);
		marksmanMaxBonus = Math.max(0.0, marksmanMaxBonus);
		piercingShotLevel = Math.max(0, Math.min(127, piercingShotLevel));
		straightShotMaxTicks = Math.max(1, straightShotMaxTicks);
		reinforcedShotDamage = Math.max(0.0, reinforcedShotDamage);
		lightningShockRatio = Math.max(0.0, lightningShockRatio);
		lightningShockRadius = Math.max(1.0, Math.min(32.0, lightningShockRadius));
		lightningShockCooldownTicks = Math.max(0, lightningShockCooldownTicks);
		lightningShockBounceTicks = Math.max(0, Math.min(100, lightningShockBounceTicks));
		lightningShockSparks = Math.max(1, Math.min(8, lightningShockSparks));
		lightningShockJumps = Math.max(1, Math.min(8, lightningShockJumps));
		storedImpactRatio = Math.max(0.0, storedImpactRatio);
		storedImpactCap = Math.max(0.0, storedImpactCap);
		storedImpactWindowTicks = Math.max(1, storedImpactWindowTicks);
		windImpactRadius = Math.max(0.5, Math.min(16.0, windImpactRadius));
		windImpactCooldownTicks = Math.max(0, windImpactCooldownTicks);
		windImpactDurabilityCost = Math.max(0, windImpactDurabilityCost);
		mediumShieldDisableRatio = Math.min(1.0, Math.max(0.0, mediumShieldDisableRatio));
		windImpactStrength = Math.max(0.0, windImpactStrength);
		brawlerDamage = Math.max(0.0, brawlerDamage);
		comboPerHit = Math.max(0.0, comboPerHit);
		comboCap = Math.max(0.0, comboCap);
		comboResetTicks = Math.max(1, comboResetTicks);
		disarmChance = clamp01(disarmChance);
		longReachBlocks = Math.max(0.0, longReachBlocks);
		impaleChance = clamp01(impaleChance);
		bleedTicks = Math.max(20, bleedTicks);
		auraSwordHits = Math.max(1, auraSwordHits);
		auraSwordRadius = Math.max(1.0, Math.min(16.0, auraSwordRadius));
		auraSwordPulseGapTicks = Math.max(1, auraSwordPulseGapTicks);
		predatorPerStack = Math.max(0.0, predatorPerStack);
		predatorMaxStacks = Math.max(0, predatorMaxStacks);
		predatorResetTicks = Math.max(1, predatorResetTicks);
		bloodlustPerStack = Math.max(0.0, bloodlustPerStack);
		bloodlustMaxStacks = Math.max(0, bloodlustMaxStacks);
		bloodlustTicks = Math.max(1, bloodlustTicks);
		overkillRatio = Math.max(0.0, overkillRatio);
		overkillRadius = Math.max(1.0, Math.min(16.0, overkillRadius));
		overkillMaxTargets = Math.max(1, overkillMaxTargets);
		firstStrikeBonus = Math.max(0.0, firstStrikeBonus);
		unrestrictedRiptideStrength = Math.max(0.0, unrestrictedRiptideStrength);
		chargeAttackTicks = Math.max(1, chargeAttackTicks);
		chargeAttackMultiplier = Math.max(1.0, chargeAttackMultiplier);
		lightningSummonRange = Math.max(4.0, Math.min(128.0, lightningSummonRange));
		lightningSummonCostRatio = clamp01(lightningSummonCostRatio);
		lightningSummonCooldownTicks = Math.max(0, lightningSummonCooldownTicks);
		catchThrowSpeed = Math.max(0.5, Math.min(5.0, catchThrowSpeed));
		assaultRifleMagazine = Math.max(1, Math.min(64, assaultRifleMagazine));
		chargedThrowSpeedMultiplier = Math.max(0.5, Math.min(3.0, chargedThrowSpeedMultiplier));
		chargedThrowPierce = Math.max(0, Math.min(127, chargedThrowPierce));
		chargedBurstScale = Math.max(1.0, Math.min(4.0, chargedBurstScale));
		chargedBurstPushScale = Math.max(0.0, Math.min(5.0, chargedBurstPushScale));
		chargedBurstLaunchSpeed = Math.max(0.0, Math.min(3.0, chargedBurstLaunchSpeed));
		chargedBurstExtraDurability = Math.max(0, chargedBurstExtraDurability);
		catchThrowDurabilityCost = Math.max(0, catchThrowDurabilityCost);
		chargedSwingDurabilityCost = Math.max(0, chargedSwingDurabilityCost);
		chargedThrowDurabilityCost = Math.max(0, chargedThrowDurabilityCost);
		stormCallBolts = Math.max(1, Math.min(12, stormCallBolts));
		stormCallRadius = Math.max(0.0, Math.min(16.0, stormCallRadius));
		stormCallGapTicks = Math.max(0, Math.min(40, stormCallGapTicks));
		stormCallCostMultiplier = Math.max(0.0, stormCallCostMultiplier);
		galeThrowPullRadius = Math.max(1.0, Math.min(16.0, galeThrowPullRadius));
		galeThrowPullStrength = Math.max(0.0, Math.min(4.0, galeThrowPullStrength));
		galeThrowPullTicks = Math.max(0, Math.min(40, galeThrowPullTicks));
		galeThrowBurstScale = Math.max(1.0, Math.min(4.0, galeThrowBurstScale));
		miscXpPerOrbPoint = Math.max(0.0, miscXpPerOrbPoint);
		ironStomachNutrition = Math.max(0, Math.min(20, ironStomachNutrition));
		ironStomachSaturation = Math.max(0.0, Math.min(20.0, ironStomachSaturation));
		xpInsuranceShare = clamp01(xpInsuranceShare);
		waterToWineMinTicks = Math.max(1, waterToWineMinTicks);
		waterToWineCutTicks = Math.max(1, waterToWineCutTicks);
		overdoseMaxBoost = Math.max(0, Math.min(10, overdoseMaxBoost));
		fuelEffectTicks = Math.max(1, fuelEffectTicks);
		overenchantMaxLevel = Math.max(1, Math.min(255, overenchantMaxLevel));
		stackOverflowMax = Math.max(1, Math.min(300, stackOverflowMax));
		return this;
	}

	private static double clamp01(double value) {
		return Math.min(1.0, Math.max(0.0, value));
	}
}
