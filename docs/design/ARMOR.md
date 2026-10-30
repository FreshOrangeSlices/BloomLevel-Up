ARMOR SKILL SYSTEM  |  WORKING DESIGN SPEC

Armor Skill System

Detailed Design Specification

*Universal Minecraft Skill Framework • Armor / Defense Category*

Working design snapshot • October 17, 2026

| **CORE IDENTITY** Armor is the body/defense/survivability category. Its base stats are intentionally simple; its Majors are where defensive play becomes strange, situational, and build-defining. Armor should support naked builds, shield builds, armor-set builds, environmental survival, damage-type counters, death denial, and deliberate counters to offensive Combat mechanics. |
| --- |

## Design pillars

Player-first defense: Armor represents the character's body and defensive capability, not only worn chestplates or material tiers.

Simple stats, mechanical Majors: Vitality, Defense, Dodge, and Durability provide the predictable numeric layer; Majors should mostly change how survival works.

Strong combinations are intentional. Defensive layers stack multiplicatively so powerful builds remain possible without simple additive percentages instantly reaching immunity.

Grinding is allowed. Environmental damage, cactus farming, shield blocking, and repeated defensive play are valid ways to progress Armor.

Modpack interoperability is required: weapon categories, elemental damage, negative effects, and armor classes should use tags/config rather than vanilla-only hard-coding.

Armor should answer Combat without becoming PvP-only. Many counters are useful against mobs, bosses, modded threats, projectiles, status effects, and environmental hazards as well.

## Status legend

| **Status** | **Meaning** |
| --- | --- |
| Locked in | Current design direction; safe to implement unless a later revision explicitly changes it. |
| Implementation detail | Behavior is decided, but exact code ordering, API hooks, or compatibility handling may still need engineering work. |
| TBD | Concept is accepted, but one or more numbers, tags, edge cases, or persistence rules still need a later pass. |

# 1. Armor Progression and XP

Armor is a Major Skill with normal progression from Level 1 to Level 100. Each normal Armor level grants one Armor Stat Point. Every 10 Armor levels grants one global Major Level, following the same shared progression framework as the other Major Skills.

| **Layer** | **Armor rule** |
| --- | --- |
| Armor Level | 1-100 normal progression. |
| Natural stat points | 1 point per Armor level; 100 natural points at Armor 100. |
| Major Levels | 1 global Major Level at Armor 10, 20, 30, and so on through 100. |
| XP Investment | Permanently unlocks after first reaching Armor 100; spends vanilla XP on permanent overcap Armor Stat Points. |
| Bloom+ / full reset | Normal Armor levels and natural allocations reset; XP-funded overcap points survive. |
| Prestige bonus | Up to +2 bonus Major Levels from Armor under the shared +1 per 50-level milestone rule. |

## 1.1 Armor XP sources

Armor progression is based on the player being subjected to danger and using defensive mechanics. The system intentionally does not need aggressive anti-farm protection: repetitive hazards such as cactus damage are valid progression if the player chooses to grind that way.

| **Defensive event** | **XP multiplier** | **Notes** |
| --- | --- | --- |
| Ordinary damage received | 1.0x | Standard Armor XP source. Environmental damage may count. |
| Successful shield block | 1.5x | Blocking is rewarded more than simply face-tanking. |
| Successful Dodge | 2.0x | A clean defensive success earns the highest current multiplier. |

| **IMPLEMENTATION DETAIL** For a Dodge or a fully blocked hit, the event can still award Armor XP even though health damage is zero. The exact damage-value basis used to calculate that XP (for example, pre-mitigation would-have-been damage) is still an implementation detail; the 1.0x / 1.5x / 2.0x multipliers are locked. |
| --- |

## 1.2 Naked progression is valid

Armor XP and the four base Armor stats do not require worn armor. A player may level Armor while naked. Worn equipment only matters for mechanics that explicitly inspect or consume equipment, such as Fitting Armor, Absorb, and Armor Durability preservation.

## 1.3 XP Investment after Level 100

The Armor XP Investment tab is hidden until Armor first reaches Level 100.

Once unlocked, the tab remains permanently available even after a full reset.

Vanilla Minecraft XP can purchase additional permanent Armor Stat Points beyond the natural 100-point pool.

XP-funded overcap points survive Bloom+/full resets.

XP Investment does not generate additional natural Major Levels.

# 2. Armor Core Stats

Armor currently has four core stats: Vitality, Defense, Dodge, and Durability. Natural level progression provides 100 total stat points, and post-100 XP Investment can push the same stats far beyond their normal range.

| **Stat** | **100-point target** | **Post-100 behavior** |
| --- | --- | --- |
| Vitality | +10 hearts maximum health | Continues linearly. |
| Defense | +10 innate armor points / 5 full armor icons | Continues linearly; armor beyond the vanilla cap gains a custom overcap benefit. |
| Dodge | 30% main Dodge chance | Normal scaling until 40%, then extremely steep diminishing returns. |
| Durability | Approximately a virtual Unbreaking III backup layer | Can continue beyond the level-100 strength; exact point-to-virtual-level mapping is TBD. |

## 2.1 Vitality

Vitality directly increases maximum health. The scaling is deliberately simple: 100 Vitality grants +10 hearts. The same linear relationship continues after 100.

| **Vitality** | **Bonus maximum health** | **Example total from vanilla 10-heart base** |
| --- | --- | --- |
| 100 | +10 hearts | 20 hearts |
| 200 | +20 hearts | 30 hearts |
| 300 | +30 hearts | 40 hearts |

## 2.2 Defense

Defense grants innate armor that functions even with no equipment. At 100 Defense, the player gains +10 armor points, equal to five full armor icons. Defense remains linear beyond 100.

| **Defense** | **Innate armor** |
| --- | --- |
| 20 | +2 armor points / 1 full armor icon |
| 50 | +5 armor points / 2.5 armor icons |
| 100 | +10 armor points / 5 armor icons |
| 200 | +20 armor points / 10 armor icons |

### Armor overcap rule

Minecraft's normal armor system effectively tops out around 20 armor points. This system must not allow higher Defense investment to become wasted. Once the player's total effective armor exceeds 20 armor points, every 1 armor point above 20 grants an additional 0.5% damage reduction.

| **Total armor** | **Armor above 20** | **Extra overcap reduction** |
| --- | --- | --- |
| 22 | 2 | 1% |
| 30 | 10 | 5% |
| 40 | 20 | 10% |
| 60 | 40 | 20% |

| **STACKING RULE** The overcap reduction is another multiplicative defensive layer, not an additive percentage. Example: Heavy Fitting Armor (30% reduction) plus 10% armor-overcap reduction resolves 100 incoming damage as 100 -> 70 -> 63, not as a single 40% subtraction. |
| --- |

| **TECHNICAL EDGE CASE** Extremely high Defense - especially with Titan Hide - may eventually approach absurd damage reduction. The exact hard technical safeguard against a literal 100% overcap layer is still an implementation detail and should not weaken normal progression. |
| --- |

## 2.3 Dodge

Dodge completely avoids eligible direct attacks. At 100 Dodge, the main Dodge chance is 30%. Dodge applies to direct attacks such as melee hits, arrows, projectiles, thrown tridents, and compatible direct modded attacks.

A successful Dodge behaves like a miss: no health damage, no knockback, and no normal on-hit consequences from that attack.

Dodge does not normally avoid fall damage, fire ticks, poison ticks, drowning, starvation, void damage, or other environmental/ongoing damage.

Successful Dodge grants 2.0x Armor XP relative to ordinary damage-taking.

### Dodge soft cap

Main Dodge scales normally until the computed chance reaches 40%. After 40%, additional Dodge investment is only 10% as effective.

| **Range** | **Gain per Dodge point** |
| --- | --- |
| Before 40% main Dodge | +0.3 percentage points per point |
| After 40% main Dodge | +0.03 percentage points per point (90% reduced effectiveness) |

This keeps post-100 Dodge useful indefinitely while making very high avoidance extremely expensive. Fitting Armor's Light-style Dodge is a separate backup roll and does not push the main Dodge percentage through this soft cap.

## 2.4 Durability

Durability preserves worn armor and shields. It is separate from Utility Preservation, which covers tools and other Utility equipment.

Vanilla Unbreaking resolves first.

If vanilla Unbreaking would still consume durability, Armor Durability performs a second backup Unbreaking-style check.

At 100 Durability, the backup layer should be approximately equivalent to virtual Unbreaking III.

Post-100 investment can extend the backup layer beyond the Level-100 target.

Shield preservation logic can be tuned separately instead of blindly copying the worn-armor path.

| **TBD** The exact conversion from Durability stat points to virtual Unbreaking level/probability is not yet locked. The required target is that 100 Durability feels approximately like a second Unbreaking III-style layer. |
| --- |

# 3. Armor Major Framework

Armor Majors are the build-defining layer. The current pool intentionally mixes general defense, situational counters, reactive mechanics, survival triggers, equipment sacrifice, and a wildcard unarmored build.

| **Major** | **Cost** | **Role** |
| --- | --- | --- |
| Fitting Armor | 2 | Dynamic Light / Medium / Heavy armor style |
| Protein! | 2 | Permanent meat-driven maximum-health growth |
| Iron Skin | 7 | Permanent Resistance I |
| Brace! | 2 | Conditional Resistance II while planted |
| Parkour | 2 | Chance to negate fall damage |
| Elemental Skin | 1 | 50% elemental/drowning damage reduction |
| Thick Blood | 1 | Shorter Poison/Wither-style duration |
| Vibranium | 2 | Blade/slashing damage counter |
| Adamantium | 2 | Blunt damage counter |
| Reflect | 2 | Projectile deflect/catch |
| Rebound | 1 | Chance to reflect melee damage |
| Unyielding | 2 | Chance to reject negative status applications |
| Last Breath | 2 | Lethal-hit survival ladder |
| Castle of Stone | 2 | Rest-recharged invulnerability after Last Breath |
| Absorb | 2 | Redirect health damage into armor durability |
| Titan Hide | 3 | Wildcard fully-unarmored defense build |
| TOTAL | 35 | Current Armor Major pool cost |

| **MAJOR ECONOMY** The current Armor Major pool costs 35 Major Levels in total. The broader character has a shared lifetime Major economy across all four core categories, so even a player who heavily specializes into Armor is giving up substantial investment elsewhere. |
| --- |

# 4. Fitting Armor and Armor Styles

## 4.1 Fitting Armor - 2 Major Levels

Fitting Armor automatically detects the armor style represented by the player's equipped set. The player does not manually select Light, Medium, or Heavy. The Major only activates when all four armor slots are filled.

| **Class** | **Vanilla materials** | **Weight value** |
| --- | --- | --- |
| Light | Leather, Chainmail | 1 |
| Medium | Gold, Iron | 2 |
| Heavy | Diamond, Netherite | 3 |

For mixed sets, average the weight value of all four equipped pieces:

| **Average weight** | **Active style** |
| --- | --- |
| 1.00-1.49 | Light |
| 1.50-2.49 | Medium |
| 2.50-3.00 | Heavy |

All four armor slots must be occupied. Any empty armor slot means Fitting Armor has no active style.

Innate Defense stat armor does not affect the classification.

Modded armor is assigned through configurable Light / Medium / Heavy tags or lists.

Fitting Armor and Titan Hide may both be owned; their activation conditions naturally prevent simultaneous use.

## 4.2 Light style

Grants Feather Falling I-equivalent fall protection.

Grants a separate 10% backup Dodge roll.

The player's normal Dodge roll resolves first. If it fails, Light performs an independent 10% Dodge check.

Because the 10% roll is separate, it does not directly increase the main Dodge stat or push it through the 40% soft cap.

Example: 30% main Dodge followed by a separate 10% Light roll produces 37% effective total avoidance, not 40%.

## 4.3 Medium style

15% reduced damage taken.

Shield-disable duration is reduced by 50%.

Medium is intended as the controlled / active-defense style rather than a weaker Heavy style.

## 4.4 Heavy style

30% reduced damage taken.

Grants strong knockback resistance; exact knockback value is still TBD.

Knockback resistance is intentionally not pure upside: staying planted can keep the player dangerously close to an enemy.

# 5. Defensive and Counter Majors

## 5.1 Protein! - 2 Major Levels

After acquiring Protein!, eating meat permanently grows the player's maximum health.

Every 20 qualifying meat items eaten grants +1/2 heart.

Maximum Protein! bonus: +10 hearts.

Reaching the cap therefore requires 400 qualifying meat consumptions.

The counter begins only after Protein! is acquired.

Vanilla and modded meat should be recognized through tags/configuration.

| **PERSISTENCE TBD** Protein! is described as permanent growth, but the exact behavior when the Major is refunded, toggled off, or the player uses Bloom+ must be defined before implementation to prevent a respec exploit. |
| --- |

## 5.2 Iron Skin - 7 Major Levels

Iron Skin grants permanent vanilla-style Resistance I while the Major is active. The cost is intentionally high because it provides universal, always-on mitigation rather than a narrow counter.

## 5.3 Brace! - 2 Major Levels

Crouch for 1 second to enter the braced state.

While braced, gain Resistance II.

Voluntary player movement or jumping ends Brace.

External displacement, including enemy knockback, does not cancel Brace.

Attacking is not currently a break condition; movement/jumping is the intended cancel rule.

## 5.4 Parkour - 2 Major Levels

Parkour grants a 35% chance to completely negate fall damage. It is independent of Light Fitting Armor's Feather Falling-style benefit and can combine with it.

## 5.5 Elemental Skin - 1 Major Level

Elemental Skin reduces supported elemental/environmental damage by 50%.

Fire and lava damage

Freezing damage

Lightning damage

Drowning damage

Compatible modded elemental damage through tags/configuration

It does not automatically cover unrelated categories such as explosions, Poison, Wither, or fall damage.

## 5.6 Thick Blood - 1 Major Level

Poison, Wither, and compatible similar damage-over-time status effects last 50% less time. Thick Blood shortens duration; it does not reduce the damage of each tick.

## 5.7 Vibranium - 2 Major Levels

Incoming blade/slashing attacks deal 30% less damage. Weapon and damage-source classification should be tag/config driven so modded swords, blades, and similar attacks can participate.

## 5.8 Adamantium - 2 Major Levels

Incoming blunt-weapon attacks deal 40% less damage. As with Vibranium, the blunt category should be configurable for modded content.

## 5.9 Reflect - 2 Major Levels

25% chance to deflect an eligible incoming projectile.

10% chance to catch an eligible incoming projectile.

If both rolls succeed, Catch overrides Deflect.

The player-facing wording should remain generic: it should not explicitly advertise special interactions with the Combat Catch! Major.

Eligible Catch!-thrown weapons can therefore be caught as an emergent interaction; item data/enchantments must be preserved when this occurs.

## 5.10 Rebound - 1 Major Level

Eligible direct melee hits have a 15% chance to proc Rebound.

On proc, Rebound reflects 50% of the incoming attack damage before the defender's armor/Defense/other mitigation.

Reflected damage can kill the attacker.

## 5.11 Unyielding - 2 Major Levels

Each eligible negative status-effect application has a 50% chance to fail entirely.

Repeated applications may try again normally; Unyielding does not create a lasting immunity window.

Examples include Poison from a pufferfish, Wither effects, Elder Guardian Mining Fatigue, and compatible modded negative effects.

Negative effects intentionally caused by eating rotten flesh or spider eyes bypass Unyielding and apply normally.

# 6. Survival and Damage-Redirection Majors

## 6.1 Last Breath - 2 Major Levels

Last Breath is a lethal-hit survival mechanic intended in part to give defensive builds an answer to Execute-style kill mechanics. A successful proc prevents death and immediately returns the player to 5 hearts. It does not apply Regeneration.

### Diminishing survival ladder

The first lethal attempt begins a 20-minute window. Every lethal attempt advances the ladder whether the roll succeeds or fails.

| **Lethal attempt during window** | **Last Breath proc chance** |
| --- | --- |
| 1st | 100% |
| 2nd | 50% |
| 3rd | 25% |
| 4th | 12.5% |
| 5th | 6.25% |
| 6th | 3.125% |
| Further attempts | Continue halving each time |

The 20-minute timer starts on the first lethal attempt.

Later lethal attempts do not refresh the timer.

When the 20-minute window expires, the next lethal attempt returns to the 100% tier.

Actual player death also resets the ladder to 100%.

## 6.2 Castle of Stone - 2 Major Levels

Castle of Stone is a follow-up survival Major tied to Last Breath. It should require Last Breath.

When available and Last Breath successfully activates, Castle of Stone grants approximately 10 seconds of invincibility.

Castle of Stone does not recharge on an ordinary timer.

To restore Castle of Stone, the player must lie/rest in a bed for roughly 3-4 seconds.

The reset is tied to the laying-down/rest action; a successful night skip is not required.

| **TBD** Choose one exact rest duration (3 or 4 seconds) before implementation. |
| --- |

## 6.3 Absorb - 2 Major Levels

Absorb sacrifices armor durability to reduce health damage. It resolves after normal defensive mitigation.

Redirect 50% of the remaining incoming damage into one random equipped armor piece.

Use a simple conversion of 1 redirected damage = 1 durability damage.

Round the durability cost down.

If the player has no armor equipped, Absorb does nothing.

If the randomly selected armor piece breaks before paying the full redirected amount, the leftover redirected damage disappears rather than returning to the player or overflowing into another piece.

Example: after normal mitigation, 10 damage remains. Absorb redirects 5 damage into a random armor piece and the player takes the other 5. If a calculation redirects 4.8 damage, the armor loses 4 durability.

# 7. Wildcard Major: Titan Hide

## Titan Hide - 3 Major Levels

Titan Hide is the wildcard Armor build. It rewards a player who removes all worn armor and turns innate Defense into the primary protection layer. It is intentionally cheap enough to be a viable early build that can later be refunded or replaced.

Requires all four armor slots to be empty.

Doubles the player's Defense stat while active.

Grants +2 innate armor points before Titan Hide's doubling behavior, effectively contributing +4 armor points while active. (A config value, `titanHideInnateArmor`, so a modpack can tune it; LegionWareX uses +4.)

The player receives 2x normal knockback.

Fall damage is reduced by 70%.

Movement speed is increased by 50%.

Shields are allowed and do not disable Titan Hide.

Majors that explicitly require worn armor naturally do not function while Titan Hide is active.

| **BUILD IDENTITY** Fitting Armor and Titan Hide are not ownership-conflicts. Fitting Armor requires all four armor slots filled; Titan Hide requires all four slots empty. A player may own both and change playstyle simply by changing equipment. |
| --- |

# 8. Damage Stacking and Resolution Rules

## 8.1 Multiplicative reduction

Separate percentage-based defensive layers multiply instead of adding together. This is the default Armor stacking rule.

Example: Heavy Fitting Armor reduces damage by 30%, and Brace! provides Resistance II (40%). A 100-damage hit resolves approximately as 100 -> 70 -> 42, rather than treating the two effects as one additive 70% reduction.

Fitting Armor reductions, Elemental Skin, Vibranium, Adamantium, armor-overcap reduction, vanilla Resistance, and similar layers should remain multiplicative.

This allows extreme builds to become very strong without ordinary stacking immediately crossing 100% additive reduction.

Absorb resolves after normal mitigation and redirects part of the remaining damage into armor durability.

## 8.2 Special ordering rules already locked

| **Mechanic** | **Locked ordering / damage basis** |
| --- | --- |
| Rebound | Uses 50% of the incoming direct-melee attack damage before the defender's mitigation. |
| Absorb | Uses the remaining incoming damage after normal mitigation. |
| Reflect | Catch result supersedes Deflect if both proc. |
| Light Dodge | Normal Dodge roll first; separate 10% Light fallback roll second. |
| Last Breath | Evaluated on lethal outcome; successful proc prevents death and returns player to 5 hearts. |

| **IMPLEMENTATION NOTE** The complete engine-level order for every possible modded damage hook does not need to be hard-coded into this design document. Implementations should preserve the semantic rules above and avoid additive reduction bugs or duplicated self-recursion. |
| --- |

# 9. Modpack Compatibility and Data Rules

Armor is intended for large modpacks and must not assume that every weapon, armor set, projectile, elemental source, status effect, or boss comes from vanilla Minecraft.

Armor class tags/lists: Light, Medium, Heavy.

Weapon/damage tags: Blade/Slashing, Blunt, Projectile, Elemental, and any later categories.

Elemental Skin should accept configured modded elemental damage types.

Unyielding should broadly recognize negative modded status effects, with a configurable exclusion/blacklist path for effects that should never be blocked.

Reflect should preserve full item identity/data when catching a thrown item or weapon.

Boss or special-attack integrations should use configurable tags and API hooks rather than vanilla-name checks.

# 10. UI and Player Feedback

Armor should use the same broad Major presentation language as the other core skills: visible Major nodes, generic prerequisite lock state, individual Major toggles where applicable, and active-state feedback.

Fitting Armor should show which style is currently active when all four armor slots are filled.

Titan Hide should clearly indicate when its all-empty-slots requirement is satisfied.

Last Breath should expose its current proc tier and/or remaining 20-minute window in a readable way without spamming the HUD.

Castle of Stone should communicate whether it is charged or requires rest.

Exact per-stat ON/OFF toggles for Armor have not yet been explicitly locked; do not assume they are identical to Utility/Combat until confirmed.

# 11. Remaining Implementation Questions

The Armor design is feature-complete enough to move on to another category. The items below are not missing concepts; they are the remaining engineering or edge-case decisions.

| **Question** | **Current state** |
| --- | --- |
| Durability point-to-probability curve | 100 points ~= virtual Unbreaking III backup layer; exact continuous/step mapping TBD. |
| Shield Durability logic | Can use its own tuned backup logic rather than blindly copying worn armor. |
| Heavy knockback resistance | Strong knockback resistance is locked conceptually; exact value TBD. |
| Protein! persistence through refund/reset | Must be defined to avoid permanent-health respec exploits. |
| Castle of Stone rest duration | Approximately 3-4 seconds; choose exact value. |
| Armor XP value for fully negated hits | Multipliers are locked; exact would-have-been damage basis is implementation-level. |
| Extreme armor-overcap safeguard | Very high Defense can become absurd; prevent literal mathematical immunity without weakening normal overcap scaling. |
| Armor stat toggles | Not yet explicitly locked. |

# 12. Current Armor Identity Summary

| **SUMMARY** Armor starts as four readable stats - more health, more innate armor, more Dodge, and better equipment preservation - then branches into mechanical defensive Majors. A player can wear a full set and dynamically become Light/Medium/Heavy, plant themselves with Brace!, counter specific attack families, catch projectiles, deny status effects, redirect damage into armor, survive lethal attacks, or abandon worn armor entirely with Titan Hide. The category is intentionally capable of becoming absurd at high investment, but its percentage layers multiply rather than simply add. |
| --- |

Current Major pool cost: 35 Major Levels. This total is shared against the broader lifetime Major economy, so full Armor specialization carries a meaningful opportunity cost against Combat, Utility, and the future fourth category.

Armor Skill System • Detailed Design Specification • October 17, 2026