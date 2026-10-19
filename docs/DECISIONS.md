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
| Aura Sword (confirmed) | Requires any one Sword Major (`#combat/sword`) | Major JSON |
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
| Controls | Heavy Hands: hold attack (left click) to charge. Catch!: right click to throw. Both rebindable in Controls. Ascension flies on double-jump |
| Hoarder's Pocket | Opened from a tab or button styled like opening a chest |
| Become My Fuel | Main hand only (the other hand holds the potion being drunk). Only drinking imprints; splash, lingering and other sources don't |
| Combat costs | Marksman 1 (Bow tree), Predator 2, Bloodlust 2, Overkill 3, First Strike 2 |
| Castle of Stone | 4 seconds resting in a bed |
| Heavy armor knockback | 50% knockback resistance, with any further sources scaling with diminishing returns |
| Placeholder art | One 64×64 placeholder (`textures/gui/placeholder.png`, from FreshOrangeSlices) stands in for every missing icon |

## Chunk 2 — hub and first effects

| Topic | Decision | Where to change |
|---|---|---|
| Effect delivery | Stats become vanilla attribute modifiers, saved with the player so extra hearts survive relogging. Removing the mod leaves them on old saves | `effect/StatEffects` |
| Power / Tempo / Speed | Power +0.06 damage per point; Tempo and Speed +1% per point of the base value (2x at 100) | `effect/StatEffects` |
| Titan Hide | Armor slots all empty → (Defense armor + 2) × 2, +50% move speed, −70% fall damage, 2x knockback taken | `effect/StatEffects`, `LivingEntityMixin` |
| Hub layout | Stats left, Majors right as a grid in data `sort` order; trees shown in each tooltip | `client/gui/HubScreen` |
| Major controls | Click: buy / switch on-off. Right-click: refund. Conflicts open a swap prompt | `client/gui/HubScreen` |

## Chunk 3 — Armor

| Topic | Decision | Where to change |
|---|---|---|
| Armor XP | 4 XP per point of incoming damage (half a heart), counting at most the player's max health per hit. Blocked hits 1.5x, dodges 2x, Reflect/Parkour saves 1x. Creative/spectator earn nothing | config `armorXpPerDamage`, `armorBlockXpMultiplier`, `armorDodgeXpMultiplier` |
| Damage cooldown | Hits inside vanilla's damage cooldown don't roll Dodge/Reflect and give no XP (stops multi-hit XP farming from one source) | `ArmorSkill.allowDamage` |
| Reduction layers | Every % reduction multiplies the damage left after vanilla armor/Resistance/enchantments (never adds), so stacking can't reach immunity. Order: Fitting Armor, Iron Skin, Brace, Elemental Skin, Vibranium/Adamantium, armor overcap, then Absorb | `ArmorSkill.modifyFinalDamage` |
| Dodge | Melee and projectile damage only (tag `bloomlevelup:dodgeable`), never while blocking with a shield. Hard cap 90% | tag + config `dodgeChanceCap` |
| Durability | 100 points ≈ a second Unbreaking III: armor 30% backup save, shields 75% (after vanilla Unbreaking fails) | `StatFormulas` |
| Fitting Armor | Each worn piece is Light (1), Medium (2) or Heavy (3) by item tag; the average decides: under 1.5 Light, under 2.5 Medium, else Heavy. Any empty slot = no style. Light: 10% backup dodge + Feather Falling I. Medium: −15% damage. Heavy: −30% damage and 50% knockback resistance, tapering if you already resist knockback | tags `bloomlevelup:armor/{light,medium,heavy}` |
| Medium style shield disable (−50%) | Not in yet: needs a hook into shield cooldowns | later chunk |
| Iron Skin / Brace! | −20% (like Resistance I) / −40% while braced (like Resistance II). Brace: crouch without moving or jumping for 1 second | config `braceDelayTicks` |
| Elemental Skin | −50% fire (incl. lava), freezing, lightning and drowning damage (tag `bloomlevelup:elemental`) | tag |
| Vibranium / Adamantium | −30% from blades / −40% from blunt weapons, by damage type or the attacker's held item tag | tags `bloomlevelup:weapon/{blade,blunt}` |
| Thick Blood | Poison and Wither last half as long (tag `bloomlevelup:damage_over_time`) | tag |
| Unyielding | 50% to shrug off each harmful effect; never blocks effects from food you chose to eat | tag `bloomlevelup:unyielding_ignored` |
| Reflect | Projectiles: 10% caught (you get the arrow, enchantments kept), else 25% deflected | `ArmorSkill` |
| Rebound | 15% of melee hits deal 50% of the hit back to the attacker (as thorns damage) | `ArmorSkill` |
| Parkour | 35% chance to take no fall damage | `ArmorSkill` |
| Last Breath | Survive a killing blow at 5 hearts. Chance halves with each save inside a 20-minute window (100%, 50%, 25%...), and resets after the window or on death | config `lastBreathWindowTicks`, `lastBreathHealth` |
| Castle of Stone | When Last Breath saves you: 10 s of invincibility (void and /kill still work). Recharges after 4 s lying in a bed | config `castleOfStoneTicks`, `castleOfStoneRestTicks` |
| Absorb | Half of the final damage goes into one random worn armor piece's durability (1:1, rounded down); the piece can break | `ArmorSkill.absorb` |
| Protein! | +½ heart per 20 meat items eaten after buying it, up to +10 hearts. Meat = tag `bloomlevelup:food/meat` (vanilla raw/cooked meats + `c:foods/*_meat`; rotten flesh doesn't count) | tag |
| Titan Hide knockback | 2x knockback taken while its bonus is active | `LivingEntityMixin` |

## Chunk 4 — Utility

| Topic | Decision | Where to change |
|---|---|---|
| Block XP | Data files `data/<ns>/bloom/xp/blocks/*.json` map block ids or `#tags` to XP. Exact id beats tags; otherwise the highest matching tag wins. Values are about mcMMO's divided by 60: stone 0.5, logs 1.25, dirt 0.6, coal 2, iron 4, gold/lapis 6, diamond 12, emerald 16, ancient debris 20, ripe crops 0.8 | data |
| Fishing / shearing XP | 8 per catch, 2.5 per shear | config `utilityFishingXp`, `utilityShearXp` |
| XP rules | Like mcMMO: placed blocks give nothing; the tool must be one that harvests the block (no XP for punching stone); crops only when fully grown; sugar cane/cactus/bamboo count every natural segment that pops off above the one you broke | tags `mature_only`, `xp_column` |
| Placed-block memory | Only XP/Yield/treasure/log blocks placed from an item by a player are remembered, saved per chunk. Planted crops are never "placed" (they had to grow). Known gap: pushing a placed block with a piston loses the mark | `PlacedBlocks` |
| Efficiency | +1% block break speed per point, as an attribute (works with any tool, and the client's mining animation matches) | `StatEffects` |
| Yield | Copies every stack of the final drop, after Fortune. Eligible blocks: ores, logs, crops and produce, mushrooms, glowstone, amethyst clusters, clay (tag `bloomlevelup:yield_eligible`). Never on placed blocks. Also applies to shearing drops | tag |
| Shovel Mastery | With a shovel in the main hand: Efficiency, Yield and Preservation count +20%. Natural dirt/sand/gravel/clay/soul blocks dug with a shovel can turn up treasure: 4% per block per 100 Yield (so ~4.8% at 100 Yield). Dirt itself is never multiplied. Treasure comes from loot table `bloomlevelup:gameplay/excavation_treasure` | config `shovelMasteryBonus`, `excavationTreasureChancePer100Yield` |
| Vein Mining | Pickaxe on an ore (tag `vein_mineable`): breaks up to 64 connected blocks of the same type, diagonals included. Each block costs normal durability and gives normal XP/drops/Yield. Stops with 1 durability left so the tool never breaks | config `veinMiningMaxBlocks` |
| Timber | Axe on a natural log: fells up to 256 connected natural logs at or above the cut, only if they touch natural (non-player) leaves. Player-placed logs and log buildings are left alone | config `timberMaxLogs` |
| Skipping a chain | Hold sneak while breaking to skip Vein Mining / Timber for that block (no need to open the hub) | config `chainBreakSneakBypass` |
| Growth Aura | While a hoe is in either hand: every second, each crop within 4 blocks (2 up/down) has a 15% chance of an extra growth tick (about 10x faster with good light/farmland). No durability use. Tag `growth_aura_crops` | config `growthAura*` |
| Master Shearing | After each successful shear, 50% that the sheep/bogged/snow golem is shearable again, repeatable. Each shear costs 1 extra durability. Modded shearables get the XP and Yield but not the coin flip | config `masterShearingChance` |
| Triple Catch | Each catch rolls the whole fishing loot table 3 times; +2 rod durability per catch (3x total). XP counts once per catch | config `tripleCatchRolls`, `tripleCatchExtraDurability` |

## Chunk 5 — Combat

| Topic | Decision | Where to change |
|---|---|---|
| Combat XP | 0.5 XP per point of real health damage you deal (after the target's armor), never more than the health it had left. The killing blow adds 50% of the XP that kill took. Dying later to fire, falls or someone else gives no kill bonus. Thorns damage gives none | config `combatXpPerDamage`, `combatKillBonus` |
| Mob multipliers | Wither 10x, Ender Dragon 12x, Warden 5x, Elder Guardian 5x; any entity id can be added | config `combatEntityXpMultipliers` |
| No XP from | Armor stands, your own pets; players only if `combatPvpXp` | tag `bloomlevelup:no_combat_xp` |
| PvP | Combat stats and Majors work on players (design default) | config `combatPvp` |
| Weapon families | Item tags `bloomlevelup:weapon/{sword,axe,bow,crossbow,trident,mace,spear}`; an empty hand is Unarmed. An item can be in several | tags |
| Bosses (Execute) | Tag `bloomlevelup:bosses`: Wither, Ender Dragon, Warden, Elder Guardian, plus `#c:bosses` | tag |
| Critical | A real vanilla crit deals 1.5x normally, 4x at 100 points (linear, keeps growing past 100) | `StatFormulas.criticalMultiplier` |
| Penetration | Ignores p/(p+100) of the target's armor: 50% at 100, never 100% | `StatFormulas.penetration` |
| Looting | Adds virtual Looting levels: 3 at 100 points (fractions rolled), so it works with every loot table that uses Looting | `StatFormulas.virtualLooting` |
| Order of bonuses | Bonuses are real damage added before the target's armor: flat adds (Stored Impact, Death From Above, Combo) first, then multipliers (crit, Marksman, First Strike, Predator, Bloodlust) | `CombatSkill.applyBonuses` |
| Full Sweep | Sweeping Damage ratio set to its maximum: sweep = 1 + 100% of the hit | `StatEffects` |
| Execute | Sword hits finish targets left at 30% health or less (bosses 10%). It sets health to zero and lets vanilla's death run (kill credit, loot, XP), so it adds no fake damage | config `executeThreshold`, `executeBossThreshold` |
| Armor Break | Each worn armor piece of the target loses durability equal to half the real damage (rounded up) | config `armorBreakRatio` |
| Guaranteed Critical | Full-strength axe swings always crit (with crit particles and sound); −2 attack damage while an axe is in hand | config `guaranteedCriticalPenalty` |
| Marksman (proposed numbers) | Fully drawn bow shots: no bonus up to 10 blocks, then rising to +50% at 40 blocks | config `marksman*` |
| Piercing Shot | Bow arrows pierce 4 enemies (like Piercing IV) | config `piercingShotLevel` |
| Straight Shot | Bow arrows fly without gravity and vanish after 5 seconds in the air | config `straightShotMaxTicks` |
| Reinforced Shot | +3 damage on crossbow arrows | config `reinforcedShotDamage` |
| Unrestricted Riptide (proposed control) | Hold sneak while releasing a trident to riptide with no enchantment, anywhere; release normally to throw. Riptide-enchanted tridents work without water or rain | `CombatSkill.riptideStrength` |
| Lightning Shock (proposed radius) | Enemies within 6 blocks of the struck target. Needs 2+ enemies; 3 shocks at 50% of the hit; one chain per 2 s; ignores the target's hurt cooldown; never triggers Thorns | config `lightningShock*` |
| Wind Impact | Landing a fall of 1.5+ blocks with a mace in hand bursts wind outward (3.5 blocks), pushing mobs away; it doesn't launch you | config `windImpact*` |
| Stored Impact | Real fall damage stores 50% (max +20) for the next mace hit; 5-minute cooldown from the fall | config `storedImpact*` |
| Brawler / Combo | +6 attack damage bare-handed. Combo: +0.25 per clean bare-handed hit, max +5, reset after 3 s without a hit | config |
| Disarm | 30% on primary bare-handed hits: mobs drop their held item, players drop their selected stack | config `disarmChance` |
| Long Reach | +2 blocks attack reach while holding a spear | config `longReachBlocks` |
| Impale / Bleed | Full-strength spear hits and spear charges: 40% to Bleed for 3 s (half a heart per second, ignores armor, Resistance still applies, refreshes instead of stacking). Bleed is a real status effect | config `impaleChance`, `bleedTicks` |
| Batter Up! | Non-sword melee weapons and fists sweep under vanilla's sweep rules (full strength, on the ground, not a crit or sprint hit) | `CombatSkill.sweep` |
| Aura Sword | Every 5th full-strength melee hit releases a 3.5-block circle sweep that pulses twice (0.5 s apart), at your attack damage | config `auraSword*` |
| Death From Above | Every melee weapon except the mace gains the mace's fall bonus (+4/block for 3 blocks, then +2/block for 5, then +1/block). No fall-damage protection | `StatFormulas.maceFallBonus` |
| Predator (proposed numbers) | Each consecutive hit on the same target +10%, up to +50%; switching targets or 5 s idle resets | config `predator*` |
| Bloodlust (proposed numbers) | Each kill +8% damage for 10 s, up to 5 stacks; kills refresh it | config `bloodlust*` |
| Overkill (proposed numbers) | 60% of a kill's excess real damage, split across up to 3 enemies within 4 blocks. Not from Execute kills or from Overkill's own kills | config `overkill*` |
| First Strike (proposed number) | +50% on the first hit against a full-health target (each pierced target counts separately) | config `firstStrikeBonus` |
| Crossbow Infinity | Plain arrows loaded into a crossbow aren't used up (and can't be picked back up). Tipped/spectral arrows and fireworks still are | `CombatSkill.freeAmmo` |
| Charge Attack / Heavy Hands controls | Hold attack (left click) on a mob or the air. Once the swing has recovered, 1 more second of holding charges it (a heavy whoosh, then sparks); release to swing for 2x (it can still crit). A quick tap still attacks, on release. Mining is unchanged. "Charge attack" can be moved to another key in Controls | config `chargeAttackTicks`, `chargeAttackMultiplier` |
| Lightning Summon (proposed control) | Sneak + left click with a trident while not aiming at a mob: lightning where you look (up to 48 blocks). Costs 10% of the trident's max durability and refuses if that would break it; 1 s cooldown | config `lightningSummon*` |
| Catch! | Right click (rebindable "Throw weapon") with a sword, axe or mace throws it. It always comes back (like Loyalty III), hits for the weapon's own damage plus enchantments, and costs 1 durability. Thrown maces scale with the drop and set off Wind Impact on landing; other thrown weapons scale with the drop with Death From Above. Thrown weapons render as a flat item for now | config `catchThrowSpeed` |
| Assault Rifle | Loading fills a 16-round magazine from your arrows (or fireworks); each click fires one round and holding right click keeps firing (about 5 shots a second); it reloads normally once empty | config `assaultRifleMagazine` |
| Copycat | In the hub, shift-click an owned weapon Major to copy it; shift-click Copycat to stop. Copied effects keep their costs and drawbacks and switch off with their source. Long Reach is allowed as a source for now | `ProgressionService.selectCopySource` |

## Chunk 6 — Misc

| Topic | Decision | Where to change |
|---|---|---|
| Misc XP (docs: TBD curve) | 0.25 Misc XP per point of vanilla XP in each collected orb, counted before Wisdom and Mending. With the shared curve (~54k XP to 100) that is ~218k orb XP: about a week of an hour or two a day at a good XP farm | config `miscXpPerOrbPoint` |
| Wisdom | Orb XP x(1 + 4% per point): 3x at 50, 5x at 100. Applies to the XP left after Mending; fractions are rolled | `StatFormulas.wisdomMultiplier` |
| Endurance | Food exhaustion x 1/(1 + 0.5 x points/100): saturation and hunger last 1.5x as long at 100 | `StatFormulas.enduranceExhaustionFactor` |
| Iron Stomach | Unsafe food = tag `bloomlevelup:food/unsafe` (rotten flesh, spider eye, pufferfish, poisonous potato, raw meat and fish, plus `c:foods/raw_meat`, `raw_fish`, `food_poisoning`). Its harmful effects never land, and it fills at least 6 hunger / 7.2 saturation (cooked chicken) | tag + config `ironStomach*` |
| Selective Cleansing | Milk (anything that "clears all effects" when consumed) clears only harmful effects. Vegetables = tag `food/vegetables` (carrots, golden carrots, potatoes, baked potatoes, beetroot, beetroot soup, `c:foods/vegetable`), minus `food/cleanse_excluded` (apples, berries) | tags |
| XP Insurance | Vanilla drops min(7 x level, 100) XP as orbs and destroys the rest; half of the destroyed part comes back on respawn (level 30: 647 of 1295). Nothing happens with keepInventory | config `xpInsuranceShare` |
| Water to Wine | New timed effects (buffs and debuffs) last at least 10 minutes; instant and infinite effects untouched. The effects it stretched are remembered on the player (saved), and switching it off, refunding it or Bloom+ cuts only those to 2 minutes left | config `waterToWine*` |
| Overdose | Re-applying an effect you already have at the same level or higher climbs one level, up to 2 above the incoming effect (Speed I → II → III). Beacon and conduit (ambient) effects never climb, or a beacon would keep you at +2 forever | config `overdoseMaxBoost` |
| Hoarder's Pocket | 9 slots saved on the player (kept through death, travel, refunds and Bloom+). Opens as a one-row chest from a chest button under the hub button, `/bloom pocket`, or an unbound key. Without the Major it still opens if anything is inside, take-out only, so nothing is stranded. Blocked: anything with container or bundle contents, plus tag `pocket_blocked` | `HoardersPocket` |
| Rainbow | Bows and crossbows don't use up anything in tag `rainbow_ammo` (all arrows incl. tipped/spectral, and fireworks); you still need one in your inventory. Requires Crossbow Infinity | tag |
| Become My Fuel (confirmed: main hand, drinking only) | Drinking a potion from the off hand imprints its first harmful non-instant effect (at the potion's level) on the main-hand weapon (any weapon family). Overwrites the old one. Every weapon hit (melee, arrows and thrown weapons from it) applies it for 10 s, whoever holds the weapon. Shown in the tooltip | config `fuelEffectTicks` |
| Overenchant | In the anvil, enchantment caps rise to 8 (modded caps above 8 stay) and "Too Expensive!" never blocks; you still pay the levels | config `overenchantMaxLevel` |
| Ascension (confirmed: double jump) | Creative-style flight in survival/adventure. Damage while flying takes flight away until you touch ground or water. Side effect of vanilla flight: no fall damage while you can fly | `MiscSkill.updateFlight` |
| Stack Overflow / Rune Embedding | Not built yet; flagged `disabled` so they're hidden and can't be bought. Stack Overflow needs every stack codec, container and packet to accept 300; Rune Embedding needs its own slot UI and an enchantment-effect bridge | Major JSON `flags` |

## Still open (need a design call)

- Combat numbers marked "proposed" above: Marksman, Predator, Bloodlust, Overkill, First Strike, Lightning radius, Unrestricted Riptide's sneak control.
- Long Reach as a Copycat source.
- License; the other mods in the modpack.
- Default rune tags (Rune Embedding). Vegetable, unsafe-food and ammo tags have first-pass defaults above.
- Misc XP rate (0.25 per orb point) and whether Ascension's flight should also cancel fall damage.
- Medium armor's shield-disable reduction (needs a shield cooldown hook).

## Hub UI requirements (from FreshOrangeSlices, for the hub chunk)

- A button in the inventory screen opens the hub; optional unbound keybind.
- A toggle button next to each levelable stat.
- Major icons: glowing when owned and on, dimmed when owned and off, shadowed when not owned.
- Icon art is 64×64 PNG (confirmed). The hub draws each icon in a 32×32 GUI-pixel slot, so it is pixel-exact at GUI scale 2 and downscaled cleanly at scale 1; use nearest-neighbour filtering.
