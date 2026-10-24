Combat Major Interactions - Technical Rules

Combat Major Interactions

Mixing, Proc Chains & Technical Rules

Companion paper to the Combat Skill System design. This document defines what happens when multiple Combat Majors overlap, how secondary effects proc, where Copycat is restricted, and which interactions are intentionally allowed to become extreme.

# 1. Combination philosophy

| **Primary rule** If two acquired Majors can logically apply to the same attack, they generally combine. Strength alone is not a reason to forbid a combination. Restrictions exist for impossible controls, explicit incompatibility, Copycat-null effects, crashes, or literal infinite recursion. |
| --- |

Compatible on-hit effects follow the hit.

Secondary effects may trigger different Majors and create proc chains.

A Major cannot recursively generate itself forever from its own secondary effect.

Copied effects retain their original penalties, cooldowns, durability costs, control requirements, and drawbacks.

Player-state mechanics belong to the player rather than to a specific item. Examples include combo stacks, stored fall-damage power, cooldowns, and Copycat selection state.

Conflicting control schemes become explicit Major conflicts instead of being forced to coexist.

Some specific effects may be Primary Hit Only when spreading them through AoE would violate the intended mechanic. Disarm is the current example.

# 2. Prerequisite toggles versus Copycat toggles

## Normal prerequisite behavior

For ordinary Majors, a prerequisite is only needed to unlock/purchase the dependent Major. Once acquired, the prerequisite may be toggled off and the dependent Major can stay active. The player UI does not reveal which Major is required; a locked node only shows a generic unavailable/prerequisite state until the hidden requirement has been acquired.

## Copycat special behavior

Copycat is different because it actively points at one owned weapon-Major effect. If that selected source effect is toggled off, Copycat also becomes inactive. Re-enabling the source restores Copycat. Copycat can select only one source effect at a time, but the player may reset/change the selection freely.

# 3. Lightning Shock technical rules

Native source: Trident Major Level 2. It may also appear on other eligible attacks if validly inherited through the system.

Shock damage is 50% of the triggering attack's applicable damage behavior.

Lightning requires at least 2 valid nearby enemies. With only one enemy, the lightning effect does not trigger.

The chain may involve at most 3 nearby enemies.

If exactly 2 valid nearby enemies exist, the effect produces 3 total shock hits/bounces across those two enemies.

Lightning can proc no more than once every 2 seconds, regardless of whether the source attack is a sweep, Aura Sword pulse, arrow, thrown weapon, or native trident hit.

Lightning Shock does not trigger Thorns.

A sweep/AoE attack does not spawn one independent lightning network per secondary target; the 2-second global proc rule remains the limiter.

# 4. Damage truth: real damage, Execute, Overkill, and Armor Break

| **Mechanic** | **Counts as real numeric damage?** | **Important consequence** |
| --- | --- | --- |
| Normal attack damage | Yes | Can feed Armor Break, Overkill, XP, and other damage-based effects. |
| First Strike bonus | Yes | Its added damage is part of the real hit. |
| Predator bonus | Yes | Its added damage is part of the real hit. |
| Stored Impact bonus | Yes | Once consumed, its bonus damage can feed other compatible damage mechanics. |
| Execute | No - it is a kill/finish rule | Execute does not invent fake damage for Armor Break or Overkill. |
| Overkill splash | Yes as secondary damage | Can trigger other compatible Majors, including Execute on a different target, but cannot recursively generate another Overkill from itself. |

## Execute and Overkill on the same kill

Execute and Overkill cancel each other out for the same kill event. If Execute is the mechanic that finishes the target, Overkill does not proc from that target. If the target dies normally from actual damage with excess damage, Overkill may proc. Overkill secondary damage may still push a different target into Execute range and allow Execute to finish that different target.

## Armor Break with Execute

Armor Break uses only the real damage actually dealt by the hit before Execute finishes the target. The execute portion contributes no additional fake armor-durability damage.

# 5. AoE and multi-target rules

Batter Up! sweeps can carry compatible on-hit effects to the targets actually hit.

Aura Sword pulses are real attack events. Both pulses may independently apply compatible effects.

Piercing projectiles evaluate compatible effects independently for each target struck.

First Strike can qualify independently for each untouched target hit by a piercing projectile.

Execute may trigger on eligible swept targets, Aura Sword targets, and piercing-projectile targets.

Armor Break may apply to each swept/Aura target based on the real damage dealt to that target.

Unarmed Disarm is Primary Hit Only and does not propagate through sweeping/Aura secondary hits.

Lightning still obeys its global 3-target / 2-second limit even when attached to a multi-target attack.

# 6. Copycat technical rules

Cost: 5 Major Levels.

Target must be one owned weapon-Major effect.

Exactly one Copycat source can be selected at a time.

The selection can be reset/swapped freely.

Copycat cannot target Universal Majors or unowned weapon effects.

The copied effect keeps all normal drawbacks/costs/cooldowns.

The copied effect can be applied to other eligible weapons when the mechanic maps sensibly.

If the source weapon-Major effect is toggled off, Copycat turns off as well.

## Copycat null list

| **Null / ineligible effect** | **Reason** |
| --- | --- |
| Catch! | Universal throw/return mechanic; not a weapon-Major source. |
| Heavy Hands | Universal charge mechanic; not a weapon-Major source. |
| Crossbow Assault Rifle | Weapon-specific magazine/reload system. |
| Trident Unrestricted Riptide | Weapon/movement-specific behavior that does not generalize safely. |
| Mace Wind Impact | Blocked from Copycat, especially to prevent bow/crossbow ground-impact nonsense. |

# 7. Confirmed combination rulings

| **Combination** | **Ruling** | **Behavior** |
| --- | --- | --- |
| Axe Charge + Execute | Allowed | A charged hit executes a target that was already under the execute threshold before it landed (like any hit: the hit that takes a target under the line doesn't execute). |
| Batter Up! + Execute | Allowed | Eligible sweep targets can be executed independently. |
| Aura Sword + Execute | Allowed | Each Aura pulse can execute eligible targets. |
| Armor Break + Batter Up! | Allowed | Armor-durability damage can apply to each swept target based on the real damage dealt to that target. |
| Unarmed Disarm + Batter Up! | Blocked for secondary targets | Disarm only checks the primary/single-target hit; sweeping/Aura secondary hits do not disarm. |
| Overkill + Execute | Mutually exclusive on one kill | If Execute finishes the target, that target produces no Overkill proc. Overkill damage can still trigger Execute on another target. |
| Predator + Overkill | Allowed | Single-target buildup can create a high-damage kill whose real excess damage feeds Overkill. |
| Bloodlust + Overkill | Allowed | Kills caused by Overkill secondary damage can feed/refresh Bloodlust. |
| First Strike + Piercing | Allowed | Each untouched pierced target can independently qualify for First Strike. |
| Straight Shot + Catch! | Allowed | Eligible thrown weapons can inherit a straight/no-arc trajectory where technically sensible. |
| Lightning + Catch! / projectiles | Allowed | Validly inherited Lightning can trigger from thrown weapons or arrows while respecting the Lightning proc limiter. |
| Mace Wind Impact + Catch! | Allowed | A thrown mace that hits the ground can trigger native Wind Impact. Wind Impact itself remains Copycat-ineligible. |
| Unarmed Combo + Copycat | Allowed | Once copied, the combo stack is player-state and can build from eligible attacks rather than being stored on one item. |
| Axe Always-Crit + Bow | Allowed | No special exception is needed; copied critical behavior may apply even though bows already have their own crit-style projectile behavior. |
| Straight Shot + Trident | Allowed | A thrown trident can inherit the no-arc trajectory when the relevant effect is validly applied. |
| Mace Stored Impact + Copycat | Allowed | Stored fall-damage power belongs to the player; the next eligible copied attack can consume it. |
| Aura Sword + Lightning + Bloodlust | All may proc | Each Aura pulse can participate normally; Lightning obeys its limiter and resulting kills may feed Bloodlust. |
| Batter Up! + Armor Break + Execute | Allowed with real-damage rule | Armor Break uses only the real hit damage; Execute may finish the target afterward. |
| Catch! + Straight Shot + Lightning + Execute | Allowed | No special transformation beyond all compatible mechanics applying normally. |
| Stored Impact + First Strike | Allowed | Both can stack on the same eligible opening hit, creating an intentionally extreme burst attack. |

# 8. Catch! and fall/impact interactions

## Death From Above + Catch!

When a valid thrown weapon travels downward, Death From Above may use the weapon's downward fall distance for mace-style damage scaling. The player is not falling, so the throw avoids the self-fall-damage risk that exists when the player personally performs Death From Above. This is an intentional high-investment orbital-strike style interaction.

## Mace Wind Impact + Catch!

Wind Impact triggers whenever the actual mace hits the ground, including when the mace was thrown through Catch!. This native mace interaction is allowed even though Wind Impact is not a valid Copycat source.

# 9. Player-state mechanics

| **State** | **Owner** | **Current rule** |
| --- | --- | --- |
| Unarmed Combo stacks | Player | Build from eligible fully ready attacks when universalized; reset after 3 seconds without attacking. |
| Mace Stored Impact | Player | Taking real fall damage stores 50% up to +20 damage. 5-minute cooldown begins when the fall damage is taken. |
| Copycat selection | Player | Exactly one source weapon-Major effect selected at a time; can be reset/swapped. |
| Lightning proc cooldown | Player/effect source | One Lightning Shock proc at most every 2 seconds. |

# 10. Conflict behavior

Conflicts are used when two Majors require incompatible use of the same input/control or cannot logically coexist.

Conflicting Majors cannot both be purchased/owned, even if one would be toggled off.

Attempting to buy a conflicting Major should display a swap prompt. Accepting the swap refunds/removes the conflicting Major and purchases the new one if enough points are available.

Example design direction: a hold-to-charge universal attack and a hold-to-throw universal weapon behavior may conflict if the control scheme cannot clearly distinguish the intended action.

The explicit conflict list should be built after the final Major pool is known rather than inferred dynamically.

# 11. Implementation safety boundaries

Do not weaken a combination merely because it is strong; first ask whether the interaction is mechanically coherent.

Block literal self-recursion and infinite proc loops.

Keep hard proc caps where already defined, especially Lightning.

Use Primary Hit Only metadata for the rare effect that should not spread through AoE. Disarm is the current example.

Preserve exact source costs/penalties when an effect is inherited or copied.

Prefer predictable event attribution so Combat XP, kill credit, Execute, and Overkill all use the same real-damage truth.

Modpack creators may assign items to multiple weapon categories. Broken creator-defined category combinations are configuration-author responsibility unless they threaten stability/crashes.

# 12. Spear Impale / Bleed technical rules

Native source: Spear Major Level 2 (Impale).

Every eligible hit event carrying Impale gets its own independent 40% Bleed application roll. This includes compatible secondary/AoE or derived hit events when normal on-hit effects are allowed to follow the hit.

Bleed is implemented as a custom status/potion effect, lasts 3 seconds, and deals 1 damage (half a heart) once per second.

Bleed damage ignores armor but is still reduced by Resistance-style damage reduction.

Bleed does not stack multiple independent instances on the same target. Reapplication refreshes the remaining duration to 3 seconds.

Because Bleed is a status effect, ordinary status-effect systems may reject, cleanse, or otherwise modify it when another system explicitly supports that interaction.

# 13. Technical items still open

Exact event ordering for every damage multiplier can be finalized during implementation as long as the real-damage / Execute / Overkill rules in this paper remain true.

Finalize Lightning target search radius and exact target-selection order.

Finalize the exact control mapping for Catch!, Heavy Hands, and any future input-sensitive Universal Majors, then mark true conflicts explicitly.

Finalize how special/modded projectiles declare eligibility for Straight Shot, Piercing, Lightning, and similar inherited effects.

Finalize Overkill transfer percentage/radius and Predator/Bloodlust numeric stacks so proc chains remain readable and performant.

Finalize whether Spear Impale is Copycat-eligible. If allowed, copied Impale must keep the same 40% per-hit roll, 3-second non-stacking Bleed, armor bypass, and Resistance interaction.

Working design specification - values may be tuned during implementation testing.