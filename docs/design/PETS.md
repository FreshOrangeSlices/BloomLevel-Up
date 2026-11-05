# Bloom Pets (draft)

FreshOrangeSlices' plan (2026-11-04), organised from the talk and from the Paper plugin BloomPets
(`FreshOrangeSlices/Bosses3`, folder `Pets`, its README), to be cleaned up and made more appealing for the Forge pack.
Nothing here is built yet. Questions still open are marked **Open**.

## The shape

- **Its own mod** beside Bloom (mod id `bloompets`): its own folder and build like the Warionex add-on, its own game
  tests and CI job, so it builds and tests in minutes and can be swapped in game as one jar. It needs Bloom; Bloom never
  needs it. Tuning happens later, in the pack.
- **Three kinds of pets** (FreshOrangeSlices, 2026-11-04: "three companion mods"):
  - **Bloom pets**: the plugin's roster of vanilla animals, bonded the plugin's way (sneak + feed a wild one its
    favourite food a few times). The Armadillo is left out (it isn't in 1.20.1).
  - **Migamigos companions** and **Zoni's Companions**, tamed by their own mods, then bonded in the pet screen.
  - **Dragons can't be bonded** (to avoid trouble), nor anything else.
- **Its own screen**, opened from a tab next to the skill hub (not a fifth skill tab).

What carries over from the plugin, and what changes:

| Plugin | In the Forge mod |
|---|---|
| Pets live with the player, summoned from a menu | Migamigos and Zoni's companions stay in the world (storing and respawning other mods' mobs broke a dragon with Carry On). The screen **calls** any pet to you, for everyone (**Open**: Bloom pets live with you and are summoned, as in the plugin, or stay in the world too?) |
| One pet out at a time | Up to **3 bonded pets** (the ones that level up and get skills); every other owned pet is just listed |
| Levels 1 to 10, skills from the player's mcMMO Taming level (0 to 1000) | **Pet levels 1 to 100**, and the skills come from the pet's own level (Bloom has no Taming skill) |
| Pets never die: they faint and rest | Kept for bonded pets |
| Happiness, petting, fetch, "loves you", tired | **Mood stays**: an unhappy pet loses some of its abilities, so feeding it is needed. Feeding is for its mood, not for healing (**Open**: how a pet heals then) |
| Bonus for the owner while a pet is out, per species | Kept, per kind of pet (**Open**) |
| Posts with jobs at level 8 | **Open** (Migamigos already has "stay") |
| Riding any pet, storage per pet | Dropped: modded pets ride and carry the way their own mod says; the screen opens the pet's own inventory where it has one |

## The pet screen

- **Every pet the player owns**, including ones not loaded right now: name, kind, level, mood, where it was last seen,
  fine or fainted. Pets tamed before the mod is in show up the next time they load near their owner.
- Per pet: **Call** (teleport it to you; everyone has it), **Inventory** (a shortcut to its own: a Migamigos backpack, a donkey's chest,
  a Zoni's companion's), **Bond / Unbond** (3 bond slots).
- Per bonded pet: level and XP, mood, its skills, and (with One and the Same) its weapon tree and an on/off switch per
  ability.
- Pets that don't fight (horses, cats, parrots...): listed, Call and Inventory, nothing more.

## Levels (bonded pets, 1 to 100)

XP sources from the plugin (numbers to retune for 100 levels; the plugin's 40 + 30 x n per level is far too steep there):

| Source | Plugin XP |
|---|---|
| Feeding it (its own mod's food) | 10, at most every 30 seconds |
| It kills a mob | 6 |
| You kill a hostile mob while it's out | 3 |
| Every hit it lands | 1, or 2 for a big hit, at most every 1.5 seconds (the fastest way) |
| Time together | 2 a minute |
| Walking around with it | 1 per 20 blocks |
| Playing fetch | 2, at most every 15 seconds (**Open**: fetch at all?) |

AFK leveling is fine (FreshOrangeSlices). Levels stay on the pet itself, so a Migamigos companion back from its
keepsake keeps its level.

## Pet skills, by the pet's level

The plugin's taming skills (mcMMO's wolf skills, each animal with its own names), on the plugin's 0 to 1000 scale
divided by 10:

| Pet level | Skill (plugin's wolf name) | What it does |
|---|---|---|
| 1 | Starting skill (each kind its own) | e.g. the plugin's Iron Colossus, Double Strike, Unyielding, Momentum, Bamboo Blessing |
| 10 | Awareness | steps out of fire, lava and cacti back to you; no fall damage |
| 15 | Gore | chance of double damage: 1% a pet level, so 100% at 100 (the plugin's numbers; likely too much here) |
| 20 | Special skill (each kind its own) | e.g. Guardian Intervention, Phase Strike, Crushing Presence, Ram, Soothing Presence |
| 20 | Fast Food | 50% chance to heal by the damage it deals |
| 25 | Thick Fur | half damage from attacks and arrows; shakes off burning |
| 35 | Holy Hound | magic, poison and wither heal it |
| 50 | Shock Proof | 1/6 damage from explosions and lightning |
| 75 | Sharpened Claws | +2 damage on every hit |
| 100 | Max level | gold name and a sparkle, a second wind once each time it's called |

Bloom pets keep their own kind's tree from the plugin (a wolf mcMMO's wolf skills, the Iron Golem, Vex, Polar Bear, Goat
and Panda their own). A **modded companion rolls one of those kinds at random when it's bonded** and keeps it for good:
a Migamigos Claymore might fight like a Panda, a Wolf or an Iron Golem.

## One and the Same (Bloom's Combat Major, the bridge)

- A Combat Major in Bloom (it exists today hidden and switched off, waiting for this).
- Owning it lets the player give **up to 3 bonded pets one weapon tree each** (Sword, Axe, Trident, Unarmed; Bow maybe
  for pets that shoot; Crossbow no).
- How much of a tree a pet can use grows with the **player's total Major Levels earned** (the whole tree at about 10 to
  15), not with what the player bought for themselves.
- Passive effects work on the pet's own hits; effects the player triggers with a key become **cooldown abilities** the
  pet uses itself every 20 to 30 seconds, each switchable off.

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
| Bow | Marksman, Piercing Shot, Straight Shot | only for pets that shoot (Migamigos' Cocogoat and Wavelyn), if their shots are arrows |
| Crossbow | all | left out |

## Building it, in phases

Each phase is its own chunk: built, tested (game tests on plain wolves first; Migamigos and Zoni's later), pushed.

1. **The mod and the list**: the `bloompets` mod and its CI job; tracking owned pets; bonding (3 slots); the screen with
   Call and Inventory.
2. **Levels and skills**: XP sources, levels 1 to 100, the skill table, fainting instead of dying, max-level perks.
3. **Mood and bonuses** (whatever of the plugin's engagement layer is kept): mood, petting, owner bonuses, maybe posts.
4. **One and the Same**: the Major in Bloom, weapon trees on pets, cooldown abilities and their switches.
5. **The pack**: Migamigos and Zoni's per-kind skills, dragons' limits, a quest chapter, tuning.
