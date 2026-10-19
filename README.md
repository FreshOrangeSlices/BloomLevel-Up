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

Next: XP sources for each category (so levels come from playing), then each category's mechanics.

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
- **Spend XP** levels the category with vanilla XP (until levels come from playing).

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
| `/bloom admin xp <player> <category> <amount>` · `/bloom admin definitions` | Ops: grant XP, show loaded data + warnings |

## For pack makers

- Categories: `data/<ns>/bloom/category/<name>.json`
- Majors: `data/<ns>/bloom/major/<path>.json` (cost, prerequisites, conflicts, toggleable, icon, flags)
- Tuning: `config/bloomlevelup.json`

## Layout

See `docs/design/` for the design documents and `docs/DECISIONS.md` for choices made during
implementation. Code: `definition/` (data model), `progression/` (rules), `player/` (state + saving),
`stats/` (formulas), `data/` (datapack loading), `command/`, `api/`, `registry/` (all game hooks).
