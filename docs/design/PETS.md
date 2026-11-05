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
| Fetch, petting | Kept |
| Riding every pet | Kept for Bloom pets, as a late phase. **Modded companions can't be ridden** (Migamigos and Zoni's are people, not mounts); dragons aren't in the list (they ride their own way) |
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
- **Out of the party a pet is unprotected** (2026-11-05): plain vanilla stats, and it can die like any dog; only the
  party pet faints. **Open** (an idea): a limit on how many pets a player can have, with more slots for levels or
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

Non-combat pets have no skill tree (their bonus, job, storage and riding are what they bring). At level 100 every pet
gets a gold name and a sparkle, a second wind once each time it comes out, and a bigger area at its post.

## One and the Same (Bloom's Combat Major, the bridge)

- A Combat Major in Bloom (it exists today hidden and switched off, waiting for this).
- Owning it lets the player give **the 3 pets in the party one weapon tree each** (Sword, Axe, Trident, Unarmed; Bow maybe
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

**Phase 1a (built, 2026-11-04):** the `bloompets` mod and its CI job; the roster as data; bonding; every player's pets
kept with the world; the party, its cooldown and full health on joining; Call (any pet, any dimension); Bloom pets'
storage (fixed sizes for now: 9, 18 or 27); owners' hits never landing; `/bloompets` (also `/pets`) until the screen.
Not yet: the Vex (with its gear), levels and everything after.

**Phase 1b (built, 2026-11-04):** the pet screen, after the sketch: K, or the tab with a lead on it just left of Bloom's
hub button on the inventory. The list (Bloom pets orange, companions dark red, the party marked pink, a filter: all,
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
instead of dying: a minute's rest where they fell, unhurt, then up with half their health (`/kill` still kills; the void
brings them to their owner first). Level 100: a gold name, a sparkle, a second wind once each time it comes out (called,
or joining the party). Sneak-feeding your own pet its food heals it (and gives a non-combat party pet XP, at most every
30 seconds). The screen: the level beside the kind and in the list, an XP bar, five slots a side (the nine skills and the
level-100 perk, item icons, dimmed with their level until open, a tooltip with what each does at the pet's level), the
rest's countdown, the list kept fresh every 2 seconds. Operators: `/bloompets level <level> <pet>` and
`/bloompets xp <amount> <pet>`. Not yet: modded companions' Spirit and its reroll (Phase 5; they level already), job XP
for pets at posts (Phase 3).

Each phase is its own chunk: built, tested (game tests on plain wolves first; Migamigos and Zoni's later), pushed.

1. **The mod and the list**: the `bloompets` mod and its CI job; tracking owned pets; the party (3 places); the screen with
   Call and Inventory.
2. **Levels and skills**: XP sources, levels 1 to 100, the skill table, fainting instead of dying, max-level perks.
3. **Mood and bonuses** (whatever of the plugin's engagement layer is kept): mood, petting, owner bonuses, maybe posts.
4. **One and the Same**: the Major in Bloom, weapon trees on pets, cooldown abilities and their switches.
5. **The pack**: Migamigos and Zoni's per-kind skills, dragons' limits, a quest chapter, tuning.
