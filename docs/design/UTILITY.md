**UTILITY SKILL SYSTEM  |  WORKING DESIGN SPEC**

**UTILITY SKILL SYSTEM**

Detailed Progression & Mechanics Specification

**Bloom Skills / Bloom+ - working terminology**

Version 0.1  |  October 16, 2026

Purpose: define the current Utility skill model in enough detail that it can be implemented, reviewed, and iterated without falling back to the old plugin-style progression. This document records the current design decisions, examples, formulas, reset behavior, Major abilities, and the post-100 XP system. It is a working specification: items explicitly marked TBD or provisional are intentionally not locked yet.

| **CORE IDEA** Utility is a broad 1-100 skill for tool-based gathering and practical work. Each level grants a local Utility Stat Point. Every 10 levels also grants a separate global Major Level. Utility stats shape the player's build; Major Levels buy powerful standalone abilities. Reaching Utility 100 unlocks a permanent XP Investment tab for overcap stat growth. |
| --- |

# 1. System at a Glance

| **Layer** | **What it does** | **Key rule** |
| --- | --- | --- |
| Utility Level (1-100) | Measures normal Utility progression. | Raised by Utility activity and can also be funded with vanilla XP. |
| Utility Stat Points | Builds the local Utility character. | 1 natural point per Utility level; allocate into Efficiency, Yield, or Preservation. |
| Major Levels | Global high-impact progression currency. | Earn 1 every 10 levels in a Major Skill; can be spent in any Major Skill tree. |
| Utility Majors | Standalone powerful abilities. | Mostly cost 1 Major Level; visible from the start; not connected to the three stat tracks. |
| XP Investment Tab | Permanent post-100 overcap growth. | Unlocks after Utility 100; XP buys extra stat points; never grants additional Major Levels. |
| Bloom+ / Full Reset | Finite New Game+-style reset. | Resets normal branch progress, refunds Major spending, and can add limited prestige Major Levels. |

## 1.1 Terminology

Utility Skill: the broad 1-100 skill category covered by this document.

Utility Stat Point: a local point placed into Efficiency, Yield, or Preservation.

Major Level: a separate global currency earned from 10-level milestones in Major Skills.

Utility Major: a powerful standalone Utility ability purchased with Major Levels (for example Vein Mining).

XP Funding: spending vanilla Minecraft XP before level 100 to help advance the normal Utility level.

XP Investment: the post-100 system that buys permanent overcap Utility Stat Points.

Bloom+: working name for the full reset / New Game+ state after the first prestige reset.

## 1.2 Design Intent

Utility should be easy to understand at a glance, strong enough to be desirable in almost any modpack, and flexible enough that players at the same Utility level can feel different. Normal progression should already be powerful. Post-100 progression is intentionally allowed to become excessive or “broken” because it represents endgame overinvestment rather than baseline balance.

The Utility UI should not resemble a giant branching skill web. The core build is three simple stat tracks plus separate floating Utility Major abilities. The player chooses how to distribute points; the Majors are nearby, visible, and tempting, but they are not linked to the stats by prerequisite lines.

# 2. Core Utility Progression (Levels 1-100)

## 2.1 How Utility Levels Are Earned

Utility XP is earned through appropriate tool-based activity. At minimum, mining, digging, and logging contribute immediately. The broader Utility umbrella is intended to cover other compatible gathering/tool actions such as farming, shearing, fishing, and equivalent modded actions when configured.

| **UNIVERSAL MODPACK RULE** XP sources should be tag/data/config driven where practical. A modpack should be able to classify modded ores, trees, tools, crops, fishing content, or other gathering actions as Utility-compatible without hardcoding each mod into the core system. |
| --- |

## 2.2 Vanilla XP Can Also Fund Utility Before 100

Players are not required to perform one activity all the way to level 100. Vanilla Minecraft XP can be intentionally spent to advance a chosen skill, including Utility. This gives the player a second route when the activity grind stops being fun or when they deliberately build an XP farm later in progression.

Activity route: perform Utility actions -> earn Utility XP -> Utility level rises.

XP route: spend vanilla XP into Utility -> Utility progress rises -> normal Utility levels are earned.

Both routes are allowed before Utility 100.

Exact vanilla-XP-to-skill-XP conversion and scaling are TBD. The cost may scale with higher Utility levels.

The design intentionally expects a player-accessible XP farming route later; that farm is outside this Utility specification.

## 2.3 One Natural Stat Point Per Level

Every Utility level immediately grants 1 Utility Stat Point. There is no need to wait for milestones. A player at Utility 37 has earned 37 natural Utility Stat Points; Utility 100 provides 100 natural Utility Stat Points total.

Natural points are distributed among three stats: Efficiency, Yield, and Preservation. There is no forced even distribution. A Utility 100 player can be a generalist, a two-stat hybrid, or a full 100-point specialist.

| **Utility 100 example** | **Efficiency** | **Yield** | **Preservation** |
| --- | --- | --- | --- |
| Balanced-ish generalist | 34 | 33 | 33 |
| Speed specialist | 100 | 0 | 0 |
| Resource specialist | 0 | 100 | 0 |
| Tool-life specialist | 0 | 0 | 100 |
| Hybrid example | 50 | 50 | 0 |

## 2.4 Every 10 Levels Grants a Major Level

Utility has a second reward cadence. At Utility 10, 20, 30, and so on through 100, the player earns 1 Major Level. These Major Levels are completely separate from Utility Stat Points.

| **Utility Level** | **Natural Utility Stat Points earned** | **Major Levels earned from Utility (cumulative)** |
| --- | --- | --- |
| 10 | 10 | 1 |
| 20 | 20 | 2 |
| 50 | 50 | 5 |
| 100 | 100 | 10 |

Major Levels are global. A Major Level earned at Utility 10 does not belong to Utility. The player may spend it on Utility, Combat, Companions, or any other Major Skill tree that exists. Likewise, a Major Level earned from Combat may be used to buy Vein Mining.

| **IMPORTANT** The normal 10-level Major Level rewards are one-time progression rewards. Bloom+ resets do not let the player repeatedly farm the same 1-100 milestones for infinite Major Levels. |
| --- |

# 3. Utility Core Stats

Utility currently has exactly three core stats. They are intentionally simple linear allocations rather than separate branches. The player can toggle each stat on/off without losing points. This matters once values become extreme: too much mining speed can feel awkward, and excessive extra drops or treasure can become annoying during routine building.

## 3.1 Efficiency

Efficiency represents how quickly relevant Utility actions are performed. The current working value is a flat +1% action-speed multiplier per point. This keeps the stat easy to read and gives strong but understandable scaling.

| **Efficiency** | **Working speed factor** | **Interpretation** |
| --- | --- | --- |
| 0 | 1.00x | Normal Utility action speed. |
| 25 | 1.25x | 25% faster relevant Utility actions. |
| 50 | 1.50x | 50% faster. |
| 100 | 2.00x | Double-speed baseline for a fully natural all-in build. |
| 200 | 3.00x | Post-100 overcap territory. |
| 500 | 6.00x | Intentionally extreme endgame investment. |

Implementation note: Minecraft block-breaking has threshold/tick behavior, so the implementation should apply Efficiency as a consistent multiplier to relevant action speed rather than trying to subtract a fixed number of ticks. The exact integration should be tested so 100 Efficiency feels approximately like a real 2x increase across supported tools.

| **TOGGLE EXAMPLE** A player with extreme Efficiency may want to disable it while carefully shaping a build. Turning Efficiency off suppresses its effect but does not remove or refund any points. |
| --- |

## 3.2 Yield

Yield increases resource output by duplicating the final eligible drop result after normal loot calculations have finished. Fortune and compatible modded loot modifiers calculate first; Yield then duplicates what the player actually earned. This is intentionally a strong synergy rather than a replacement for Fortune.

Rule: every full 100 Yield guarantees one additional copy of the final eligible drop result. The remainder below the next 100 is the percentage chance to gain another complete copy.

| **Yield** | **Bonus-copy behavior** | **Example if Fortune produces 3 diamonds** |
| --- | --- | --- |
| 25 | 25% chance for one full extra copy | 3 normally; 25% chance to become 6. |
| 50 | 50% chance for one full extra copy | 3 or 6. |
| 100 | 1 guaranteed full extra copy | 3 -> 6. |
| 150 | 1 guaranteed + 50% chance for another | 6 or 9. |
| 200 | 2 guaranteed full extra copies | 3 -> 9. |
| 300 | 3 guaranteed full extra copies | 3 -> 12. |

General formula: guaranteed extra copies = floor(Yield / 100). The remainder (Yield mod 100) is the percentage chance for one additional full copy. Total output therefore starts with the original final drop, then adds the Yield copies.

Yield should operate only on eligible drops. The system needs tags/configuration/exclusions so modpacks can prevent duplication of inappropriate outputs. Context-specific Utility actions may translate Yield differently when raw item duplication would be nonsensical. The shovel, for example, can translate its Yield behavior into treasure/discovery rather than simply creating more dirt.

| **FORTUNE STACKING** If Fortune produces 3 coal and Yield is 100, the result is 6 coal. If Fortune produces 6 redstone dust and Yield is 100, the result is 12. Yield acts after the Fortune result. |
| --- |

## 3.3 Preservation

Preservation reduces durability consumption, but it does not merge directly into the vanilla Unbreaking calculation. Instead, it provides a second independent save attempt only when Unbreaking fails to save the durability point.

The tool attempts to lose durability.

Vanilla Unbreaking runs normally.

If Unbreaking succeeds, no durability is lost and Preservation does nothing.

If Unbreaking fails, Preservation makes its own backup save roll.

Durability is consumed only if both systems fail to save it.

Current working Preservation curve: backup save chance = Preservation / (Preservation + 100). This gives strong natural mastery at 100 while allowing post-100 growth to keep improving without ever reaching a literal 100% save rate.

| **Preservation** | **Backup save chance after Unbreaking fails** | **Approx. tool-life effect without Unbreaking** |
| --- | --- | --- |
| 25 | 20.0% | ~1.25x |
| 50 | 33.3% | ~1.50x |
| 75 | 42.9% | ~1.75x |
| 100 | 50.0% | ~2.00x |
| 200 | 66.7% | ~3.00x |
| 300 | 75.0% | ~4.00x |
| 500 | 83.3% | ~6.00x |

This means a player who naturally invests all 100 Utility points into Preservation gets a true second 50/50 durability-save chance whenever Unbreaking does not protect the tool. Post-100 XP investment can push that backup chance much higher, but the formula approaches 100% asymptotically rather than granting perfectly unbreakable tools.

## 3.4 Stat Toggles

Efficiency, Yield, and Preservation each have an independent on/off toggle. The toggle affects behavior only; it does not change ownership or allocation. This is a quality-of-life requirement, not a balance penalty.

Efficiency off: suppress speed bonuses when precision is more useful than raw speed.

Yield off: suppress extra output/treasure when the player wants clean inventory behavior.

Preservation off: normally unnecessary, but available for consistency and debugging/edge cases.

The system does not currently require a separate normal Utility-stat respec. A full Bloom+ reset clears the normal cycle progression, while permanent XP-funded overcap investment survives as described later.

# 4. Utility Majors

Utility Majors are the “I want that” abilities. They are deliberately separate from Efficiency, Yield, and Preservation. In the UI they should appear as floating standalone nodes/cards near the Utility panel with no direct connecting lines to the three stats.

Most Utility Majors cost 1 Major Level.

All Utility Majors are visible from the beginning, including locked or requirement-gated future abilities.

Most current Utility Majors do not need a Utility-level requirement. A player may spend a Major Level earned elsewhere to buy one.

Future unusual/strong Majors may require prerequisites and/or more than 1 Major Level.

Major abilities should be strong. Balance should prefer natural work/resource costs over arbitrary nerfs.

## 4.1 Vein Mining - Pickaxe Major

Vein Mining breaks connected eligible resource blocks as one convenience action. Its value is primarily time saved, not free work.

Cost: 1 Major Level.

Toggleable.

Each affected block pays the normal relevant durability/action cost.

Normal drops are still calculated per block; Yield may then apply to eligible results.

A configurable maximum connected-block count should exist to protect performance and prevent pathological modded veins from processing thousands of blocks at once.

## 4.2 Timber - Axe Major

Timber processes a valid connected tree rather than requiring the player to break every log manually.

Cost: 1 Major Level.

Toggleable, especially important around decorative log builds.

Pays normal durability/work cost for the logs processed.

Tree detection should avoid treating arbitrary player structures as valid trees whenever reasonably possible.

## 4.3 Shovel Mastery - Shovel Major

Shovel Mastery is intentionally simpler than Vein Mining or Timber. It is the generalist tool Major rather than a forced complicated excavation tree.

Cost: 1 Major Level.

Current concept: approximately +20% effectiveness to all applicable Utility stats while using a shovel.

Efficiency and Preservation scale normally through the shovel.

Yield translates into shovel-appropriate treasure/discovery behavior instead of simply multiplying dirt/sand output.

## 4.4 Growth Aura - Hoe Major

Growth Aura reuses the crop-growth concept from the existing Bee pet effect. While active and appropriately using/holding a hoe, the player accelerates nearby valid crop growth.

Cost: 1 Major Level.

Toggleable.

Should remain tied to active hoe use/equipping rather than becoming a permanent aura after purchase.

Should not burn durability every growth tick; the equipment/use condition is the primary limitation.

Compatible modded crops should be supported through tags/data/configuration where possible.

## 4.5 Master Shearing - Shears Major

Master Shearing keeps the fun of a true repeated coin toss. A successful shear always gives the normal drops. After the harvest, the system rolls a 50% chance that the target remains in its shearable state and can immediately be sheared again.

Cost: 1 Major Level.

Every repeated attempt gets another independent 50% roll; there is intentionally no once-per-animal protection.

Drawback: each shearing attempt consumes approximately 2x normal shear durability.

Yield can still affect the eligible sheared-resource result separately.

Implementation should target generalized shearable interactions where practical, not only vanilla sheep.

## 4.6 Triple Catch - Fishing Major

Triple Catch makes one successful fishing action generate three independent fishing loot rolls rather than cloning one caught item three times.

Cost: 1 Major Level.

One catch -> three loot rolls from the active fishing loot system.

Natural cost: approximately 3x normal fishing-rod durability on a successful catch.

Independent rolls allow vanilla treasure, fish, junk, and modded fishing loot to participate naturally.

# 5. Major Level Economy

## 5.1 Major Levels Are Global

Major Levels belong to the character, not to the skill that generated them. Utility contributes up to 10 normal Major Levels from its 10/20/.../100 milestones, but those points can be spent anywhere.

| **EXAMPLE** The player reaches Combat 10 and earns a Major Level. Utility is still level 3. Because Vein Mining has no Utility-level requirement in the current draft, the player can spend that Combat-earned Major Level on Vein Mining immediately. |
| --- |

## 5.2 Variable Major Costs

Most Utility Majors are intended to cost 1 Major Level. The broader framework supports abilities costing 2 or more Major Levels when the effect justifies it. Extremely strong abilities can also use prerequisites or cross-skill requirements later.

## 5.3 Visibility and Requirements

Major abilities should remain fully visible from the beginning. A locked Major should show its cost and requirements instead of disappearing. Utility itself is intended to be relatively free-form; stricter prerequisite chains are more appropriate for future high-power or cross-skill abilities.

## 5.4 Resetting Major Choices

Whenever Major selections are reset, 100% of the spent Major Levels return to the player. Major Levels are not destroyed by changing a build. The current design guarantees full refund behavior during Bloom+; whether the final UI also exposes a lightweight Major-only respec is an implementation choice that can be decided later.

# 6. XP Investment Tab (Post-100)

Reaching Utility 100 for the first time unlocks a separate XP Investment branch/tab. This is intentionally hidden or unavailable before mastery so the normal 1-100 experience stays clear.

Once unlocked, the XP Investment tab remains permanently available even after a Bloom+ reset.

Vanilla XP is spent here to buy additional Utility Stat Points beyond the 100 natural points.

XP Investment does not increase the Utility level above 100.

XP Investment never grants additional Major Levels.

XP-funded overcap points are permanent and survive Bloom+ resets.

The overcap points may push Efficiency, Yield, and Preservation beyond 100; there is intentionally no normal hard cap.

## 6.1 XP Cost Curve

The exact XP price is TBD. The desired shape is endgame-friendly: the first post-100 purchases should be relatively accessible because a player at this stage can obtain the first ~50 vanilla XP levels quickly. Costs should then scale upward so extreme overcap investment becomes a meaningful XP sink.

| **IMPLEMENTATION GUIDANCE** Internally, raw XP points are preferable to charging “levels” directly because vanilla level costs are nonlinear. The UI can still present an understandable cost estimate, but the purchase logic should use stable raw-XP values. |
| --- |

## 6.2 Example Post-100 Character

| **Stage** | **Efficiency** | **Yield** | **Preservation** | **Meaning** |
| --- | --- | --- | --- | --- |
| Natural Utility 100 | 50 | 30 | 20 | 100 natural stat points allocated. |
| After XP investment | 130 | 85 | 45 | 80 permanent XP-funded points added. |
| Extreme late game | 220 | 180 | 140 | Intentionally beyond normal balance; toggles become important. |

The exact allocation above is illustrative. The core rule is that XP-funded stat growth is permanent and can eventually make Utility dramatically stronger than its natural level-100 form.

# 7. Bloom+ / Full Reset / Prestige

The full reset is a finite New Game+-style system rather than an infinite prestige farm. Its purpose is to let the player rebuild normal skills while slowly increasing the permanent Major Level ceiling.

## 7.1 What Resets

Normal skill/branch levels for the new progression cycle.

Normal 1-100 stat points tied to those reset levels are re-earned with the new cycle.

Purchased Major abilities are cleared from the loadout and their spent Major Levels are refunded 100%.

## 7.2 What Survives

The character's owned Major Levels and any unspent Major Levels.

Permanent prestige bonus Major Levels already earned.

The fact that normal 10-level Major Level milestones were already claimed; they cannot be farmed again.

The unlocked XP Investment tab for skills that previously reached 100.

Post-100 XP-funded overcap stat points and their current allocation.

Hidden titles/achievement-style unlocks, unless a future design explicitly says otherwise.

## 7.3 Prestige Bonus Major Levels

A Bloom+ reset can add bonus Major Levels based on how far each individual branch reached before the reset. Branch levels do not combine across skills.

50 levels in one branch -> that branch can contribute +1 prestige Major Level.

100 levels in one branch -> that branch can contribute +2 prestige Major Levels total.

A branch can contribute at most +2 prestige Major Levels across the character's lifetime.

The character can gain at most +6 prestige Major Levels total across all resets.

If the first reset earns only part of the +6, later resets can earn the remaining amount by meeting unclaimed branch thresholds.

Utility 25 + Combat 25 does not count as 50. The threshold must exist inside one branch.

## 7.4 Prestige Examples

| **Reset state** | **Branch results** | **Prestige award** | **Lifetime prestige total** |
| --- | --- | --- | --- |
| First reset | Utility 100; Combat 50; another branch below 50 | Utility +2, Combat +1 | 3 / 6 |
| Later reset | Combat reaches 100; another branch reaches 100 | Combat has +1 remaining; other branch +2 | 6 / 6 |
| Future resets | Any branch levels | No further bonus once lifetime cap is reached | 6 / 6 |

Working presentation idea: the initial system can be presented as “Bloom Skills,” while the post-reset state becomes “Bloom+.” Additional reset counts may be displayed later, but the lifetime prestige bonus cap remains 6.

# 8. Hidden Behavior Counters, Titles & Minor Perks

The system may quietly track how the player actually behaves, but these counters should not control the main build. They remain hidden until a threshold is achieved.

No progress bar is shown before the hidden condition is completed.

When achieved, the game can reveal a title and/or a minor passive perk.

Titles/perks should be equippable/selectable rather than making every hidden unlock permanently active at once.

These rewards are intentionally minor compared with Utility Majors and do not replace the main progression system.

Exact hidden counters, titles, and passive effects are intentionally deferred. This system exists as a place for behavior-driven flavor without restricting player choice.

# 9. Utility UI Behavior

The Utility screen should be simple enough that the player understands the system without reading a guide. The three local stats are the central build controls. Utility Majors sit around them as separate standalone unlocks.

| **UI area** | **Content** |
| --- | --- |
| Header | Utility level, Utility XP progress, available natural Utility Stat Points, and global Major Level balance. |
| Core stats | Efficiency, Yield, Preservation. Each shows total value and an individual on/off toggle. |
| Utility Majors | Vein Mining, Timber, Shovel Mastery, Growth Aura, Master Shearing, Triple Catch. Floating/separate, no connecting branch lines. |
| Major cards | Always visible; show cost, toggle state where applicable, and requirements if a future Major has them. |
| XP Funding (pre-100) | A clear way to spend vanilla XP toward normal Utility progress. Exact control/placement TBD. |
| XP Investment tab | Hidden until Utility reaches 100 once; then permanently available. |
| Hidden titles | Do not appear as incomplete counters. They appear only after the player actually unlocks them. |

## 9.1 Simplified Layout Concept

A rough information hierarchy (not final art):

| UTILITY - Level 47 XP: [progress bar] Natural Stat Points Available: 0        Global Major Levels: 3  Efficiency     20    [ON/OFF] Yield          19    [ON/OFF] Preservation    8    [ON/OFF]  Floating Utility Majors (not connected to the stat rows): Vein Mining │ Timber │ Shovel Mastery │ Growth Aura │ Master Shearing │ Triple Catch  [XP Investment] tab appears only after Utility 100 has been reached once. |
| --- |

# 10. Universal Modpack / Implementation Requirements

Utility is intended to sit inside large modpacks rather than assume every tool, block, crop, fish, or creature belongs to the same mod. The implementation should therefore separate gameplay logic from specific content definitions.

Use tags/data/configuration for eligible ores, logs, crops, shearables, fishing content, and tool categories where practical.

Allow modpack authors to add or exclude content without editing core code.

Major abilities should operate on generalized categories/events rather than hardcoded vanilla-only lists.

Yield eligibility must be configurable because duplicating every possible modded loot output would be unsafe or undesirable.

Vein Mining and Timber need configurable safety limits and compatibility handling.

Optional mods should fail gracefully when absent.

The post-100 and prestige systems are character progression systems and should remain independent of any one content mod.

# 11. Worked Player Examples

## 11.1 Natural Utility Specialist

A player reaches Utility 100 through mining, logging, digging, and some XP funding. They allocate all 100 natural points into Yield. Fortune produces 4 diamonds from an ore. Yield 100 duplicates the final eligible result once, producing 8 diamonds. The player also earned 10 Major Levels from Utility's 10-level milestones, but may have spent some or all of those on completely different Major Skills.

## 11.2 Cross-Skill Major Spending

A player reaches Combat 20 and earns 2 Major Levels from Combat. They do not want either current Combat Major, so they open Utility and spend 1 of those Major Levels on Vein Mining. The source of the Major Level does not restrict where it is spent.

## 11.3 Preservation With Unbreaking

A tool first receives the normal Unbreaking check. If Unbreaking saves the durability, Preservation never rolls. If Unbreaking fails and the player has Preservation 100, Preservation then rolls a 50% backup save. The two systems therefore remain mechanically distinct even though the combination can make high-end tools last an extremely long time.

## 11.4 Post-100 Overcap

After reaching Utility 100 once, the player opens XP Investment and buys 120 permanent extra Utility Stat Points over time. They place 80 into Efficiency and 40 into Preservation. If the player later enters Bloom+, those 120 XP-funded points survive even though their normal Utility level and normal 1-100 stat allocation reset.

## 11.5 Bloom+ Progression

Before the first reset, the player has Utility 100 and Combat 50. Utility qualifies for +2 prestige Major Levels and Combat qualifies for +1. The reset refunds all currently spent Major Levels, resets the normal skill progression, preserves XP-funded overcap points, and awards 3 prestige Major Levels. Later, the player reaches Combat 100 and another branch 100. Combat has one unclaimed prestige point remaining and the new branch has two, bringing the lifetime prestige bonus to the hard cap of 6.

# 12. Items Intentionally Not Finalized

Exact Utility XP curve from level 1 to 100.

Exact vanilla-XP conversion cost for funding Utility before level 100.

Exact post-100 XP price curve for buying permanent Utility Stat Points.

Final technical implementation of Efficiency across Minecraft break-speed thresholds and unusual modded tools.

Exact shovel treasure tables/behavior and how Yield translates into treasure.

Whether a lightweight Major-only respec exists in addition to the full Bloom+ reset.

Possible automatic Utility milestone rewards at 25/50/75/100 beyond the existing stat points and Major Level cadence.

Additional Utility Majors beyond the current six.

Hidden behavior titles/passives and their thresholds.

Future cross-skill Majors, prerequisite chains, and derived/hybrid branches.

# 13. Current Design Rules to Preserve

Utility 100 should already feel excellent; post-100 exists to become excessive, not to make level 100 finally usable.

The player owns their Major Levels. A Major Level can be spent outside the skill that generated it.

Most Utility Majors should remain affordable at 1 Major Level.

Strong abilities should prefer natural work costs over arbitrary “fun tax” penalties.

Normal Utility build choice comes from stat allocation; Utility Majors are separate and visually unconnected.

Fortune and Yield intentionally synergize.

Preservation is a backup roll after Unbreaking fails, not an additive Unbreaking stat.

Post-100 XP stat investment is permanent and cannot generate additional Major Levels.

Bloom+ is finite: per-branch prestige contribution is capped, and total bonus Major Levels are capped at +6.

The system should be modpack-friendly and configurable rather than hardcoded around vanilla content.

**END OF CURRENT UTILITY SPECIFICATION**

Working design - values marked provisional can change during implementation/balance testing

Page