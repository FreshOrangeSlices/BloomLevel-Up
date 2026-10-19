# Bloom Level-Up

A Fabric mod for Minecraft **26.3**: a modpack-friendly character build system inspired by Path of Exile
and mcMMO. Four skill categories (Utility, Combat, Armor, Misc) level from use, each level gives a stat
point, and every 10 levels gives a shared **Major Level** you can spend on rule-breaking abilities in any
category. Post-100 XP Investment and the Bloom+ reset add long-term growth.

## Status

Built in chunks. Done so far:

- **Chunk 1 — foundation:** project setup, data-driven category / stat / Major definitions (datapack JSON),
  versioned persistent player profile, the full shared progression rule set (levels, stat points, global
  Major Levels, prerequisites, conflicts with swap, toggles, refunds, XP funding, XP Investment, Bloom+),
  config file, and a temporary `/bloom` command front end. Pure-Java core covered by unit tests.
- **All four categories defined:** 15 stats and 66 Majors (Utility 6, Combat 31, Armor 16, Misc 13) with
  costs, prerequisites and names. Their actual effects are built category by category later.

- **Chunk 2 — Skill Hub:** the hub screen (button beside the inventory, optional keybind), live sync with
  the server, and the first real effects: Vitality, Defense, Power, Tempo, Speed and Titan Hide.

- **Chunk 3 — Armor:** Armor XP from taking damage, Vitality / Defense / Dodge / Durability, armor
  overcap, and all 16 Armor Majors (Fitting Armor, Last Breath, Castle of Stone, Absorb, Reflect...).
- **Chunk 4 — Utility:** mcMMO-style Utility XP (mining, woodcutting, digging, farming, fishing,
  shearing; player-placed blocks don't count), Efficiency / Yield / Preservation, and all 6 Utility
  Majors (Vein Mining, Timber, Shovel Mastery, Growth Aura, Master Shearing, Triple Catch).

- **Chunk 5 — Combat:** Combat XP from real damage (plus a kill bonus), Critical / Penetration / Looting,
  and the Combat Majors, including the ones with controls (Charge Attack, Catch!, Assault Rifle,
  Lightning Summon) and Copycat.

- **Chunk 6 — Misc:** Misc XP from collected XP orbs, Wisdom / Speed / Endurance, and 11 of the 13 Misc
  Majors (Iron Stomach, Selective Cleansing, XP Insurance, Second Serving, Water to Wine, Overdose,
  Hoarder's Pocket, Rainbow, Become My Fuel, Overenchant, Ascension). Stack Overflow and Rune Embedding are
  hidden until their own chunk (they need deeper inventory/enchantment work).

Every push also boots a test server and runs game tests for the Armor, Utility, Combat and Misc mechanics,
and boots a real client into a world (client-test workflow).

## Getting the jar

Every push builds automatically. Open the **Actions** tab → latest **build** run → download
**bloomlevelup-jar**. Drop it in `mods/` together with Fabric API.

## Trying it in game

Open your inventory and click the button just outside its top-right corner, or bind **Open Skill Hub**
in Controls. In the hub:

- Category tabs along the top. Stats on the left: **+ / −** to spend or take back points (shift = 10),
  **On/Off** to switch a stat without losing points.
- Majors on the right: click to buy, click an owned one to switch it on/off, right-click to refund.
  Glowing = on, dim = owned but off, dark = not owned. Hover for details.
- **Spend XP** levels the category with vanilla XP (every category also levels from playing now).
- Vein Mining and Timber: hold **sneak** while breaking to skip them for that block.
- Copycat: **shift-click** an owned weapon Major in the hub to copy it; shift-click Copycat to stop.
- Combat controls (rebindable in Controls → Bloom Level-Up): **hold left click** to charge (Charge
  Attack / Heavy Hands), **right click** with a sword/axe/mace to throw it (Catch!), **sneak + left click**
  with a trident to call lightning (Lightning Summon), **sneak while releasing** a trident to riptide
  (Unrestricted Riptide).
- Hoarder's Pocket: the **chest button** under the hub button in your inventory, `/bloom pocket`, or bind
  **Open Hoarder's Pocket** in Controls.
- Ascension: **double-tap jump** to fly, like creative.
- Become My Fuel: hold the weapon in your **main hand** and drink a harmful potion from your **off hand**.

Admin commands are still there for testing:

| Command | What it does |
|---|---|
| `/bloom` | Your levels, stats, Major Levels and owned Majors |
| `/bloom allocate <stat> <amount> [invested]` | Put points into a stat (negative to take them back) |
| `/bloom buy <major> [swap]` · `/bloom refund <major>` | Buy / refund a Major (`swap` accepts a conflict trade) |
| `/bloom toggle stat <stat>` · `/bloom toggle major <major>` | Switch an effect on/off without losing it |
| `/bloom fund <category> <xp>` | Spend vanilla XP to level a category below 100 |
| `/bloom invest <category> <count>` | Buy permanent post-100 points (after mastering a category) |
| `/bloom bloomreset confirm` | Bloom+ reset |
| `/bloom pocket` | Open Hoarder's Pocket |
| `/bloom admin xp <players> <category> <amount>` · `/bloom admin definitions` | Ops: grant XP, show loaded data + warnings |

## For pack makers

- Categories: `data/<ns>/bloom/category/<name>.json`
- Majors: `data/<ns>/bloom/major/<path>.json` (cost, prerequisites, conflicts, toggleable, icon, flags)
- Utility block XP: `data/<ns>/bloom/xp/blocks/<name>.json` — `{"category": "bloomlevelup:utility", "values": {"#c:ores": 3.0, "minecraft:stone": 0.5}}`
- Tags that decide what counts (all under `bloomlevelup:`): blocks `vein_mineable`, `timber_logs`,
  `timber_leaves`, `yield_eligible`, `excavation_treasure`, `growth_aura_crops`, `mature_only`, `xp_column`;
  items `armor/light|medium|heavy`, `weapon/sword|axe|bow|crossbow|trident|mace|spear|blade|blunt`,
  `food/meat`, `food/unsafe`, `food/vegetables`, `food/cleanse_excluded`, `pocket_blocked`, `rainbow_ammo`,
  `utility_tools`; entity types `bosses`, `no_combat_xp`; damage types
  `dodgeable`, `elemental`, `melee`, `blade`, `blunt`; effects `damage_over_time`, `unyielding_ignored`
- Shovel treasure: loot table `bloomlevelup:gameplay/excavation_treasure`
- Tuning: `config/bloomlevelup.json`

## Layout

See `docs/design/` for the design documents and `docs/DECISIONS.md` for choices made during
implementation. Code: `definition/` (data model), `progression/` (rules), `player/` (state + saving),
`stats/` (formulas), `data/` (datapack loading), `command/`, `api/`, `registry/` (all game hooks).
