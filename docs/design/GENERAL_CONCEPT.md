Universal Skill System • General Mod Concept

**Universal Skill System**

General Mod Concept & UI / Progression Handoff for Claude

| **Core request:  **Do not recreate the old plugin as a Fabric mod with the same structure. Build a mod-native, reusable progression framework with a central skill hub, levelable stat icons, purchasable Major icons, strong modpack interoperability, and room for unusual cross-skill builds. |
| --- |

**This document intentionally explains the overall system only. **The separate Utility, Combat, Armor, and Misc design documents contain the individual stats, Major effects, formulas, and interaction rules. This file should be the architectural reference Claude reads first.

# 1. What the Mod Is

| **Principle** | **Meaning** |
| --- | --- |
| Four core skill categories | Utility, Combat, Armor, and Misc. Pets are a separate system and should not become a fifth core skill tab. |
| Use-based progression + player choice | Each category has its own level and Stat Points, while a shared Major Level currency lets the player buy powerful mechanics in any category. |
| Simple base stats, wild Majors | The ordinary level tracks handle readable numeric growth. Major icons are where mechanics change, rules are broken, and builds become unique. |
| Modpack-first | Vanilla content is the default, not the boundary. Modded weapons, armor, mobs, ammunition, foods, effects, damage types, crops, and loot should be classifiable through tags/config/API rather than hardcoded assumptions. |
| Strong builds are intentional | The system is allowed to become absurd at high investment. Balance should come primarily from cost, prerequisites, tradeoffs, cooldowns, incompatibilities, and the finite Major Level economy—not by flattening every effect into small percentages. |

# 2. The Skill Hub UI

The player should have one central skill hub rather than a command-heavy plugin-style interface. The hub is the visual home of the progression system.

**The hub** shows four main category icons: Utility, Combat, Armor, and Misc.

Selecting a category opens that category page without leaving the overall hub experience.

A category page contains two different kinds of icons: levelable Stat icons and purchasable Major icons.

The interface should be clean and readable. Do not turn the Major system into a giant Path-of-Exile-style galaxy of tiny nodes.

## Levelable Stat Icons

These are the ordinary numeric progression tracks inside a category. A category level grants Stat Points, and the player allocates those points into the category's Stat icons.

1 normal category level = 1 natural Stat Point.

Natural level cap = 100, so a category normally supplies 100 Stat Points before any post-100 system.

The number of Stats is category-specific. The framework must not assume every category has the same number of tracks.

Stats should be easy to inspect and allocate. Bars, sliders, plus/minus controls, or clickable icons are preferred over a complex node web.

Where the detailed category document says a stat can be toggled, its active/inactive state must be supported by the framework.

## Major Icons

Major icons represent abilities, rule changes, weapon specializations, survival mechanics, and other powerful build choices. They are purchased with the global Major Level currency rather than normal Stat Points.

Every Major icon is visible from the beginning. Hidden prerequisites should not hide the existence of the Major.

A locked Major that has a prerequisite should show a generic state such as “Prerequisite Required.” It should not reveal which prerequisite is missing.

Major costs can vary heavily. A simple perk might cost 1–2 Major Levels; build-defining effects can cost 7, 10, or even 20.

Some weapon Major lines have multiple ranks. Each rank is its own purchase where the category design says so.

Purchased Majors are individually toggleable unless the effect is inherently passive/permanent or the detailed design says otherwise.

An active Major icon should have a clear visual state, such as a small glowing/orbiting light. Inactive purchased Majors should remain visibly owned but not active.

Conflicting Majors cannot both be owned. Attempting to buy a conflict should offer a swap/refund flow instead of silently failing.

# 3. Category Levels, XP, and Stat Points

Each of the four categories has its own level from 1–100. The XP source is category-specific; do not force every category into the same gain rule.

| **Category** | **Primary natural progression** | **Special note** |
| --- | --- | --- |
| Utility | Tool / gathering actions | Detailed Utility document defines eligible actions and XP. |
| Combat | Actual damage dealt + kill bonus | Overkill damage is capped by target remaining HP for XP purposes; entity multipliers are configurable. |
| Armor | Damage received plus defensive events | Dodges and shield blocks can award bonus Armor XP; environmental damage can still count. |
| Misc | Collected vanilla XP orbs | Misc mirrors the original orb value without consuming the orb or reducing the player's normal XP reward. |

**Manual vanilla-XP investment before 100. **The player may spend normal Minecraft XP to advance a chosen category instead of being forced to grind one activity all the way to 100. XP spent into a category advances that category only; it does not create a freely transferable normal skill level.

# 4. Global Major Level Economy

Major Levels are a separate, shared build currency. They are intentionally global so progression earned in one category can fund a Major in another category.

Every 10 natural levels in a category grants 1 global Major Level.

Each category therefore grants 10 natural Major Levels on its first journey from 1 to 100.

Four categories × 10 = 40 natural Major Levels total.

Bloom+/prestige can grant up to +2 bonus Major Levels per category (+1 per 50 levels reached), for up to +8 bonus Major Levels overall.

Current intended lifetime maximum = 48 Major Levels.

Major Levels are freely spendable across any category. Example: a Major Level earned from Combat may be spent on an Armor or Utility Major.

The Major catalogue should cost far more than 48 total. The player is not expected to buy everything.

| **Important anti-farm rule:  **Natural Major Levels from the first 1–100 journey are one-time rewards. A full reset must not let the player repeatedly farm the same natural Major Levels. |
| --- |

# 5. Level 100 and XP Investment

Utility, Combat, and Armor gain a post-100 XP Investment feature. Misc is intentionally different and does not receive this tab.

The XP Investment tab is hidden/locked until the player first reaches level 100 in that category.

Once unlocked, it remains permanently unlocked even if the category is later reset.

The player can spend vanilla Minecraft XP to buy permanent local Stat Points beyond the natural 100.

These post-100 Stat Points belong only to that category and do not grant additional natural Major Levels.

Post-100 Stat Points survive Bloom+/full reset.

Misc has no post-100 XP Investment tab because Misc already levels from vanilla XP orbs and is intentionally capped as the general-XP category.

# 6. Reset / Bloom+ Behavior

The system needs a true reset/prestige path, but it must distinguish temporary progression from permanent investment.

| **Thing** | **On full reset / Bloom+** | **Why** |
| --- | --- | --- |
| Normal category level | Resets | Player re-levels the category. |
| Natural allocated Stat Points | Refunded/reset with the category | Player rebuilds the normal 1–100 allocation. |
| Post-100 XP-invested Stat Points | Permanent | These are the long-term investment layer. |
| Purchased Majors | Refunded into available Major Levels | Builds can be rebuilt freely. |
| Previously earned natural Major Levels | Do not re-award on re-level | Prevents reset farming. |
| XP Investment tab unlock | Permanent once earned | Reaching 100 once permanently proves mastery. |

**Respec philosophy. **Major purchases should be easy to refund/respec. The system is about experimenting with builds, not punishing the player for trying combinations. Any specific permanent counters or item modifications defined by a Major are handled by that Major's own rules.

# 7. Major Prerequisites, Toggles, Conflicts, and Synergy

Most prerequisites should be Major-to-Major ownership requirements, not “have 73 points in Stat X” gates.

Normal prerequisites are ownership checks. Once the player owns the prerequisite, turning that prerequisite Major off does not automatically disable the dependent Major unless the detailed design explicitly says so.

Some Majors intentionally have custom dependency behavior. Example: Copycat follows the active state of its selected source effect. The framework should support exceptions instead of enforcing one rigid prerequisite rule.

Cross-category and cross-weapon synergy is intentional. If two effects logically work together, prefer allowing the combination.

Do not pre-ban combinations merely because they are strong. Block combinations only for real control conflicts, impossible state ownership, recursion, or explicitly defined incompatibilities.

Prevent infinite self-recursion. A proc may trigger another compatible proc, but an effect must not endlessly trigger itself.

Player-state mechanics (cooldowns, stored damage, combo counters, etc.) belong to the player unless the detailed design explicitly makes them item-state.

# 8. Player-Facing Progress Display

Nameplate format: PlayerName [lvl N].

N is the number of Major Levels currently spent, not the number earned or available.

If the player has ever unlocked any post-100 XP Investment tab, the [lvl N] portion becomes green permanently.

Do not expose the player's exact build, exact post-100 stat investment, or a noisy list of active buffs in the nameplate.

# 9. Modpack Compatibility Requirements

This mod is intended to live inside large modpacks. The framework must not assume BloomCraft owns all weapons, mobs, armor, foods, ammunition, status effects, damage types, loot, crops, or progression content.

Use tags/configurable lists for item archetypes such as sword, axe, bow, crossbow, trident, mace, light/medium/heavy armor, blade/blunt damage, ammunition, meat, vegetables, elemental damage, bosses, and other categories used by the detailed designs.

Allow explicit item/entity/effect IDs in config where tags are insufficient.

A modded item may belong to more than one archetype if the pack author intentionally configures it that way.

Where practical, expose data-driven configuration or API hooks so other mods/datapacks can register compatible content without editing core code.

The server should be authoritative for progression, costs, permanent state, item imprints, and proc resolution. The client UI should display synced state and send player choices back to the server.

Persistent data must survive logout/restart and should be versioned/migratable. This includes category levels, allocations, Major ownership/toggles, one-time Major rewards, permanent post-100 investment, prestige bonuses, and any Major-specific persistent state.

# 10. Scope Boundaries

Pets are a separate system. Do not add Pets as a fifth main skill category. A future Major may bridge Combat/player mechanics into pets, but the pet framework should remain independent.

Hidden behavior counters/titles/passives are optional secondary progression and should never control the core build. They can be added after the main four-category framework is stable.

The four category design documents are the source of truth for exact numbers and individual Major effects. This general document is the source of truth for the shared progression/UI architecture.

# 11. Implementation Priorities for Claude

Build the shared framework first: category registry/state, XP/level handling, Stat Point allocation, global Major Level wallet, Major ownership/toggles/prerequisites/conflicts, reset/persistence, and the central hub UI.

Keep category definitions flexible. Do not write the framework as if every category has the same number of Stats, the same XP source, the same post-100 behavior, or the same Major layout.

Make the hub feel like a mod UI, not a plugin command menu. The user should be able to understand current category level, XP progress, Stat allocation, owned Majors, available Major Levels, prerequisites, and active states from the GUI.

Do not silently “balance” or simplify unusual effects from the detailed design documents. If an interaction is technically problematic, surface the implementation problem instead of replacing the mechanic with a generic percentage buff.

Prefer configurable/tag-driven compatibility over vanilla-only hardcoding. The goal is a reusable system that can be dropped into larger modpacks.

Keep numerical formulas and individual Major behavior modular so the design can be tuned without rewriting the entire progression engine.

| **One-sentence target:  **A four-category, modpack-friendly RPG progression hub where normal levels improve simple category Stats, a scarce global Major currency buys powerful rule-changing abilities, post-100 XP creates permanent specialization, and the whole system remains easy to respec, configure, and combine. |
| --- |

# 12. Conceptual Data Model (High Level)

This is not a required class diagram; it is a checklist of the state the framework must be able to represent.

Per player, per category: current level, current category XP, natural Stat Points earned, current stat allocations, one-time natural Major rewards already claimed, level-100 mastery unlock.

Per player, global: available Major Levels, spent Major Levels, prestige bonus Major Levels, owned Majors, active/toggled Majors, conflict/prerequisite state.

Per player, per category where supported: permanent post-100 XP-invested Stat Points and their allocations.

Per Major: ID, category, icon, display name, description, cost, optional ranks, prerequisites, conflicts, toggle policy, activation condition, persistent state hooks, and compatibility tags/config.

Per item when a Major modifies an item permanently: item-attached persistent data rather than player-only state (for example, an imbued weapon effect).

Design handoff for Claude • framework overview, not individual skill balancing