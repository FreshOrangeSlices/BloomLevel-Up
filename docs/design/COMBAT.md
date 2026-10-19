Combat Skill System
Combat Tab - Updated Detailed Design Specification
Main Combat design document: progression, five stats, weapon Major pool, Universal Majors, costs, UI rules, and modpack configuration. The companion interactions paper remains responsible for proc-chain edge cases.
This is the main Combat design sheet Claude should use for the actual Combat feature set. It is not a replacement for the separate Combat Major Interactions & Technical Rules paper; both are authoritative within their respective scope.
1. Combat identity and progression
• Combat is primarily offensive. Defensive sustain, armor progression, max-health growth, and most defensive mechanics belong to Armor.
• Combat uses Minecraft combat rather than replacing it: attack cooldowns, critical hits, armor, projectiles, weapon identity, enchantments, and modded weapon classification remain relevant.
• Combat Level runs from 1 to 100. Each level grants 1 Combat Stat Point, for 100 natural Combat Stat Points at Combat 100.
• Every 10 normal Combat levels grants 1 global Major Level. Combat contributes 10 natural Major Levels during its first 1-100 progression.
• Vanilla Minecraft XP may also be manually invested into normal Combat progression before Level 100.
• Reaching Combat 100 permanently unlocks the Combat XP Investment section. XP Investment buys permanent post-100 Combat Stat Points but never grants additional Major Levels.
• Combat is intentionally tuned to level more slowly than Utility because repeatable combat is easy to farm.
Build philosophy: weapon Major ranks are cheap, specific techniques. Universal Combat Majors cost more because they break normal weapon boundaries. Players are expected to mix builds rather than purchase the entire Combat pool.
2. Five base Combat stats
Stat
 | Effect
 | 100-point target
 | 
Power
 | Flat outgoing attack damage.
 | +6 damage total (+0.06 per point as first-pass linear scaling).
 | 
Tempo
 | Attack speed / attack recovery.
 | 2x baseline attack speed/recovery.
 | 
Critical
 | Increases the damage of successful Minecraft critical hits; does not add crit chance.
 | The entire successful critical hit deals 4x damage.
 | 
Penetration
 | Ignores a percentage of target armor.
 | Ignore 50% of armor.
 | 
Looting
 | Modest vanilla-Looting-like effect on eligible mob drops.
 | Approximately Looting III; intentionally far weaker than Utility Yield.
 | 
• All five Combat stats are individually toggleable.
• Post-100 XP Investment may raise these same stats above 100. Excessive endgame scaling is intentional; exact overcap formulas can be tuned during implementation.
3. Combat XP and XP Investment
• Most Combat XP comes from actual health damage dealt by eligible player-originated attacks.
• Damage XP is capped by the target's remaining HP. Overkill damage beyond remaining HP does not generate extra damage XP.
• The player who lands the final eligible hit receives a meaningful kill bonus. If the target later dies from unrelated falling, fire, drowning, another actor, or another cause, that player does not receive the kill bonus.
• Entity IDs may have configurable Combat-XP multipliers. Bosses and dangerous modded mobs can be worth arbitrary values such as 5x, 10x, 12x, etc.
• PvP support is configurable. In the intended default design, Combat stats and Majors function fully in PvP.
XP Investment behavior
• Hidden until Combat first reaches Level 100.
• Once unlocked, remains unlocked permanently, including after Bloom+ / full reset.
• Spends vanilla Minecraft XP to buy additional permanent Combat Stat Points beyond the natural 100.
• XP-funded overcap points survive Bloom+ resets.
• XP Investment never grants additional Major Levels.
4. Major Level and UI rules
• Each weapon-Major rank costs exactly 1 global Major Level.
• Weapon lines stop when meaningful mechanics stop; no filler ranks are added just to reach Level 3.
• Universal Majors may cost multiple Major Levels.
• Major Levels are global and may be earned in any category and spent in Combat.
• Purchased Majors can be refunded/respecced essentially at a moment's notice; refunded points return to the global Major pool.
• Purchased Majors are individually toggleable.
• All Major nodes remain visible. A prerequisite-locked node shows only a generic state such as “Prerequisite Required”; the UI does not reveal which Major is required.
• Normal prerequisites are ownership requirements only. Once the dependent Major is unlocked, the prerequisite may be toggled off while the dependent Major remains active.
• Copycat is the exception: if its selected source weapon-Major effect is toggled off, Copycat also becomes inactive.
• Conflicting Majors cannot both be owned, even if one is toggled off. Purchasing a conflicting Major should offer a swap/refund prompt.
• Active Major icons should eventually use a small glowing/orbiting mote/ring rather than filling the gameplay HUD with effect icons.
5. Weapon Majors
Family
 | Rank
 | Major
 | Current behavior
 | 
Sword
 | 1
 | Full Sweep
 | Sweeping attacks deal 100% of the primary hit's damage instead of reduced sweep damage.
 | 
Sword
 | 2
 | Execute
 | Eligible sword hits execute normal enemies at or below 30% max HP; bosses at or below 10%.
 | 
Axe
 | 1
 | Charge Attack
 | Charge a completed axe attack for 2x damage. The charged attack can also crit.
 | 
Axe
 | 2
 | Armor Break
 | Eligible axe hits damage armor durability by roughly 50% of actual health damage dealt.
 | 
Axe
 | 3
 | Guaranteed Critical
 | Axe attacks always crit, but suffer -2 flat attack damage.
 | 
Bow
 | 1
 | Marksman
 | Properly drawn / longer-distance shots gain additional damage. Exact range curve TBD.
 | 
Bow
 | 2
 | Piercing Shot
 | Eligible arrows can pass through enemies and continue traveling.
 | 
Bow
 | 3
 | Straight Shot
 | Eligible bow projectiles ignore normal projectile arc/drop.
 | 
Crossbow
 | 1
 | Reinforced Shot
 | Crossbow attacks gain +3 flat damage.
 | 
Crossbow
 | 2
 | Assault Rifle
 | 16-shot magazine. Click or hold to fire; 16 shots consume 16 arrows, then reload. Explicitly Copycat-ineligible.
 | 
Trident
 | 1
 | Unrestricted Riptide
 | The Major itself grants Riptide-style movement regardless of rain/water and does not require the Riptide enchantment.
 | 
Trident
 | 2
 | Lightning Shock
 | Eligible melee or thrown hits can create 50%-damage chain lightning. Requires at least 2 nearby enemies; global limiter is 3 nearby targets and one proc per 2 seconds.
 | 
Trident
 | 3
 | Lightning Summon
 | Summon lightning through the trident at a cost of 10% weapon durability per use. Control/rounding at very low durability TBD.
 | 
Mace
 | 1
 | Wind Impact
 | Whenever the mace hits the ground, it creates a Wind Burst-like impact. Works with a mace thrown by Catch!; effect itself is Copycat-ineligible.
 | 
Mace
 | 2
 | Stored Impact
 | Taking real fall damage stores 50% of that damage for the next mace attack, capped at +20 damage. 5-minute cooldown begins when the fall damage is taken.
 | 
Unarmed
 | 1
 | Brawler
 | While holding nothing, gain +6 attack damage.
 | 
Unarmed
 | 2
 | Combo
 | Each successful fully-ready unarmed hit adds +0.25 damage, up to +5. Resets after 3 seconds without attacking.
 | 
Unarmed
 | 3
 | Disarm
 | Primary/single-target unarmed hits have a 30% chance to make the target drop the held item. Does not propagate through sweep/Aura secondary hits.
 | 
Spear
 | 1
 | Long Reach
 | Spear melee attacks gain approximately +2 blocks of attack reach.
 | 
Spear
 | 2
 | Impale
 | Fully charged spear hits have a 40% chance to apply the custom Bleed status effect. See Bleed rules below.
 | 
Spear Impale / Bleed rules
• Impale checks on fully charged spear hits and has a 40% proc chance.
• Bleed is implemented as a custom status/potion effect rather than invisible per-target timer logic.
• Bleed lasts 3 seconds and deals 1 damage (half a heart) per second.
• Reapplying Bleed refreshes the duration; multiple Bleed instances do not stack.
• Bleed damage bypasses armor, but Resistance-style damage reduction still applies.
• The 40% Bleed proc may trigger on all eligible hit events, including compatible sweep/Aura hits, piercing/multi-target projectile hits, Catch!/thrown-weapon hits, and other valid inherited/copied attack events.
• Because Bleed does not stack, repeated procs on one target refresh duration rather than multiplying tick damage.
Spear is intentionally only a 2-rank weapon line. Do not add Brace/Joust/filler ranks unless the design is explicitly expanded later.
6. Universal Combat Majors and current costs
Universal Major
 | Cost
 | Design prerequisite
 | Core effect
 | 
Batter Up!
 | 2
 | Sword Major 1
 | Eligible melee weapons and unarmed attacks gain sweeping behavior.
 | 
Aura Sword
 | 3
 | Prerequisite relationship TBD
 | After 5 successful eligible attacks, automatically triggers a full 360-degree sweep around the player; current concept pulses twice while the player remains free to move/attack.
 | 
Heavy Hands
 | 3
 | Axe Major 1
 | Extends Axe Charge Attack to other eligible melee weapons.
 | 
Catch!
 | 3
 | Trident Major 1
 | Eligible weapons can be thrown like a trident and automatically return; Loyalty is not required.
 | 
Death From Above
 | 3
 | Mace Major 1
 | Eligible melee weapons gain mace-style fall-distance damage scaling without inheriting mace fall-safety/reset behavior.
 | 
Predator
 | 2
 | None currently
 | Repeated attacks against the same target ramp offensive damage. Exact stack math TBD.
 | 
Bloodlust
 | 2
 | None currently
 | Kills grant temporary offensive momentum; continued kills refresh/build it. Exact numbers TBD.
 | 
Overkill
 | 3
 | None currently
 | Excess real damage from a normal kill splashes/transfers to nearby enemies. Cannot recursively proc itself. Exact transfer/radius TBD.
 | 
First Strike
 | 2
 | None currently
 | First eligible attack against a full-health target gains a meaningful opening-burst bonus. Exact bonus TBD.
 | 
Crossbow Infinity
 | 2
 | None currently
 | Crossbows do not consume normal arrows. Fireworks and special/tipped ammunition are still consumed unless upgraded by the Misc Major Rainbow.
 | 
Copycat
 | 5
 | Owned eligible weapon-Major effect
 | Select exactly one owned eligible weapon-Major effect and universalize it across compatible weapons. Selection can be freely changed; source toggle controls Copycat active state.
 | 
One and the Same
 | 4-5 TBD
 | Future Combat + Pet requirement
 | Future Combat/Pets bridge. Exact scope and cost wait for the Pet system.
 | 
Current cost pressure: weapon-Major ranks total 20 Major Levels with Spear included. Priced Universal Majors total 30 Major Levels before One and the Same. Combat therefore currently contains 50 purchasable Major Levels before the Pet bridge, intentionally exceeding the player’s total lifetime Major budget when other tabs are considered.
7. Copycat rules
• Cost: 5 Major Levels.
• Selects exactly one eligible owned weapon-Major effect at a time.
• Selection can be freely reset/changed.
• Cannot copy Universal Majors or unowned effects.
• Copied effects retain their original penalties, cooldowns, durability costs, control requirements, and other drawbacks.
• If the selected source effect is toggled off, Copycat also becomes inactive until the source is re-enabled.
• Impale/Bleed is allowed to participate in valid inherited/copied attack events and keeps its normal 40% proc, 3-second refresh-only Bleed behavior.
• Long Reach Copycat behavior is not explicitly locked yet; do not assume it universalizes until implementation/design review confirms it.
Copycat-ineligible effect
 | Reason
 | 
Catch!
 | Universal throw/return mechanic; not a weapon-Major source.
 | 
Heavy Hands
 | Universal charge mechanic; not a weapon-Major source.
 | 
Crossbow Assault Rifle
 | Crossbow-specific magazine/reload system.
 | 
Trident Unrestricted Riptide
 | Movement/use behavior is too weapon-specific to spread safely.
 | 
Mace Wind Impact
 | Ground-impact behavior is blocked from Copycat, especially to avoid nonsensical bow/crossbow behavior.
 | 
8. Modded weapons, damage sources, and compatibility
• Prefer tags/configurable item-ID lists over hard-coded vanilla-only checks.
• Supported weapon families include Sword, Axe, Bow, Crossbow, Trident, Mace, Unarmed, Spear, and future compatible classes.
• One modded item may deliberately belong to multiple weapon families. Broken creator-defined category combinations are configuration-author responsibility unless they threaten stability.
• Combat effects should classify modded entities, projectiles, and damage sources through tags/config/API hooks where possible.
• Boss classification used by Execute must be configurable/taggable rather than hard-coded to vanilla bosses.
9. Nameplate and reset behavior
• Nameplate format: PlayerName [lvl N], where N is Major Levels currently spent/used, not total earned or available.
• If the player has ever unlocked any post-100 XP Investment tab, the [lvl N] indicator permanently changes color (currently green) to show mastery without revealing the build.
Bloom+ / full reset
• Normal Combat level resets.
• The 100 natural Combat Stat Points reset and must be re-earned/reallocated.
• Spent Major Levels are refunded.
• The Combat XP Investment tab stays unlocked permanently once first earned.
• XP-funded post-100 Combat Stat Points survive the reset.
• Natural 1-100 Major Levels are one-time progression and are not re-farmable through resets.
10. Companion technical paper boundary
The separate “Combat Major Interactions & Technical Rules” document remains authoritative for proc chains, AoE propagation, real-damage truth, Execute vs Overkill, Lightning limits, player-state mechanics, conflicts, and confirmed Major combinations. The new Spear/Bleed rules above should be added to that implementation layer when Combat is built.
11. Remaining open values
• 1. Finalize Bow Marksman range-to-damage scaling.
• 2. Finalize Predator and Bloodlust numeric stack/duration values.
• 3. Finalize Overkill transfer percentage and radius.
• 4. Finalize First Strike damage bonus.
• 5. Finalize Aura Sword prerequisite relationship if one is desired.
• 6. Finalize Trident Lightning target-search radius and exact target-selection order.
• 7. Finalize Lightning Summon control mapping and 10% durability rounding at very low durability.
• 8. Finalize One and the Same after the Pet system is designed.
• 9. Finalize explicit control-conflict pairs once input-sensitive Majors are implemented.
• 10. Decide whether Long Reach is a valid Copycat source.
Current status: Combat is structurally defined enough for implementation in chunks. Remaining work is mostly numeric tuning, controls, edge-case validation, and future Pets integration.
