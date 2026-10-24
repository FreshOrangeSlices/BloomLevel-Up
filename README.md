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

- **Chunk 6 — Misc:** Misc XP from collected XP orbs, Wisdom / Speed / Endurance, and the Misc Majors
  (Iron Stomach, Selective Cleansing, XP Insurance, Second Serving, Water to Wine, Overdose, Hoarder's
  Pocket, Rainbow, Become My Fuel, Overenchant, Ascension).
- **Chunk 7 — the last part:** Rune Embedding, Stack Overflow, Medium armor's shield-disable reduction and
  the `[lvl N]` nameplate. Every Major in the design is now in except One and the Same, which waits for
  the Pet system.

Every push also boots a test server and runs game tests for the Armor, Utility, Combat and Misc mechanics,
and boots a real client into a world (client-test workflow).

## Getting the jar

Every push builds automatically. Open the **Actions** tab → latest **build** run → download
**bloomlevelup-jar**. Drop it in `mods/` together with Fabric API, and delete the old Bloom jar.

Each jar carries its build number: `bloomlevelup-0.1.0+build.57.jar` is build 57 (the run number on the
Actions tab). The same number shows in the bottom-right corner of the Skill Hub and in Mod Menu, so
client and server can be checked at a glance; they should always run the same build.

## Trying it in game

Open your inventory and click the button right of the recipe book, or bind **Open Skill Hub** in
Controls. In the hub:

- Category tabs along the top (pickaxe, sword, chestplate, potion). Top left, the red **Bloom+** button:
  this tab back to level 0 and its Majors refunded (in both builds), for +1 bonus Major Level per 50
  levels (up to 2 per tab); it asks twice. Top right, the white **reset Majors** button: refunds every
  Major in this tab, then that tab waits 20 minutes. That button is the only way to refund Majors;
  clashing Combat Majors can still be swapped if you can pay for the new one first.
- **Builds**: the iron and golden helmets under the page are Build 1 and Build 2. The second unlocks once
  any tab reaches 100 (it starts as a copy). Each build has its own Majors and normal stat points from the
  same pool; XP-bought points, levels and each Major's progress are shared. Switching waits 2 minutes.
- **Drag** the page (or scroll) to look around. Combat is a tree: Master
  of Arms in the middle, each weapon around it with its Majors, the universal Majors along the bottom;
  lines turn red when a Major on them is on.
- The stats window (top left; the arrow folds it away): **+** to spend points (shift = 10); **−** to
  take normal points back shows up once the category is level 100 (and goes again after Bloom+), and
  is always there for XP-bought points; **shift-click Buy point** for up to 10 at once; **click a stat's icon** to switch it off or on without losing points (corner dot: green
  on, red off).
- Majors: click to buy, click an owned one to switch it on/off. Glowing = on, red dot = owned but
  off, dark = not owned. Hover for a summary; **right-click an owned Major** for its details window
  (what it does, its numbers, how to use it, its catches, and the combos you own).
- The **Stats** button (bar chart, top right, left of the white button) opens this tab's stat sheet,
  worked out by the server: Combat for any weapon in your inventory (pick it in the row at the top)
  against a target with no/iron/diamond/netherite armor, with a switch per stacking effect you own to
  show it at its max, and the vanilla sword your hit compares to; Armor shows what reaches your health
  from each kind of hit; Utility your tools; Misc the rest. **All stats** lists where every number comes from.
  Each effect switch goes **Live** (what you have right now) → **Max** → **Off**; **All** sets them together.
  Coloured "+x" numbers are extra damage against undead (green), spiders (purple) or water mobs (aqua). The
  sheet remembers your picks per tab, and **Open Stat Sheet** (Controls, unbound) opens it directly.
- After level 100, **Mode: XP** switches the stat rows (green) to XP Investment points; there the
  icon switches only a stat's invested points. The Armor tab shows your real armor total.
- **Spend XP** levels the category with vanilla XP (every category also levels from playing now).
- Vein Mining and Timber: hold **sneak** while breaking to skip them for that block.
- Copycat: **shift-click** an owned weapon Major in the hub to copy it; shift-click Copycat to stop.
- Combat controls (rebindable in Controls → Bloom Level-Up): the **weapon-skill key**, **R** by default.
  With a mace and Wind Impact it bursts wind where you aim (or under you); with a trident and Lightning
  Summon it calls lightning where you aim; otherwise **hold** to charge (Charge Attack / Heavy Hands; a
  meter under the crosshair turns gold when ready, let go to swing) and **tap** to throw a sword, axe,
  mace or spear (Catch!). Majors that would share the key on one weapon can't be owned together,
  unless you own **Master of Arms**: then a charge Major makes every press a hold. Let go of a full
  charge on a mob in reach to swing; anywhere else it's the charged skill (Charged Throw, Charged
  Burst, Storm Call). A tap is the normal skill. A mace with Catch! and Wind Impact bursts when you
  aim at a block and throws otherwise (Gale Throw).
  **Sneak while releasing** a trident to riptide (Unrestricted Riptide).
- Hoarder's Pocket: the **chest button** above the off-hand slot in your inventory, `/bloom pocket`, or
  bind **Open Hoarder's Pocket** in Controls.
- Rune Embedding: the **enchanted-book button** above that (or `/bloom runes`, or bind **Open Rune
  Embedding**). Slots 1-4 work while you wear no armor, slot 5 while your hand is empty.
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
| `/bloom bloomreset <category> confirm` | Bloom+ reset of one category |
| `/bloom build` | Switch builds |
| `/bloom admin waits on\|off` | Switch the reset and build-switch waits on or off for the server |
| `/bloom admin refund <players> <major>` | Refund a single Major (and what needs it) to fix a profile |
| `/bloom pocket` · `/bloom runes` | Open Hoarder's Pocket / Rune Embedding |
| `/bloom admin xp <players> <category> <amount>` · `/bloom admin definitions` | Ops: grant XP, show loaded data + warnings |

## For pack makers

- Categories: `data/<ns>/bloom/category/<name>.json`
- Majors: `data/<ns>/bloom/major/<path>.json` (cost, prerequisites, conflicts, toggleable, icon, flags)
- Utility block XP: `data/<ns>/bloom/xp/blocks/<name>.json` — `{"category": "bloomlevelup:utility", "values": {"#c:ores": 3.0, "minecraft:stone": 0.5}}`
- Tags that decide what counts (all under `bloomlevelup:`): blocks `vein_mineable`, `timber_logs`,
  `timber_leaves`, `yield_eligible`, `excavation_treasure`, `growth_aura_crops`, `mature_only`, `xp_column`;
  items `armor/light|medium|heavy`, `weapon/sword|axe|bow|crossbow|trident|mace|spear|blade|blunt`,
  `food/meat`, `food/unsafe`, `food/vegetables`, `food/cleanse_excluded`, `pocket_blocked`, `rainbow_ammo`,
  `utility_tools`; enchantments `rune/body`, `rune/unarmed`; entity types `bosses`, `no_combat_xp`; damage types
  `dodgeable`, `elemental`, `melee`, `blade`, `blunt`; effects `damage_over_time`, `unyielding_ignored`
- Shovel treasure: loot table `bloomlevelup:gameplay/excavation_treasure`
- Tuning: `config/bloomlevelup.json`

## Layout

See `docs/design/` for the design documents and `docs/DECISIONS.md` for choices made during
implementation. Code: `definition/` (data model), `progression/` (rules), `player/` (state + saving),
`stats/` (formulas), `data/` (datapack loading), `command/`, `api/`, `registry/` (all game hooks).

## License

All Rights Reserved. You may include the unmodified jar in public or private modpacks, with credit to
Bloom Level-Up and FreshOrangeSlices. See [LICENSE](LICENSE) for the exact terms.

Compatibility targets: Dungeons and Taverns, World Bosses, ATi Structures, Origins: Legacy, Sodium,
Lithium and Iris. Better Combat isn't a target for now (it may interfere with Bloom's Combat mechanics).
