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
| Armor stat toggles | Off until decided (Utility stats are toggleable) | `data/.../category/armor.json` |
| Multi-rank Majors | One definition per rank with a shared `group`, each rank requires the previous | Major JSON |

## Still open (need a design call)

- Exact XP curve and per-activity XP values for each category.
- Protein! behaviour on refund / Bloom+ (exploit risk).
- Combat and Misc stat lists and Major pools (docs not received yet).
- Durability stat → virtual Unbreaking mapping; Heavy knockback value; Castle of Stone rest time (3 or 4 s).
