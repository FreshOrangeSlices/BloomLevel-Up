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
  - **Migamigos companions** and **Zoni's Companions**, tamed by their own mods, then listed in the pet screen like the rest.
  - **Dragons can't join** (to avoid trouble), nor anything else.
- **Its own screen**, opened from a tab next to the skill hub (not a fifth skill tab).

What carries over from the plugin, and what changes:

| Plugin | In the Forge mod |
|---|---|
| Pets live with the player, summoned from a menu | **All pets stay out in the world**, Bloom pets too (FreshOrangeSlices, 2026-11-04: a friend keeps about 10 dogs, and that's how he plays). Only the pets in the player's **party** get their levels, skills, owner bonus and Bloom stats (see The party); every other pet is a plain pet. The screen **calls** any pet to you, for everyone |
| One pet out at a time | Any number out; a **party of up to 3**, and **only one Bloom pet follows** you (modded companions all follow, their own mods' way) |
| Levels 1 to 10, skills from the player's mcMMO Taming level (0 to 1000) | **Pet levels 1 to 100**, and the skills come from the pet's own level (Bloom has no Taming skill) |
| Pets never die: they faint and rest | Kept for party pets |
| Happiness, petting, fetch, "loves you", tired | **Mood stays**: an unhappy pet loses some of its abilities, so feeding it is needed. Feeding still heals, and pets get a **second bar, hunger**, like the player's food: a pet has to be fed, and one with food in its own inventory takes it and eats by itself. **Fuel**: a Bloom pet eats its favourite food; a modded companion eats cooked vanilla food |
| Bonus for the owner while a pet is out, per species | Kept (party pets only). A modded companion has the bonus of the kind it rolled |
| Posts with jobs at level 8 | Kept |
| Fetch, petting | Kept; **fetch only for Bloom pets** (the roster's vanilla animals, nothing else; FreshOrangeSlices, 2026-11-05) |
| Riding every pet | **Dropped** (FreshOrangeSlices, 2026-11-05): on Forge 1.20.1 it can't be the plugin's simple version (steering a mob runs on the player's own game and needs a client patch, and there's no built-in way to grow a pet or shrink its rider, which needs a mod like Pehkui), and about three chunks for something roughly like the plugin's wasn't worth it. Horses, donkeys, llamas, camels, pigs and striders keep their vanilla riding. Modded companions and dragons were never in it |
| Storage per pet | Kept for Bloom pets (small; it also holds their food). **No pet inventory for modded companions** in the screen (they keep their own mod's) |
| Pet Toy, Pet Blooms | The screen and a key replace the toy; a bloom stays as an optional flower that calls one pet |
| Pet Guide | A quest book chapter |
| Pet PvP toggle | Dropped (co-op) |
| mcMMO tie-in | Gone (pet levels instead) |

## The party

- **Bloom pets: one in the party**, the one that follows you. It has its level, skills, owner bonus and Bloom stats.
- **Support pets** (Bee, Allay, Cat and the other non-combat ones): their passive works while you're **within 32
  blocks** of them, following or not. **At most 3 support pets work in any 60-block area**: a 4th there does nothing
  (so 3 Bees is the limit for one farm).
- **Modded companions: no limit, and they always have their traits and bonus**, in the party or not (FreshOrangeSlices,
  2026-11-04: broken, but fine; a player who'd rather not fight can lean on a good team).
- **Out of the party** (Bloom pets): still yours, still out in the world, still callable, no Bloom bonus. It keeps its
  level and traits stored; joining again brings them back, **with its health filled to full**.
- **Party changes have a 5-minute cooldown** (so swapping isn't a free full heal mid-fight).
- **Bloom pets at posts keep their jobs** outside the party (a guard still guards), as in the plugin.
- **Only the party pet earns XP**; Bloom pets outside the party don't level (support pets at posts earn from their jobs,
  see Levels).
- **Out of the party a pet is unprotected** (2026-11-05): its level's stats aren't in effect (plain vanilla stats), and
  if it dies at home it's dead. The party pet faints, and **a pet at its post comes back** (it faints there and rests;
  confirmed 2026-11-05). **Open** (an idea): a limit on how many pets a player can have, with more slots for levels or
  resources.
- **Kills by pets keep their drops**: the add-on's rule that a mob killed by another mob drops nothing already counts
  tamed pets and companions as the player's side. Bloom pets that aren't tameable in vanilla (Iron Golem, Panda, Bee...)
  must carry an owner mark the add-on reads too (`world/MobKillDrops.playerSide`), with a game test.

## The pet screen

FreshOrangeSlices' sketch: `art/pets-screen-sketch.png`; with suggestions added: `art/pets-screen-mockup.png`.

- **Left, the pet list**: Bloom (vanilla) pets first, then modded companions; a pink mark on the ones in the party.
  Every pet the player owns, including ones not loaded right now. Pets tamed before the mod is in show up the next time
  they load near their owner.
- **Centre, the pet's model**, turning to follow the mouse (as the inventory shows the player; Easy Mob Spawn Control's
  screen does it for mobs).
- **Four slots each side**: its skills. A toggle at the top right switches between **Bloom** (its weapon tree, with One
  and the Same) and **Pet** (its level and traits).
- **Bottom left: Call** (summons it to you; everyone has it). **Bottom right: its inventory** (Bloom pets only).
- Suggestions for what the sketch has no room for yet: see the mockup (name and level with an XP bar, health, hunger and
  mood bars, where it is, a modded companion's rolled kind ("Spirit of the Panda"), the owner bonus line, locked slots showing their
  level, on/off switches for abilities, party and follow buttons, post, rename, release, list filters).

## Levels (party pets, 1 to 100)

**The curve is Bloom's own, cut in half** (FreshOrangeSlices, 2026-11-05): XP from level L to L+1 = 25 + 5 x L (Bloom's
50 + 10 x L halved), so 30 XP for level 2, 275 at level 50, 520 for the last one, about 27,250 from 1 to 100 (a Bloom
tab takes about 54,500). The plugin's 40 + 30 x n per level was far too steep for 100 levels. **Non-combat pets take a
quarter of Bloom's curve** (12.5 + 2.5 x L, about 13,600 to 100), since they level the slowest.

**How they earn it** (2026-11-05): **combat pets the way Bloom's Combat tab does**, 0.5 XP per point of real damage they
deal (after armor, never more than the target had left), and the killing blow adds 50% of what that kill took. **Each
combat kind has its own XP multiplier** so they all level at about the same pace, whatever they hit for (a wolf bites for
about 4, an Iron Golem about 15, a goat's ram about 2: roughly wolf 1x, golem 0.3x, goat 2x; agreed 2026-11-05).
**Non-combat pets** from time together, walking with you and feeding, plus **XP from their jobs** on top (a Bee for each
crop it grows, an Allay for each item it collects, a Sniffer for each find...), so a farm pet at its post levels without
taking the party's place.

**How long to 100** (FreshOrangeSlices, 2026-11-05): a non-combat pet about **8 hours** with it out; a combat pet about
**16 hours of real fighting**, about 48 hours of normal play with it out (a pet fights maybe a third of the time). The
first 10 levels come in about 20 minutes of fighting; levels 90 to 100 take about 3 hours. Bloom's Combat rate as it
is would max a wolf in about 4 hours of fighting, far too quick, so the pets' XP per damage is scaled down to land
there.

For reference, the plugin's XP sources (replaced by the above):

| Source | Plugin XP |
|---|---|
| Feeding it (its own mod's food) | 10, at most every 30 seconds |
| It kills a mob | 6 |
| You kill a hostile mob while it's out | 3 |
| Every hit it lands | 1, or 2 for a big hit, at most every 1.5 seconds (the fastest way) |
| Time together | 2 a minute |
| Walking around with it | 1 per 20 blocks |
| Playing fetch | 2, at most every 15 seconds |

AFK leveling is fine (FreshOrangeSlices). Levels stay on the pet itself, so a Migamigos companion back from its
keepsake keeps its level.

## Pet skills, by the pet's level

The plugin's taming skills (mcMMO's wolf skills, each animal with its own names), on the plugin's 0 to 1000 scale
divided by 10:

| Pet level | Skill (plugin's wolf name) | What it does |
|---|---|---|
| 1 | Starting skill (each kind its own) | e.g. the plugin's Iron Colossus, Double Strike, Unyielding, Momentum, Bamboo Blessing |
| 10 | Awareness | steps out of fire, lava and cacti back to you; no fall damage |
| 15 | Gore | chance of double damage: half the pet's level in %, so about 8% at 15 and 50% at 100 (capped at 50%, 2026-11-05: strong on purpose, it's for players who aren't good at fighting) |
| 20 | Special skill (each kind its own) | e.g. Guardian Intervention, Phase Strike, Crushing Presence, Ram, Soothing Presence |
| 20 | Fast Food | 50% chance to heal by the damage it deals |
| 25 | Thick Fur | half damage from attacks and arrows; shakes off burning |
| 35 | Holy Hound | magic, poison and wither heal it |
| 50 | Shock Proof | 1/6 damage from explosions and lightning |
| 75 | Sharpened Claws | +2 damage on every hit |
| 100 | Max level | gold name and a sparkle, a second wind once each time it's called |

Bloom pets keep their own kind's tree from the plugin (a wolf mcMMO's wolf skills, the Iron Golem, Vex, Polar Bear, Goat
and Panda their own). A **modded companion rolls one of the combat kinds at random when it first joins the party** (never the Vex, and never
a non-combat kind) and keeps it: a Migamigos Claymore might get the **Spirit of the Panda**, of the Wolf or of the Iron
Golem (shown that way in the screen). **One free reroll**: the first time the player opens its page, a pop-up says
"Claymore gained the Spirit of the Wolf. Keep it, or reroll? (This can't be undone.)"

## The roster: every Bloom pet

From the plugin (its README and `Species.java`), with the changes agreed on 2026-11-04. Every owner bonus grows with
the pet's level (in the plugin about 45% stronger at its top level; here spread over 100 levels). A sad pet's bonus, job,
skills and storage rest until it cheers up.

**Categories** (from the plugin): **Combat** fights whatever you fight and defends you, medium storage; **Pack** carries
the most (up to a double chest); **Speedster** is the fastest; **Utility** helps around you. Storage grows with level:
small 9 slots (18 later), medium 18 (27 later), pack 27 up to 54.

| Pet | Category | Favourite food | Bonus for you while it's out | Job at a post |
|---|---|---|---|---|
| Wolf | Combat | any meat | +8% melee damage | guards (attacks monsters within 16 blocks) |
| Polar Bear | Combat | salmon | you can't freeze, and Slowness can't touch you | guards |
| Iron Golem | Combat | iron ingot | +3 armor | guards |
| Goat | Combat | wheat | 40% less fall damage | guards |
| Panda | Combat | bamboo | stand still a moment and you slowly heal | guards |
| Vex | Combat | emerald | +10% attack speed | guards |
| Donkey | Pack | golden carrot | +2 hearts | waits |
| Mule | Pack | apple | +30% knockback resistance | waits |
| Llama | Pack | hay bale | spits at mobs that hit you | spits at monsters that come close |
| Camel | Pack | cactus | speed on sand, soul sand and snow | waits |
| Sniffer | Pack | torchflower seeds | now and then digs up treasure while you walk on dirt, sand or gravel (armor trims rarely, seeds and flowers) | digs up finds around its post |
| Horse | Speedster | sugar | +8% movement speed | waits |
| Fox | Speedster | sweet or glow berries | mobs notice you from 25% shorter range | waits |
| Rabbit | Speedster | carrot or dandelion | Jump Boost | waits |
| Ocelot | Speedster | tropical fish | creepers and phantoms leave you alone; you often wake up to a gift | creepers and phantoms near the post leave everyone alone |
| Strider | Speedster | warped fungus | Fire Resistance in the Nether | waits |
| Cat | Utility | cod | as the Ocelot | as the Ocelot |
| Bee | Utility | any flower | crops **and saplings** around you grow fast, like Bloom's Growth Aura but always on while the Bee is out (no hoe needed; 2026-11-05: the pet is the other way to get it, for players who'd rather keep their Major points). A bit better than the Major on purpose: the Major stays as it is (crops only, hoe in hand). Doesn't stack with the Major: owning both is no faster | tends the crops around its post |
| Allay | Utility | amethyst shard | pulls nearby drops to you and bottles loose XP into its storage | collects drops and loose XP around its post |
| Chicken | Utility | seeds | you glide down long drops | waits |
| Cow | Utility | wheat | clears one bad effect every 2 minutes | waits |
| Turtle | Utility | seagrass | you breathe underwater | waits |
| Frog | Utility | slime ball | Dolphin's Grace while swimming | waits |
| Pig | Utility | carrot, potato or beetroot | Lumberjack: fells whole trees, the same as Bloom's Timber (kept on purpose, like the Bee's; doesn't stack with the Major) | waits |
| Axolotl | Utility | tropical fish (or a bucket of it) | you slowly heal while hurt, faster in water | waits |
| Bat | Utility | melon slice | Night Vision | waits |

Left out: the Armadillo (not in 1.20.1). **The Vex** has no storage and can't be ridden; it's the only pet that wears
gear (armor and a weapon, which adds to its damage).

## Combat pets' skills

The six combat kinds each have their own names for the shared skills, plus a starting and a special skill of their own.
A Migamigos or Zoni's companion rolls one of these kinds (not the Vex) when it first joins the party. Levels are the pet's own (the plugin's
1000 scale divided by 10).

| Level | Skill | Wolf | Iron Golem | Vex | Polar Bear | Goat | Panda |
|---|---|---|---|---|---|---|---|
| 1 | Starting skill | Pack Bond | Iron Colossus | Double Strike | Unyielding | Momentum | Bamboo Blessing |
| 10 | Awareness | Keen Nose | Construct Awareness | Phantom Awareness | Arctic Awareness | Mountain Awareness | Bamboo Sense |
| 15 | Gore | Rending Bite | Crushing Blow | Spectral Rend | Mauling Blow | Headbutt | Heavy Paw |
| 20 | Special skill | Alpha Howl | Guardian Intervention | Phase Strike | Crushing Presence | Ram | Soothing Presence |
| 20 | Fast Food | Blood Hunger | Self Repair | Soul Siphon | Feast | Graze | Snack Break |
| 25 | Thick Fur | Winter Coat | Iron Plating | Ethereal Form | Thick Hide | Thick Horns | Dense Fur |
| 35 | Holy Hound | Moonblessed | Arcane Core | Voidfed | Winterborn | Mountain Spirit | Inner Balance |
| 50 | Shock Proof | Storm Hide | Reinforced Frame | Stormproof | Avalanche Proof | Rock Solid | Unshaken |
| 75 | Sharpened Claws | Wolfsteel Fangs | Forged Fists | Spectral Blade | Rending Claws | Hardened Horns | Heavy Paws |

**The Wolf's skills get new names and reworded effects** (FreshOrangeSlices, 2026-11-04: mcMMO's wolf shouldn't show
through). Names agreed 2026-11-05:

| Level | Wolf | What it does |
|---|---|---|
| 1 | Pack Bond | when you and it hit the same target within 3 seconds, its bite deals 20% more |
| 10 | Keen Nose | it smells danger: steps out of fire and lava back to you, and lands soft (no fall damage) |
| 15 | Rending Bite | a chance its bite tears deep: twice the damage, and the target Bleeds |
| 20 | Alpha Howl | in a fight, every 30 seconds it howls: you and your party pets get Strength I for 6 seconds, enemies near it are slowed |
| 20 | Blood Hunger | a chance its bites mend it by the damage they deal |
| 25 | Winter Coat | half damage from blows and arrows; fire goes out on it fast |
| 35 | Moonblessed | poison, wither and magic mend it instead of hurting it |
| 50 | Storm Hide | explosions and lightning barely scratch it (a sixth of the damage) |
| 75 | Wolfsteel Fangs | +2 bite damage |

What the shared ones do: **Awareness** steps out of fire, lava and cacti back to you, no fall damage; **Gore** a chance
of double damage (just the plugin's name for it, nothing gory), half the pet's level in %, up to 50% at 100 (the plugin's 1% a level went to 100%); **Fast Food** 50% chance to heal by the damage it
deals; **Thick Fur** half damage from attacks and arrows (the golem: fire too), shakes off burning; **Holy Hound** magic,
poison and wither heal it; **Shock Proof** 1/6 damage from explosions and lightning; **Sharpened Claws** +2 damage on
every hit.

Starting and special skills:
- **Iron Colossus**: a walking fortress with 100+ health. **Guardian Intervention**: once a Minecraft day, a blow that
  would kill you leaves you at half a heart with Resistance II for 20 seconds (only while it follows you; a totem in your
  hand goes first).
- **Double Strike**: a 2% chance to hit twice as hard, +2% every 5 levels, up to 10%. **Phase Strike**: its strikes
  ignore 25% of the target's armor (50% from level 50).
- **Unyielding**: can't be knocked back, slowed or frozen. **Crushing Presence**: its hits slow (II) and weaken (I) for 5
  seconds (Slowness IV and Weakness II from level 50).
- **Momentum**: every third hit lands 30% harder. **Ram**: that third hit also launches the target about 6 blocks up.
- **Bamboo Blessing**: when it wins a fight, it and you get Regeneration I for 8 seconds. **Soothing Presence**: a chance
  on each hit (15% at level 20, up to 60% at 100) to heal it, you and friends within 6 blocks, and hurt the undead (3
  health at 20, +1 every 10 levels, up to 9).

Non-combat pets have no skill tree (their bonus, job and storage are what they bring). At level 100 every pet
gets a gold name and a sparkle, a second wind once each time it comes out, and a bigger area at its post.

## One and the Same (Bloom's Combat Major, the bridge)

- A Combat Major in Bloom (it exists today hidden and switched off, waiting for this). **In the Combat tab, under
  Copycat, for 3 Major points** (FreshOrangeSlices, 2026-11-05).
- **It shows the pet screen's Pet / Bloom toggle** (2026-11-05: rather than a pet slot of its own): without it the toggle
  isn't there; with it, the Bloom view lets the player **pick which weapon tree the pet follows**. The choice stays;
  **the player can reset it, but that resets the pet's whole level** (back to 1).
- **Who picks a tree** (2026-11-05): combat Bloom pets (Wolf, Iron Golem, Polar Bear, Goat, Panda; the Vex when it
  comes) and modded companions, one tree each (Sword, Axe, Trident, Unarmed; Bow for pets that shoot; Crossbow no).
  Non-combat pets don't (their bonus and job are what they bring). A tree works while the pet is in the party or at
  its post, like its skills; **leaving the party only disables it**: nothing is reset or undone.
- **The pet's own level opens its tree** (2026-11-05: "pet based", not the player's Major Levels), the same table for
  every pet and companion: levels 10, 25, 50, 75. Gaps in a tree are filled with Bloom's universal passive combat
  Majors. **Disarm at 75. No Bow tree** (2026-11-05: more trouble than it's worth). **A pet's Execute finishes
  nothing**: it hits harder on a boss under 50% health and on anything else under 70% (2026-11-05). **A pet's Disarm
  drops the item as a player's does** (fine: farming it would be on purpose). Cooldown abilities (Charge Attack,
  Lightning Summon, the leap) each switch off in the screen (Lightning Summon would get annoying). Picking: the Bloom
  view shows the four trees; click one, confirm; then its effects fill the slots, dimmed until their level; a Reset
  button warns first. **A reset costs a Bloom pet half its current level, another mod's companion three quarters of
  its level (FreshOrangeSlices, 2026-11-05; first it was back to level 1); a Migamigos Amigo keeps its level and waits 5
  hours instead**
  (its level is its own mod's, which Pets doesn't touch). **Migamigos companions use their own Amigo level** as their
  level (read from Migamigos); Zoni's companions, with none, use Bloom Pets' (the normal system: level 1-100 and XP as
  a Bloom combat pet's, FreshOrangeSlices 2026-11-05). **Amigos level at a Bloom pet's pace (agreed and built 2026-11-05):** Migamigos' own curve (XP only from its kills, the mob's orb XP; 10 x 1.1^level a level, to 99; +0.1 damage a
  level, +1 health every 2nd) makes Amigo 50 about 2,300 zombie kills and 75 about 25,000, so its tree's last effects
  would never come. Bloom Pets keeps its own count for an Amigo on the Bloom combat curve and, each time that count
  would level a Bloom pet, gives the Amigo one Amigo level's worth of XP; Migamigos levels it on its next kill, with its
  own health and damage. Up to Amigo level 75 (enough for the last tree effect; 75 to 99 stays Migamigos' own climb).
  An Amigo already in the world starts counting from its level, nothing owed for the past (`PetLevels.amigoPace`,
  `Amigos.giveLevel`; game tests `AmigoGameTests`, with Migamigos in Bloom Pets' test run). A pet's Execute: +30% (2026-11-05). The
  trees (agreed 2026-11-05, Predator rather than First Strike in the Trident's gap):

  | Tree | 10 | 25 | 50 | 75 |
  |---|---|---|---|---|
  | Sword | Slash | Full Sweep | Execute | Aura Sword |
  | Axe | Armor Break | Guaranteed Critical | Charge Attack (cooldown) | a leap on its target (cooldown) |
  | Trident | Lightning Shock | Predator | Lightning Summon (cooldown) | Overkill |
  | Unarmed | Brawler | Combo | Bloodlust | Disarm |
- **Its own skills and its tree work together** (2026-11-05: strong on purpose).
- **One and the Same off or lost** (toggled off, or a Major reset): the pets' trees are disabled, their picks and
  levels kept; owning it again brings everything back as it was.
- Earlier note (2026-11-04, before the party changed to one Bloom pet): owning it lets the player give **the 3 pets in
  the party one weapon tree each** (Sword, Axe, Trident, Unarmed; Bow maybe for pets that shoot; Crossbow no).
- ~~How much of a tree a pet can use grows with the player's total Major Levels earned~~ (replaced 2026-11-05: the
  pet's own level opens it).
- Passive effects work on the pet's own hits; effects the player triggers with a key become **cooldown abilities** the
  pet uses itself every 20 to 30 seconds, each switchable off.

| Tree | Effect | On a pet |
|---|---|---|
| Sword | Execute | its hits do more damage to a boss under 50% health and anything else under 70% (finishes nothing; 2026-11-05) |
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
| Bow | Marksman, Piercing Shot, Straight Shot | dropped (2026-11-05) |
| Crossbow | all | left out |

## Building it, in phases

**Phase 1a (built, 2026-11-04):** the `bloompets` mod and its CI job; the roster as data; bonding; every player's pets
kept with the world; the party, its cooldown and full health on joining; Call (any pet, any dimension); Bloom pets'
storage (fixed sizes for now: 9, 18 or 27); owners' hits never landing; `/bloompets` (also `/pets`) until the screen.
Not yet: the Vex (with its gear), levels and everything after.

**Phase 1b (built, 2026-11-04):** the pet screen, after the sketch: K, or the tab with a lead on it under Bloom's
hub button on the inventory (after Bloom's own buttons there; it was left of the hub button until 2026-11-05). The list (Bloom pets orange, companions dark red, the party marked pink, a filter: all,
party, Bloom pets, companions); the chosen pet's model following the mouse (the live one when it's nearby, else drawn
from its look, kept in its entry); four slots a side, empty until levels, with the Pet / Bloom switch at the top right;
health and where it is; Call, Join / Leave party (greyed out with the time left while the cooldown runs; companions
are always with you) and Storage (Bloom pets only). In the Skill Hub's look (FreshOrangeSlices, 2026-11-04): the hub's
dark panel and gold frame, its palette, the name large and gold like the hub's headings, the list in a box like the
hub's stats window, dark buttons with an orange edge under the mouse, and the pet standing on a piece of the hub's art
(the combat cave for fighters and companions, the meadow for the rest).

**Phase 2 (built, 2026-11-05):** pet levels 1 to 100, kept on the pet itself (`bloompets.level`, `bloompets.xp`) and in
its entry; the curves, XP sources and per-kind multipliers above (in `PetsConfig`'s `levels` section, the multiplier in
the roster's `xp_multiplier`); a level-up message naming the skills it opens. The nine skills for the five combat kinds
(the roster's `skills`: wolf, iron_golem, polar_bear, goat, panda), the plugin's numbers, Gore at half the level in %
(Rending Bite also Bleeds, with Bloom's Bleed when Bloom is there); skills work while the pet is in the party and awake.
The golem, polar bear, goat and panda in the party fight for their owner (whatever hurts them or they hit; the goat gets
a melee attack of its own); pets never hurt or go after players (co-op) or their owner's other pets. Party pets faint
instead of dying: 5 minutes' rest where they fell, unhurt, then up with half their health (`/kill` still kills; the void
brings them to their owner first). Level 100: a gold name, a sparkle, a second wind once each time it comes out (called,
or joining the party). Sneak-feeding your own pet its food heals it (and gives a non-combat party pet XP, at most every
30 seconds). The screen: the level beside the kind and in the list, an XP bar, five slots a side (the nine skills and the
level-100 perk, item icons, dimmed with their level until open, a tooltip with what each does at the pet's level), the
rest's countdown, the list kept fresh every 2 seconds. Operators: `/bloompets level <level> <pet>` and
`/bloompets xp <amount> <pet>`. Not yet: modded companions' Spirit and its reroll (Phase 5; they level already), job XP
for pets at posts (Phase 3).

**Phase 3a (built, 2026-11-05): mood and hunger** (`PetMood`), the plugin's happiness with a food bar beside it, for
Bloom pets (modded companions keep their own mods' needs until Phase 5). Mood 0 to 100, shown as 10 hearts (a bar in
the screen): it drops a point every 3 minutes in the party (three times as fast once it's tired, after 90 minutes in the
party; twice while hungry, four times starving; a quarter while it loves you); at home it rests (tiredness gone in 5
minutes) and, if fed, cheers up 10 a minute. Feeding cheers it up 15 (once a minute), a pat (sneak + right-click with
an empty hand; not horses, donkeys, mules, llamas or camels, which open their inventory that way) 5 (every 30 seconds);
full mood from your care and it loves you for 30 minutes (fainting ends it). Under 30 it sulks: no skills, bonus, job or
storage until it cheers up. Hunger 0 to 20: a point every 4 minutes in the party (a full bar lasts 80 minutes); its
favourite food fills 6 (feeding it, or by itself from its storage at 14 and under); 15 and up it slowly heals. The
screen shows both bars (with tooltips) and "sulking" in its status.

**Phase 3b (built, 2026-11-05): bonuses for the owner** (`PetBonuses`), every kind in the roster (the Vex's when it
comes), the plugin's numbers times the pet's bonus power (1 at level 1, 1.45 at 100). Who gives one: the party pet, and
up to 3 of the owner's non-combat pets within 32 blocks of them (the nearest; one of each kind, the highest level); not
while sulking or fainted. Stat bonuses are modifiers on the player that go when the pet does. The Bee and the Pig use
Bloom's own Growth Aura (crops and saplings, no hoe, its chance times the Bee's power) and Timber: Bloom Pets keeps a
short-lived flag on the player (`bloompets.growth`/`growthUntil`, `timberUntil` in their persistent data) and Bloom's
`UtilitySkill` honours it, so there's one aura and one Timber, and owning the Major too is no faster. The screen: the
bonus under the mouse on a pet's level line, and in a non-combat pet's first slot (its spawn egg).

**Phase 3c (built, 2026-11-05): posts and jobs** (`PetPosts`). From level 8 (`postLevel`) a Bloom pet out of the party
can be left where it stands (the screen's Post button, `/bloompets post`), 3 posts a player (`maxPosts`). It strolls
around its post (6 blocks; called away, or a long chase, it pops back) and does its job: combat pets guard (the nearest
monster within 16 blocks, not creepers, endermen and piglins only once angry), the Bee tends crops and saplings (4 every
3 seconds ÷ its power, 6 blocks), the Allay collects drops and loose XP (as bottles) into its storage (8 blocks; not if
another player is nearer), the Sniffer digs (every 4 minutes ÷ power; a trim now and then while its owner's about), the
Llama spits at monsters (10 blocks), the Cat and Ocelot keep creepers and phantoms off everyone near (24 blocks). Level
100: half again the area. A pet at its post uses its skills, faints rather than dying, gets hungry and glum (a point
every 2 minutes) only while its owner's online, and earns **job XP**: a point a crop, an item or bottle, 10 a find, 2 a
spit, 30 a minute at most (guards earn their fighting XP). Calling it, or adding it to the party, takes it off its post.

**Phase 4 (built, 2026-11-05): One and the Same.** In Bloom: the Major switched on, 3 Major Levels, drawn under Copycat
(flag `under_copycat`), its text saying what it does. In Bloom Pets (`PetTrees`): the four trees above on combat Bloom
pets and companions, each pet's pick, its switched-off abilities and a companion's wait kept on the pet (and in its
entry); `BloomBridge` asks Bloom's `BloomApi.isMajorActive` by name whether its owner has the Major (remembered while
they're away, so a pet at its post keeps its tree as they left it), and reads Bloom's Bleed and boss list. Effects on a
pet's own melee hits, Bloom's numbers where they fit a pet (Slash 50% Bleed 3 s, Full Sweep 1 + the hit around the
target, Aura Sword every 5th hit 4 blocks, Armor Break half the damage as durability, Lightning Shock 3 sparks at 50%
once per 2 s, Predator +10%/hit to +50%, Overkill 60% of the excess to 3 enemies, Combo +0.25/hit to +5, Bloodlust +8%
a kill for 10 s to 5 stacks, Disarm 30%); for a pet: Brawler +3 (a player's +6 is for bare fists), Guaranteed Critical
×1.35 (no −2 penalty, which would make a wolf weaker), Execute +30% under 50% (bosses) or 70%, Charge Attack a double
hit every 25 s, Leap onto a target 4 to 12 blocks off every 25 s (its landing hit +50%), Lightning Summon every 25 s:
the look of lightning, 6 damage and fire to its target only. A tree's sweeps and sparks set off nothing more (no skills,
no second sweep). Migamigos companions use their own Amigo level and XP (`Amigos`, read by name; Bloom Pets never adds
to it). The screen: the Pet / Bloom switch only with the Major; the Bloom view picks a tree (its four effects listed),
then shows them with Bloom's own icons, dimmed until their level, cooldown ones switching off with a click (a red ✕),
the tree at the top right and Reset at the bottom right, both asking first in a box over the pet. `/bloompets tree
<sword|axe|trident|unarmed|reset> <pet>`. One and the Same's icon is Bloom's question mark (FreshOrangeSlices, 2026-11-05). `/bloompets screen bloom`
opens the screen on its Bloom view; the pack test buys the Major and shows the wolf's Sword tree (`pack_pets_bloom`). Game tests: `PetTreeGameTests` (and Bloom's smoke test checks the Major
loads at 3).

Each phase is its own chunk: built, tested (game tests on plain wolves first; Migamigos and Zoni's later), pushed.

1. **The mod and the list**: the `bloompets` mod and its CI job; tracking owned pets; the party (3 places); the screen with
   Call and Inventory.
2. **Levels and skills**: XP sources, levels 1 to 100, the skill table, fainting instead of dying, max-level perks.
3. **Mood and bonuses** (whatever of the plugin's engagement layer is kept): mood, petting, owner bonuses, posts (built
   as 3a, 3b, 3c; fetch is left for later).
4. **One and the Same**: the Major in Bloom, weapon trees on pets, cooldown abilities and their switches.
5. **The pack**: Migamigos and Zoni's per-kind skills (Spirits), their mood and bonus, fetch, a quest chapter, tuning.
   **Dragons are out of Bloom Pets altogether** (FreshOrangeSlices, 2026-11-05: no dragons' limits step).

**Phase 4 polish (agreed and built, 2026-11-05; Bloom Pets 0.4.0):** a locked tree effect shows its icon greyed, its
level small in the corner; the left column's 5th slot in the Bloom view is the pet's stats (a book: damage per hit, or an
Amigo's damage from its levels, the extras every hit gets, health and armor; loaded pets only); an Amigo with levels
waiting says "Levels up on its next kill"; **the softer reset** (above: half a Bloom pet's level, three quarters of a
companion's, an Amigo's 5-hour wait); Bloom Pets' version follows its phase (0.4.0; CI's build number stays on the end);
`/bloompets info <player> <pet>` for operators (`@s` for your own; the player first, since a pet's name can have spaces). No upgrade test (going all in). Kept as they are: how strong Amigos can
get (a strong build is the player's choice) and how full the screen is.

**Phase 5, agreed before building (FreshOrangeSlices, 2026-11-05):**
- **Spirits**: a companion gets its Spirit when it's added to its owner's pet list (companions never join the party),
  an even chance of each combat kind (Wolf, Iron Golem, Polar Bear, Goat, Panda; **never the Vex**), with the same skill
  numbers as a Bloom pet of that kind, at the companion's level. **One reroll, ever** (the free one, from the pop-up the
  first time its page opens; it can't land on the same kind). An Amigo (top level 99) gets the level-100 perks (gold
  name, second wind) at 99.
- **Companions' mood and hunger**: the same bars and rates as a Bloom pet's; they eat cooked vanilla food, fed with sneak
  + right-click (a plain right-click is Migamigos' own menu); a sulking companion's Spirit skills and tree rest.
- **No owner bonus from companions** (a player can have an army of them).
- **Fetch** (Bloom pets only), like the plugin: **a ball** (a blue ball with the Heart of the Sea's look, named Ball),
  one given to the player with every new pet (any ball works with any pet that plays, so players end up with plenty;
  no recipe, FreshOrangeSlices 2026-11-05). As the plugin's Fetch Stick (`PetManager.throwFetch`, `fetch`, `endFetch`;
  `Species.playsFetch`): **every Bloom pet but the Iron Golem and the Vex plays; companions don't**; only **the pet in
  the party** plays (not one at a post, fainted or ridden); right-click throws the ball (no one else can pick it up);
  the pet runs (or flies) to it, carries it back on its head and drops it at your feet, and the ball is back in your
  inventory (also if it can't reach it or after 25 seconds, so it's never lost); hearts, a sound, "Name brought the ball
  back! ♥". **Each fetch**: the plugin's +2 bond (one minute of its time-together XP) is **22 XP** here (one minute of
  ours), and its +8 happiness is **+8 mood**, each at most every 15 seconds.
- **The Spirit pop-up** shows just its name ("Claymore gained the Spirit of the Wolf") and asks keep or reroll.
- **A "Pets" chapter in the quest book**: bond a pet, reach level 10, post a pet, a pet at level 25, buy One and the
  Same, pick a tree. Rewards: some XP and Bloom XP, nothing more (FreshOrangeSlices, 2026-11-05).
- **Order** (FreshOrangeSlices, 2026-11-05): companions' mood and hunger first (5a), then Spirits (5b), then fetch and
  the quest chapter (5c), one push each.

**Phase 5a (built, 2026-11-05): companions' mood and hunger** (`PetMood`, `Bonding`). Bloom pets' bars and rates for
other mods' companions (Migamigos', Zoni's). A companion never joins the party, so it counts as **out while it's within
32 blocks of its owner** (online, same dimension): its mood drops, it tires and gets hungry, and it mends while well fed,
as a party pet; **anywhere else it rests**, as a pet at home (tiredness goes, a fed one cheers up, no hungrier), so an
army left at a base needs no feeding. It eats **cooked vanilla food** (`#bloompets:companion_food`: cooked meat and
fish, baked potatoes, bread, dried kelp, cookies, pumpkin pie, the stews; the pack can add to it), sneak + right-click;
sneak + right-click with an empty hand pats it; both are cancelled before its own mod sees them (a plain click is still
Migamigos' backpack). **One that's out eats by itself from its own inventory** at 14 food and under, as a Bloom pet from
its storage (FreshOrangeSlices, 2026-11-05: `CompanionInventory`, a Migamigos companion's backpack through its
`getInventory()`, a Zoni's companion's slots through Forge's item capability). **Its news goes above the hotbar**, not
to chat (a player can have an army). **A sulking companion's tree rests** (and its Spirit, from 5b).
The screen shows its mood and food bars with its own help texts. Game tests: `CompanionCareGameTests` (a Migamigos
Claymore).

**Phase 5b (built, 2026-11-05): Spirits** (`Spirits`). A companion gets its Spirit the moment it's added to its
owner's pet list (companions listed before this version, the first time it sees them): an even pick of Wolf, Iron
Golem, Polar Bear, Goat and Panda, never the Vex. **The owner is told at once** in chat ("Claymore gained the Spirit of
the Wolf. [Keep] [Reroll]", FreshOrangeSlices: instantly, the buttons running `/bloompets spirit keep|reroll <pet>`),
and the pet screen asks too (Keep / Reroll in the box over the pet) until it's settled. **One reroll, ever**, always
to another kind; after Keep or the reroll it's settled. Its skills are that kind's nine with a Bloom pet's numbers
(Iron Colossus too: kept, FreshOrangeSlices 2026-11-05), at the companion's level (an Amigo's own), working whenever
it isn't sulking; a companion with the Iron Golem's Spirit (level 20+, within 32 blocks) can step in as Guardian
Intervention. The screen shows "Spirit of the Wolf" where a Bloom pet shows its category, and the skills in its slots.
**Top level**: 100, or an Amigo's 99 (FreshOrangeSlices: the perks at 99): a gold name, a sparkle, and a second wind
once each time it's called (a blow that would kill it leaves it at half health; before the add-on's keepsake).
`/bloompets info` shows the Spirit. Game tests: `SpiritGameTests` (a Migamigos Claymore).

**Phase 5c (built, 2026-11-05): fetch and the Pets chapter.** **The Ball** (`bloompets:ball`, the Heart of the Sea's
look, stacks to 16, no recipe): one is given with every new Bloom pet (bonded, tamed or given; FreshOrangeSlices: Bloom
pets only). Right-click throws it for the pet in your party (`Fetch`, the plugin's rules above: every Bloom pet but the
Iron Golem and the Vex; not while it's fainted, sitting or ridden); a goal ahead of all its others runs it to the ball
and home, the ball on its head; at your feet the ball is back in your inventory, as it is when the pet can't reach it,
after 25 seconds, or when anything ends the game (a thrown ball left over after a restart becomes an ordinary ball
anyone can pick up). Each fetch: 22 XP and +8 mood, at most every 15 seconds. **Quiet advancements**
(`bloompets:pets/bond|level_10|level_25|post|one_and_the_same|tree|fetch`, no display, awarded by code:
`PetAdvancements`) for **the quest book's Pets chapter** (`pack/quests/32_pets.json`, a flexible guide chapter): A Friend
for Life (bond), Go Get It! (fetch, optional), Growing Up (level 10), On Guard (a post), Old Friends (level 25), One and
the Same, Sword, Axe, Trident or Fists (a tree); rewards in vanilla levels and Bloom XP for every tab
(FreshOrangeSlices: all tabs; 25 to 50 each, 100 each for the last two). Game tests: `FetchGameTests`.

**Playtest fixes (FreshOrangeSlices' first playtest, 2026-11-05; Bloom Pets 0.5.0):**
- **Follow, Wander or Stay for every pet** (`PetModes`), Bloom pets and companions alike: the button at the top left of
  the pet's picture (click for the next), `/bloompets mode <follow|wander|stay> <pet>`, kept on the pet
  (`bloompets.mode`). Follow walks after you (a fighter fights for you); Wander roams around where you left it; Stay
  stands on its spot (it leaves it only to fight, then walks back). Joining the party sets Follow, leaving it Wander
  where it is, a Call Follow; a posted pet keeps its post whatever its mode. A wolf, cat or parrot stays by sitting; a
  Migamigos companion through its own Follow / Wander / post (by name); everything else through a goal ahead of its
  others (`PetGoals.Keep`), which also holds a Zoni's companion "sitting" so its own follow goal lets go. Goals now
  come the first second a pet is listed (before, a pet bonded mid-game had none until it was next loaded).
- **Your target first**: a fighter on Follow (the party pet, or any you set to Follow) goes for whatever you hit ahead
  of what's hitting it or you, and switches the moment you hit something else (within 32 blocks; wolves too).
- **News above the hotbar**: level-ups, Spirits (the screen still asks Keep or Reroll), the Ball, bonding, fainting,
  bonuses, mood. Only a death and a lost call stay in chat.
- **Companions aren't patted or fetched**: a sneak + right-click with an empty hand is theirs (Bocchi's own controls).
- **A food pouch for companions with no inventory of their own** (Zoni's: only their gear): 2 slots of cooked food,
  the pet screen's Storage; it eats from there when it's hungry and out with you (a Migamigos one still eats from its
  backpack).
- **A fallen companion comes back** (`Comeback`): Zoni's summon device brings a companion back as a new one, so its
  level, XP, Spirit, tree, mood and name are kept for its owner by its kind and the next one of that kind they get picks
  them up (the newest fallen first; not Migamigos, whose keepsake brings the same one back). Its second wind is taken
  as the killing blow lands (it was at death, when Zoni's had already dropped its gear and swapped its device).
- **Rename** (the pencil, top right of the picture; `/bloompets rename "<name>" <pet>`), so two of a kind can be told
  apart; **Release** (the ✕ beside it, asking first; `/bloompets release <pet>`): every pet goes back to the wild
  (FreshOrangeSlices: Ganyu and Claymore too), a Migamigos companion untamed in Migamigos as well (its owner cleared,
  only if its save still writes); **a summoned companion (Zoni's: crafted, never wild) goes back into its device**
  (0.5.3): its gear drops at its feet, it's gone, and its owner gets its summon item (`<kind>_summon_item`, in place of
  an `empty_summon_item` they carry); its storage comes back to them; a pet that isn't loaded is set free when it next
  loads (a summoned one on its first tick); companions released by 0.5.0 or 0.5.1 (only taken off the list) are freed
  the same way as they load. Companions stay fireproof where their mod makes them so (Zoni's; FreshOrangeSlices: leave
  it).
- Game tests: `PetModeGameTests`, `ZoniGameTests` (Zoni's Companions' Bocchi, now in the test run too).
- **Hidden armor** (Bloom Pets 0.5.1 with the add-on's 0.2.0): the dot at the top right of a companion's picture (left
  of rename; green shown, red hidden) hides all its armor for everyone, or shows it again; it still protects. The add-on's `armor/HiddenArmor`
  does the hiding (players hide theirs piece by piece by the inventory's armor slots, or `/hidearmor`); Bloom Pets calls
  it by name (`ArmorBridge`), so without the add-on there's no dot.
