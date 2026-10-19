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
| Prestige total cap | **+8** (General Concept) rather than +6 (older Utility doc) | config `prestigeTotalCap` |
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
| Universal prerequisites (placeholder, design still open) | "Sword Major 1" etc. is mapped to the first Major in that tree (Batter Up! → Full Sweep, Heavy Hands → Charge Attack, Catch! → Unrestricted Riptide, Death From Above → Wind Impact) | Major JSON |
| Copycat sources | Majors flagged `weapon` are copyable unless also flagged `copycat_ineligible`; Universal Majors are flagged `universal` | Major JSON `flags` |
| One and the Same (confirmed) | Present but flagged `disabled` (cost 5 placeholder) until the Pet system exists | `major/combat/one_and_the_same.json` |
| Aura Sword | No prerequisite (docs: TBD) | Major JSON |
| Long Reach as Copycat source | Allowed for now (docs: TBD) | add `copycat_ineligible` to block |
| Category pacing | Docs want Combat slowest and Misc second-easiest; the curve is currently shared. Per-category curves arrive with the XP-source chunk | config |

## Still open (need a design call)

- Exact XP curve and per-activity XP values for each category.
- Protein! behaviour on refund / Bloom+ (exploit risk).
- Durability stat → virtual Unbreaking mapping; Heavy knockback value; Castle of Stone rest time (3 or 4 s).
- Combat numbers: Marksman range curve, Predator/Bloodlust stacks, Overkill transfer/radius, First Strike bonus, Lightning radius, Lightning Summon controls.
- Misc: Hoarder's Pocket access UX, Become My Fuel with two weapons held, default food/vegetable/rune/ammo tags.

## Hub UI requirements (from FreshOrangeSlices, for the hub chunk)

- A toggle button next to each levelable stat.
- Major icons: glowing when owned and on, dimmed when owned and off, shadowed when not owned.
