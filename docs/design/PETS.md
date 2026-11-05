# Bloom Pets (draft)

FreshOrangeSlices' plan (2026-11-04), organised from the talk and from the Paper plugin BloomPets
(`FreshOrangeSlices/Bosses3`, folder `Pets`, its README), to be cleaned up and made more appealing for the Forge pack.
Nothing here is built yet. Questions still open are marked **Open**.

## The shape

- **Its own mod** beside Bloom (mod id `bloompets`): its own folder and build like the Warionex add-on, its own game
  tests and CI job, so it builds and tests in minutes and can be swapped in game as one jar. It needs Bloom; Bloom never
  needs it. Tuning happens later, in the pack.
- **Pets are the pets players already own**: tamed animals (wolves, cats, horses...), Migamigos companions, Zoni's
  Companions. Bloom Pets adds no creatures and no taming of its own (the plugin's favourite-food taming isn't needed: the
  mods tame). Dragons are restricted (**Open**: not bondable at all, or bondable without skills and trees?).
- **Its own screen**, opened from a tab next to the skill hub (not a fifth skill tab).

What carries over from the plugin, and what changes:

| Plugin | In the Forge mod |
|---|---|
| Pets live with the player, summoned from a menu | Pets stay in the world (they're other mods' mobs; storing and respawning them broke a dragon with Carry On). The screen **calls** a pet to you instead |
| One pet out at a time | Up to **3 bonded pets** (the ones that level up and get skills); every other owned pet is just listed |
| Levels 1 to 10, skills from the player's mcMMO Taming level (0 to 1000) | **Pet levels 1 to 100**, and the skills come from the pet's own level (Bloom has no Taming skill) |
| Pets never die: they faint and rest | Kept for bonded pets |
| Happiness, petting, fetch, "loves you", tired | Kept as the engagement layer (**Open**: all of it?) |
| Bonus for the owner while a pet is out, per species | Kept, per kind of pet (**Open**) |
| Posts with jobs at level 8 | **Open** (Migamigos already has "stay") |
| Riding any pet, storage per pet | Dropped: modded pets ride and carry the way their own mod says; the screen opens the pet's own inventory where it has one |

## The pet screen

- **Every pet the player owns**, including ones not loaded right now: name, kind, level, mood, where it was last seen,
  fine or fainted. Pets tamed before the mod is in show up the next time they load near their owner.
- Per pet: **Call** (teleport it to you), **Inventory** (a shortcut to its own: a Migamigos backpack, a donkey's chest,
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

For modded pets the starting and special skills need picking per kind (a Migamigos Claymore, a Zoni's companion...);
**Open**: per kind, or per category (combat, pack, speedster, utility as in the plugin)?

## One and the Same (Bloom's Combat Major, the bridge)

- A Combat Major in Bloom (it exists today hidden and switched off, waiting for this).
- Owning it lets the player give **up to 3 bonded pets one weapon tree each** (Sword, Axe, Trident, Unarmed; Bow maybe
  for pets that shoot; Crossbow no).
- How much of a tree a pet can use grows with the **player's total Major Levels earned** (the whole tree at about 10 to
  15), not with what the player bought for themselves.
- Passive effects work on the pet's own hits; effects the player triggers with a key become **cooldown abilities** the
  pet uses itself every 20 to 30 seconds, each switchable off.
- **Open**: is calling a pet to you (teleport) part of One and the Same, or for everyone?

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
