# Bloom Pets (draft)

FreshOrangeSlices' idea plan (2026-11-04), written down to build from. Nothing here is built yet; the open questions at the
end come first.

## What it is

- **Its own mod**, beside Bloom: its own folder and build like the Warionex add-on, its own game tests and CI job, so it
  builds and tests in minutes and can be swapped in game as one jar. It needs Bloom (it reads Bloom's Combat trees and
  the player's Major Levels); Bloom never needs it. Tuning happens later, in the pack.
- **Pets are any pet or companion the player owns**: tamed animals (wolves, cats, horses...), Migamigos companions,
  Zoni's Companions, Book of Dragons' dragons. Bloom Pets doesn't add creatures of its own.
- **Not a fifth skill tab.** Pets get their own screen, opened from a tab next to the skill hub (not inside it).
- **One and the Same** (the Combat Major waiting on this, hidden and switched off today) is where this idea started: a
  pet gets one Combat tree. Its final shape follows this design.

## The pet screen

- A list of every pet the player owns, including ones that aren't loaded right now (name, kind, level, where it was last
  seen, alive or not).
- Per pet: its level and progress, the **one Combat tree** chosen for it, and that tree's effects, each with an on/off
  switch.

## Pet levels

A pet gains levels by:
- **being out** (loaded and with or near its owner),
- **being fed**,
- **fighting alongside its owner** (damage dealt, kills).

What a level gives the pet is open (see questions).

## Combat trees on pets

- The player picks **one Combat tree per pet**: Sword, Axe, Trident, Unarmed (Bow and Crossbow, see below).
- **What's unlocked doesn't depend on what the player bought for themselves**: it grows with the player's total Major
  Levels earned, the whole tree by about 10 to 15.
- Passive effects work on the pet's own hits. Effects the player triggers with a key become **cooldown abilities** the
  pet uses on its own, every 20 to 30 seconds, each switchable off.
- Effects that can't work on a pet are left out.

First pass at what each tree's effects could mean on a pet:

| Tree | Effect | On a pet |
|---|---|---|
| Sword | Execute | its hits finish off enemies at 30% health or less (bosses 10%) |
| Sword | Full Sweep / Batter Up | its hits sweep the enemies around its target |
| Sword | Slash | its hits can make the target Bleed |
| Sword | Aura Sword | every 5 hits, sweep waves around it (or a cooldown ability) |
| Axe | Armor Break | its hits wear the target's armor |
| Axe | Guaranteed Critical | its hits always crit, a little less base damage |
| Axe | Charge Attack (Heavy Hands) | cooldown ability: a double-damage hit |
| Axe | Death From Above | left out (or a leap on its target as a cooldown ability) |
| Trident | Lightning Shock | its hits send sparks to nearby enemies |
| Trident | Lightning Summon | cooldown ability: lightning on its target |
| Trident | Unrestricted Riptide, Catch! | left out |
| Unarmed | Brawler | flat extra damage |
| Unarmed | Combo | each hit in a row adds a little damage |
| Unarmed | Disarm | its hits can knock the item out of a target's hand |
| Bow | Marksman, Piercing Shot, Straight Shot | only for pets that shoot (Migamigos' Cocogoat and Wavelyn), if their shots are arrows; else left out |
| Crossbow | all | left out |

## Open questions

1. **What does a pet's level give it?** More health and damage? Faster ability cooldowns? Does its level also gate how
   much of its tree it can use, or is that only the player's Major Levels?
2. **"An add-on to Misc, Combat or Defense"**: does that mean a few pet Majors inside those categories (for example a
   Misc Major for faster pet levels, an Armor Major that shares some of your defense with your pets), rather than a tree
   of their own?
3. **The unlock curve**: which effect of a tree comes first, and how many Major Levels each step takes (whole tree at 10?
   15?).
4. **Bow on ranged pets**: worth trying for the companions that shoot?
5. **Universal Combat Majors** (Bloodlust, First Strike, Overkill, Predator): pet perks too, or only the weapon trees?
6. **Limits**: one tree per pet; any cap on how many pets get one at a time?
