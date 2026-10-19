# Implementation decisions & open questions

Running log of choices made while building, so the design docs in `design/` stay the source of truth
and anything the code decided on its own is visible here. Each item can be changed.

## Chunk 1 — foundation

| Topic | Decision | Where to change |
|---|---|---|
| Target | Minecraft 26.3, Fabric Loader 0.19.5, Fabric API 0.163.0+26.3, Loom 1.18, Java 25 (from the official Fabric template) | `gradle.properties` |
| Mod id / package | `bloomlevelup` / `com.freshorangeslices.bloomlevelup` | — |
| Starting level | Categories start at level 0; natural stat points = level (so 100 at the cap) | `ProgressionService` |
| XP curve (TBD in docs) | XP for L→L+1 = 50 + 10·L (≈54k XP from 0 to 100) | `config/bloomlevelup.json` |
| Vanilla XP funding rate (TBD) | 1 skill XP per raw vanilla XP point; never spends more than needed to reach the cap | config `vanillaXpToSkillXp` |
| Post-100 investment price (TBD) | n-th point costs 100 + 10·n raw XP | config `investmentBaseCost`, `investmentCostGrowth` |
| Prestige total cap (confirmed) | **+6** lifetime, max +2 per category | config `prestigeTotalCap` |
| Natural stat respec | Allowed (points can be pulled back out) | config `allowStatRefund` |
| Refunding a prerequisite | Blocked while an owned Major depends on it; refund the dependent first | `ProgressionService.refundMajor` |
| Major Level accounting | Not stored as a balance: earned = milestones + prestige, spent = what was paid for owned Majors. Refunds return exactly what was paid | `BloomProfile` |
| Removed Majors | If a datapack removes a Major, owners keep it (dormant, inactive) so nothing is lost | `ProgressionService.isMajorActive` |
| Armor-overcap safeguard (TBD) | Overcap reduction layer capped at 90% | config `armorOvercapReductionCap` |
| Toggles (confirmed by FreshOrangeSlices) | Every stat and every Major can be toggled. A toggled-off stat is fully off (0), never partially reduced | stat/Major JSON `toggleable` (defaults to true) |
| Weapon trees (confirmed) | No ranks: each weapon tree is a group of Majors, each costs 1 and can be bought in any order. Overrides the "multiple ranks" wording in GENERAL_CONCEPT/COMBAT docs | Major JSON `group` |

## Combat & Misc data

| Topic | Decision | Where to change |
|---|---|---|
| Universal prerequisites (confirmed) | Owning any one Major in the related tree unlocks it: Batter Up! ← Sword, Heavy Hands ← Axe, Catch! ← Trident, Death From Above ← Mace. Written as `"#combat/sword"` in `prerequisites`. Refunding a tree Major is allowed while another one in that tree is still owned | Major JSON |
| Copycat sources | Majors flagged `weapon` are copyable unless also flagged `copycat_ineligible`; Universal Majors are flagged `universal` | Major JSON `flags` |
| One and the Same (confirmed) | Present but flagged `disabled` (cost 5 placeholder) until the Pet system exists | `major/combat/one_and_the_same.json` |
| Aura Sword | No prerequisite (docs: TBD) | Major JSON |
| Long Reach as Copycat source | Allowed for now (docs: TBD) | add `copycat_ineligible` to block |
| Category pacing | Shared curve for now; per-category curves tuned to the pacing answers below arrive with the XP-source chunk | config |

## Answers from FreshOrangeSlices (2026-10-18)

| Topic | Answer |
|---|---|
| Opening the hub | Visible button in the inventory screen. Optional keybind in Controls, unbound by default |
| Pacing to 100 | About 2 weeks of normal play per category. Combat is fastest (about 5 days with a mob farm; farming is never punished) even with slightly higher XP requirements. Misc about 1 week of XP farming. Utility and Armor slowest in practice |
| Vanilla XP funding | Charged in raw XP (UI may show the vanilla-level equivalent). Around skill level 80–90, one level costs about 15–20 vanilla levels' worth of XP (about 315–550 XP counted from level 0) |
| XP Investment price | Restarts cheap at the first post-100 point and climbs the same way, reaching about 15–20 vanilla levels' worth per point around the 80th–90th point |
| Combat kill bonus | 50% of the XP the kill took (iron sword kills a zombie in 3 hits → the kill adds half of those 3 hits' XP) |
| Utility XP | Like mcMMO: per-block values, ores worth more, farming, fishing and shearing included |
| Protein! on refund / Bloom+ | Resets (bonus hearts and meal count) |
| Controls | Catch! and Heavy Hands default to left click, rebindable in Controls. Ascension flies on double-jump |
| Hoarder's Pocket | Opened from a tab or button styled like opening a chest |
| Become My Fuel | Main hand only (the other hand holds the potion being drunk). Only drinking imprints; splash, lingering and other sources don't |
| Combat costs | Marksman 1 (Bow tree), Predator 2, Bloodlust 2, Overkill 3, First Strike 2 |
| Castle of Stone | 4 seconds resting in a bed |

## Still open (need a design call)

- Heavy Hands and Catch! both default to left click: how they are told apart.
- Combat effect sizes: Marksman, Predator, Bloodlust, Overkill, First Strike (I'll propose numbers).
- Heavy armor knockback strength; Aura Sword prerequisite; Long Reach as a Copycat source.
- License; the other mods in the modpack.
- Durability stat → virtual Unbreaking mapping; Trident Lightning radius; Lightning Summon control.
- Default food / vegetable / rune / ammo tags.

## Hub UI requirements (from FreshOrangeSlices, for the hub chunk)

- A button in the inventory screen opens the hub; optional unbound keybind.
- A toggle button next to each levelable stat.
- Major icons: glowing when owned and on, dimmed when owned and off, shadowed when not owned.
- Icon art is 64×64 PNG (confirmed). The hub draws each icon in a 32×32 GUI-pixel slot, so it is pixel-exact at GUI scale 2 and downscaled cleanly at scale 1; use nearest-neighbour filtering.
