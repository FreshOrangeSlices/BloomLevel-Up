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
| Long Reach as Copycat source (confirmed) | Allowed | add `copycat_ineligible` to block |
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

## Answers from FreshOrangeSlices (2026-10-19)

| Topic | Answer |
|---|---|
| Combat numbers | Keep Marksman +50%, Predator +10%/hit to +50%, Bloodlust +8%/kill to 5 stacks, Overkill 60% to 3 mobs within 4 blocks, First Strike +50% until playtested |
| Combat controls | Keep Lightning Summon (sneak + left click with a trident) and Unrestricted Riptide (sneak while releasing) for now |
| Lightning Shock | Keep: 6 blocks, 3 shocks at 50%, once per 2 s |
| Copycat + Long Reach | Allowed |
| License | All Rights Reserved; the unmodified jar may be included in public or private modpacks with attribution (see `LICENSE`) |
| Compatibility targets | Dungeons and Taverns, World Bosses, ATi Structures, Origins: Legacy, plus Sodium, Lithium, Iris and similar visual/performance mods. Better Combat is not a target (it may interfere with Bloom's Combat) |
| Misc XP rate | Keep 1 Misc XP per 4 orb XP until playtested |
| Ascension and falling | No fall damage while actually flying. Knocked out of flight by damage, the fall hurts normally; landing gives flight back |
| Water to Wine | Fully symmetrical: enemy Poison, Wither, Bleed... can last 10 minutes too (intended danger) |
| Overdose | Beacon and conduit effects stay excluded |
| Hoarder's Pocket after refund / Bloom+ | Take-out only while anything is inside; once empty, closed until the Major is bought again |
| Become My Fuel | Fuelled bows and crossbows apply it through their projectiles; a fuelled trident when thrown |
| Food lists | Keep as they are (apples and berries excluded from cleansing) |
| Rune Embedding | Keep the body and bare-hand lists; multi-enchantment books count every valid enchantment; no duplicates; runes persist through death; Frost Walker / Soul Speed later |
| Stack Overflow | Own inventory and Hoarder's Pocket only, never a server-wide rule; everything else keeps normal limits and splits big stacks; switching off splits safely |
| One and the Same / Pets | Pets are planned as their own subsystem in Bloom Level-Up (not a skill tab); One and the Same stays hidden until then |
| Catch! look | Flat spinning item for now; a 3D projectile is later polish |

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
| Iron Skin / Brace! | −20% (like Resistance I) / −40% while braced (like Resistance II). Brace: crouch still or raise a shield, on the ground; braced at once (round 6, was 1 second) | config `braceDelayTicks` |
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
| Shovel Mastery (see Playtest round 1) | With a shovel in the main hand: Efficiency, Yield and Preservation count +20%. Natural dirt/sand/gravel/clay/soul blocks dug with a shovel can turn up treasure: 4% per block per 100 Yield (so ~4.8% at 100 Yield). Dirt itself is never multiplied. Treasure comes from loot table `bloomlevelup:gameplay/excavation_treasure` | config `shovelMasteryBonus`, `excavationTreasureChancePer100Yield` |
| Vein Mining | Pickaxe on an ore (tag `vein_mineable`): breaks up to 64 connected blocks of the same type, diagonals included. Each block costs normal durability and gives normal XP/drops/Yield. Stops with 1 durability left so the tool never breaks | config `veinMiningMaxBlocks` |
| Timber | Axe on a natural log: fells up to 256 connected natural logs at or above the cut, only if they touch natural (non-player) leaves. Player-placed logs and log buildings are left alone | config `timberMaxLogs` |
| Skipping a chain | Hold sneak while breaking to skip Vein Mining / Timber for that block (no need to open the hub) | config `chainBreakSneakBypass` |
| Growth Aura (see Playtest round 1) | While a hoe is in either hand: every second, each crop within 4 blocks (2 up/down) has a 15% chance of an extra growth tick (about 10x faster with good light/farmland). No durability use. Tag `growth_aura_crops` | config `growthAura*` |
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
| Marksman (confirmed for playtesting) | Fully drawn bow shots: no bonus up to 10 blocks, then rising to +50% at 40 blocks | config `marksman*` |
| Piercing Shot | Bow arrows pierce 4 enemies (like Piercing IV) | config `piercingShotLevel` |
| Straight Shot | Bow arrows fly without gravity and vanish after 5 seconds in the air | config `straightShotMaxTicks` |
| Reinforced Shot | +3 damage on crossbow arrows | config `reinforcedShotDamage` |
| Unrestricted Riptide (confirmed control) | Hold sneak while releasing a trident to riptide with no enchantment, anywhere; release normally to throw. Riptide-enchanted tridents work without water or rain | `CombatSkill.riptideStrength` |
| Lightning Shock (confirmed) | Enemies within 6 blocks of the struck target. Needs 2+ enemies; 3 shocks at 50% of the hit; one chain per 2 s; ignores the target's hurt cooldown; never triggers Thorns | config `lightningShock*` |
| Wind Impact (see Playtest round 1) | Landing a fall of 1.5+ blocks with a mace in hand bursts wind outward (3.5 blocks), pushing mobs away; it doesn't launch you | config `windImpact*` |
| Stored Impact (see Playtest round 1) | Real fall damage stores 50% (max +20) for the next mace hit; 5-minute cooldown from the fall | config `storedImpact*` |
| Brawler / Combo | +6 attack damage bare-handed. Combo: +0.25 per clean bare-handed hit, max +5, reset after 3 s without a hit | config |
| Disarm | 30% on primary bare-handed hits: mobs drop their held item, players drop their selected stack | config `disarmChance` |
| Long Reach | +2 blocks attack reach while holding a spear | config `longReachBlocks` |
| Impale / Bleed (see Playtest round 1) | Full-strength spear hits and spear charges: 40% to Bleed for 3 s (half a heart per second, ignores armor, Resistance still applies, refreshes instead of stacking). Bleed is a real status effect | config `impaleChance`, `bleedTicks` |
| Batter Up! | Non-sword melee weapons and fists sweep under vanilla's sweep rules (full strength, on the ground, not a crit or sprint hit) | `CombatSkill.sweep` |
| Aura Sword (see Playtest round 1) | Every 5th full-strength melee hit releases a 3.5-block circle sweep that pulses twice (0.5 s apart), at your attack damage | config `auraSword*` |
| Death From Above | Every melee weapon except the mace gains the mace's fall bonus (+4/block for 3 blocks, then +2/block for 5, then +1/block). No fall-damage protection | `StatFormulas.maceFallBonus` |
| Predator (confirmed for playtesting) | Each consecutive hit on the same target +10%, up to +50%; switching targets or 5 s idle resets | config `predator*` |
| Bloodlust (confirmed for playtesting) | Each kill +8% damage for 10 s, up to 5 stacks; kills refresh it | config `bloodlust*` |
| Overkill (confirmed for playtesting) | 60% of a kill's excess real damage, split across up to 3 enemies within 4 blocks. Not from Execute kills or from Overkill's own kills | config `overkill*` |
| First Strike (confirmed for playtesting) | +50% on the first hit against a full-health target (each pierced target counts separately) | config `firstStrikeBonus` |
| Crossbow Infinity | Plain arrows loaded into a crossbow aren't used up (and can't be picked back up). Tipped/spectral arrows and fireworks still are | `CombatSkill.freeAmmo` |
| Charge Attack / Heavy Hands controls (see Playtest round 1) | Hold attack (left click) on a mob or the air. Once the swing has recovered, 1 more second of holding charges it (a heavy whoosh, then sparks); release to swing for 2x (it can still crit). A quick tap still attacks, on release. Mining is unchanged. "Charge attack" can be moved to another key in Controls | config `chargeAttackTicks`, `chargeAttackMultiplier` |
| Lightning Summon (confirmed control) | Sneak + left click with a trident while not aiming at a mob: lightning where you look (up to 48 blocks). Costs 10% of the trident's max durability and refuses if that would break it; 1 s cooldown | config `lightningSummon*` |
| Catch! | Right click (rebindable "Throw weapon") with a sword, axe or mace throws it. It always comes back (like Loyalty III), hits for the weapon's own damage plus enchantments, and costs 1 durability. Thrown maces scale with the drop and set off Wind Impact on landing; other thrown weapons scale with the drop with Death From Above. Thrown weapons render as a flat item for now | config `catchThrowSpeed` |
| Assault Rifle | Loading fills a 16-round magazine from your arrows (or fireworks); each click fires one round and holding right click keeps firing (about 5 shots a second); it reloads normally once empty | config `assaultRifleMagazine` |
| Copycat | In the hub, shift-click an owned weapon Major to copy it; shift-click Copycat to stop. Copied effects keep their costs and drawbacks and switch off with their source. Long Reach is allowed as a source (confirmed) | `ProgressionService.selectCopySource` |

## Chunk 6 — Misc

| Topic | Decision | Where to change |
|---|---|---|
| Misc XP (docs: TBD curve) | 0.25 Misc XP per point of vanilla XP in each collected orb, counted before Wisdom and Mending. With the shared curve (~54k XP to 100) that is ~218k orb XP: about a week of an hour or two a day at a good XP farm | config `miscXpPerOrbPoint` |
| Wisdom | Orb XP x(1 + 4% per point): 3x at 50, 5x at 100. Applies to the XP left after Mending; fractions are rolled | `StatFormulas.wisdomMultiplier` |
| Endurance | Food exhaustion x 1/(1 + 0.5 x points/100): saturation and hunger last 1.5x as long at 100 | `StatFormulas.enduranceExhaustionFactor` |
| Iron Stomach | Unsafe food = tag `bloomlevelup:food/unsafe` (rotten flesh, spider eye, pufferfish, poisonous potato, raw meat and fish, plus `c:foods/raw_meat`, `raw_fish`, `food_poisoning`). Its harmful effects never land, and it fills at least 6 hunger / 7.2 saturation (cooked chicken) | tag + config `ironStomach*` |
| Selective Cleansing | Milk (anything that "clears all effects" when consumed) clears only harmful effects. Vegetables = tag `food/vegetables` (carrots, golden carrots, potatoes, baked potatoes, beetroot, beetroot soup, `c:foods/vegetable`), minus `food/cleanse_excluded` (apples, berries) | tags |
| XP Insurance | Vanilla drops min(7 x level, 100) XP as orbs and destroys the rest; half of the destroyed part comes back on respawn (level 30: 647 of 1295). Nothing happens with keepInventory | config `xpInsuranceShare` |
| Water to Wine | New timed effects (buffs and debuffs) last at least 10 minutes; instant and infinite effects untouched. An effect counts as stretched only if the stretched version actually became the active one (an ignored or blocked effect never marks anything). The marks are saved on the player, and switching it off, refunding it or Bloom+ cuts only those to 2 minutes left (weaker copies waiting underneath within 10 minutes are cut too) | config `waterToWine*` |
| Overdose | Re-applying an effect you already have at the same level or higher climbs one level, up to 2 above the incoming effect (Speed I → II → III); more Speed I then refreshes Speed III. Beacon and conduit (ambient) effects never climb, or a beacon would keep you at +2 forever | config `overdoseMaxBoost` |
| Hoarder's Pocket | 9 slots saved on the player (kept through death, travel, refunds and Bloom+). Opens as a one-row chest from a chest button under the hub button, `/bloom pocket`, or an unbound key. Without the Major it still opens (button, command or key) while anything is inside, take-out only (shift-click can't top stacks up either), so nothing is stranded; once empty it stays closed until the Major is bought again. Blocked: anything with container or bundle contents, plus tag `pocket_blocked` | `HoardersPocket` |
| Rainbow | Bows and crossbows don't use up anything in tag `rainbow_ammo` (all arrows incl. tipped/spectral, and fireworks); you still need one in your inventory. Requires Crossbow Infinity, and only works while it's switched on | tag |
| Become My Fuel (confirmed: main hand, drinking only) | Drinking a potion from the off hand imprints its first harmful non-instant effect (at the potion's level) on the main-hand weapon (any weapon family). Overwrites the old one. Every weapon hit (melee, arrows and thrown weapons from it) applies it for 10 s, whoever holds the weapon. Shown in the tooltip | config `fuelEffectTicks` |
| Overenchant | In the anvil, enchantment caps rise to 8 (modded caps above 8 stay) and "Too Expensive!" never blocks; you still pay the levels | config `overenchantMaxLevel` |
| Ascension (confirmed: double jump) | Creative-style flight in survival/adventure. Damage while flying (or while airborne, by the server's own check) takes flight away until you're standing on something or in water; that state is saved, so relogging mid-air doesn't skip the fall. Falls only stop hurting while you're actually flying (vanilla would make anyone who may fly immune) | `MiscSkill.updateFlight` |

## Chunk 7 — the last part

| Topic | Decision | Where to change |
|---|---|---|
| Stack Overflow (placeholder scope, see To-Do questions) | Anything that already stacks (max stack above 1) holds up to 300 per slot in your own inventory and Hoarder's Pocket, on both sides so the client predicts it. Chests, hoppers, shulkers and other players keep vanilla limits; a big stack splits as it goes in. Drag-spreading and double-click collecting still stop at the item's normal size. Switching it off splits big stacks back, pocket included (what doesn't fit drops). Saved items and container contents accept counts up to 300 everywhere, so nothing is cut on save. A big stack never leaves you as one item: dropping, dying or tossing it makes normal-size item entities, so hoppers and mobs can't cut it down. Creative mode keeps big stacks too | config `stackOverflowMax` (max 300) |
| Rune Embedding | 5 slots (slots 1-4 body, 5 bare hand), opened from an enchanted-book button under the pocket button, `/bloom runes` or an unbound key; a hopper-style screen. Only enchanted books; every enchantment in a book must be in the slot's tag; no curses; an enchantment can't be in two runes. Kept through death; take-out only without the Major | tags `bloomlevelup:rune/body`, `rune/unarmed` |
| Body runes | Work only while all four armor slots are empty. Each enchantment counts as worn on the first armor slot it fits (Protection anywhere, Respiration on the head...). Default tag: the four Protections, Feather Falling, Thorns, Respiration, Aqua Affinity, Depth Strider, Swift Sneak. Different Protection kinds can sit in different runes. Frost Walker and Soul Speed aren't in yet (they need location hooks); Unbreaking and Mending aren't allowed (nothing to wear down) | tag `rune/body` |
| Bare-hand rune | Works only with an empty main hand: a bare-handed attack hits with the rune's enchantments (Sharpness, Smite, Bane of Arthropods, Knockback, Fire Aspect, Looting, Sweeping Edge). Fists only sweep with Batter Up!, so Sweeping Edge needs it | tag `rune/unarmed` |
| Fitting Armor / Medium shield | When an axe disables your shield, it stays disabled half as long | `ArmorSkill.shieldDisableSeconds` |
| Nameplate (COMBAT.md) | Other players see `Name [lvl N]`, N = Major Levels currently spent; gray, green for good once any category is mastered | `client/NameTags` |

## Playtest round 1 (report for Chunks 3-5, 2026-10-20)

Rows above that these replace are marked "(see Playtest round 1)".

| Topic | Decision | Where to change |
|---|---|---|
| Mouse buttons | Minecraft 26.3 numbers mouse buttons differently (left = 1, right = 3; older versions used 0 and 1). The hub compared against the old numbers, so every left click acted as "refund" and nothing could be bought. It now uses the engine's own constants. A client game test clicks Majors with the real mouse | `HubScreen.mouseClicked` |
| Owned but off | An "OFF" badge and a rust-colored frame, so switching a Major off never looks like a refund | `HubScreen.drawMajors` |
| Armor readout | The Armor tab shows the real armor total and the overcap bonus (the vanilla bar stops at 20 points) | `HubScreen.drawHeader` |
| XP Investment on/off | Each stat's XP-invested points have their own switch (in XP mode the On/Off buttons cover only them), so 300 invested points can be dropped while the 100 level points keep working. The stat's own switch still turns everything off. Saved as `disabled_invested` | `ProgressionService.toggleInvested` |
| XP Investment look | XP mode: green "XP Investment points", a green wash and frame on the stat rows, values shown as `100 +200`. Shorter button labels ("Mode: Level / Mode: XP", "Buy point: N XP") so nothing is cut off | `HubScreen` |
| Weapon-skill key | One rebindable key, default right click: hold to charge (Charge Attack / Heavy Hands), tap to throw (Catch!). Letting go of a charge swings at the crosshair, never at a block. On right click it steps aside: blocks and mobs that use a click (chests, doors, log stripping, villagers, horses) go first, anything in the off hand wins, and items with their own right click (tridents, spears, bows, food) keep it. Aimed at the air or a hostile mob it acts at once. Bound to another key, right click is untouched. Replaces the old "Charge attack" (left click) and "Throw weapon" keys. Lightning Summon stays on sneak + left click | `client/combat/CombatControls` |
| Charge meter | A small bar under the crosshair while charging; gold and blinking when ready | `client/gui/BloomHud` |
| Straight Shot crosshair | With Straight Shot and a bow, the crosshair is a dot: red while drawing, green when fully drawn | `BloomHud` |
| Bloodlust bar | A red bar right of the hotbar: stacks, damage bonus and time left, refilled by each kill. Replaces the action-bar message | `BloomHud`, payload `Bloodlust` |
| Overkill radius | A ring of crit sparks marks the splash radius on every Overkill kill | `CombatSkill.overkill` |
| Inventory buttons | Survival inventory: the hub button sits right of the recipe book; Pocket and Runes stack above the off-hand slot (off the right side, so potion effects aren't covered). Creative keeps them outside the top-right corner | `HubButton`, `SideButton` |
| Reflect catch | Arrows that can't normally be picked up (a skeleton's) are caught too. Mob-thrown tridents and Infinity/creative arrows aren't, so a catch never farms or duplicates items | `ArmorSkill.catchable` |
| Spear jab | A spear's left-click jab stabs through `PiercingWeapon`, not `Player.attack`; its first stab now counts as the swing's own target, so Batter Up!, Impale (full strength) and the rest follow it | `PiercingWeaponMixin`, `CombatSkill.onStab` |
| Long Reach | Spears use their own attack-range component, not entity reach; Long Reach now adds +2 blocks to that range (jab and charge), both sides. Other weapons (via Copycat) still get +2 entity reach | `CombatSkill.longReach` |
| Catch! model | The client never knew which weapon was thrown (it defaulted to a trident); the thrown item is now synced | `ThrownWeapon` |
| Unrestricted Riptide | Working as designed (sneak while releasing); a one-time action-bar hint now says so when a trident without Riptide is used without sneaking | `CombatControls.riptideHint` |
| Stored Impact (replaces the cooldown) | The first real fall opens one 5-minute window; every real fall inside it adds 50% of its damage (max +20 total). Later falls don't restart the timer. The next mace hit spends it all, or it's lost when the window closes. The action bar shows the total and time left | config `storedImpactWindowTicks` |
| Wind Impact (rework) | Right-click the top of a block with a mace (one the block itself doesn't use): a wind-charge burst there, which launches you up if you're standing on it (falling back to where you jumped from is forgiven, as with a wind charge) and blows mobs within 3.5 blocks away. 1 s cooldown, shown on the mace. A thrown mace still bursts where it lands. Landing a fall no longer triggers it | `CombatSkill.windBurst`, config `windImpactCooldownTicks` |
| Impale | 50% (was 40%). Bleed has no potion swirl; it drips dark red and spatters on each damage tick (the status icon stays) | config `impaleChance`, `BleedEffect` |
| Lightning Shock | One bounce per second (spark line, burst and crack each time), so the ricochet can be followed | config `lightningShockBounceTicks` |
| Aura Sword | Three waves rolling outward to 1, 2 and 3 blocks, 0.2 s apart; a mob is hit by one wave at most | config `auraSwordRadius` (3), `auraSwordPulseGapTicks` |
| Growth Aura | 35% per crop per second (was 15%), and a crop now jumps a whole growth stage instead of getting one random tick; other plants (cactus, cane, berries...) get 4 growth ticks | config `growthAuraChance`, `growthAuraRandomTicks` |
| Shovel Mastery | 3% base treasure chance per dug block, plus 5% per 100 (boosted) Yield. Loot: music discs out; coal, gravel, lapis and raw iron in | config `excavationTreasureBaseChance`, loot table |
| Config versions | The config file now has `configVersion`. Files from before this round move to the new defaults above, but only for values that were still at the old default | `BloomConfig.migrate` |

## Playtest round 2 (handoff 2026-10-21)

| Topic | Decision | Where to change |
|---|---|---|
| Catch! spears (confirmed) | Spears can be thrown. With the weapon-skill key on its own key: tap throws, hold charges. On the default right click a spear keeps its own right-click charge (spears have their own use), so it throws only from a separately bound key (pending FreshOrangeSlices' choice of tap-to-throw on right click) | `CombatAbilities.throwable` |
| Catch! tridents (confirmed) | With Catch!, any trident you throw comes back as if it had Loyalty III | `CombatSkill.onEntityLoad` |
| Straight Shot on thrown weapons | Copycat can spread Straight Shot to thrown weapons; they fly straight but are never deleted: once they hit something (or after 5 s) gravity comes back and they fall or fly home. Only arrows vanish after 5 s | `CombatSkill.cleanupTick` |
| Lightning Shock (rework) | Three jumps from the struck target, each to the nearest enemy not shocked yet (so up to 3 other enemies); with fewer around it bounces back, never the same one twice in a row. Needs one other enemy. Each jump is a visible spark travelling between the two, landing 0.5 s later | config `lightningShockBounceTicks` (10) |
| Overdose | 7 Major Levels (was 5) | Major JSON |
| Become My Fuel | The imprinted effect lasts 5 s | config `fuelEffectTicks` (100) |
| Fitting Armor / Medium shield | An axe's 5 s shield disable becomes 1.5 s | config `mediumShieldDisableRatio` (0.3) |
| Wind Impact | Each burst costs 3 mace durability | config `windImpactDurabilityCost` |
| Growth Aura | 20% per crop per second (was 35%) | config `growthAuraChance` |
| XP Investment cost | Shown as a vanilla level, "Buy point: Lv 36": the level whose total XP covers the cost, rounded up so being at that level always affords it. The exact XP is in the tooltip. The economy is unchanged | `PlayerXpWallet.levelForPoints` |
| Stat switches | A small green dot when on, empty when off (tooltip says which) | `client/gui/DotToggle` |
| Rune Embedding screen | Its own screen type: armor outlines in slots 1-4, a sword outline in slot 5, title "Rune Embedding", and hovering an empty slot says what it takes | `RuneEmbedding.MENU`, `client/gui/RuneScreen` |
| Stored Impact bar | Orange, above the Bloodlust bar: 4 segments of 0.5 damage; each full bar (2 damage) becomes "×N"; shows the window's time left; gold when at the +20 cap | `BloomHud` |
| Spear sweep (Batter Up!) | A wide arc of three larger sweeps across the target, and the sweep reaches as far as the spear does (other weapons keep vanilla's 3 blocks) | `CombatSkill.sweep` |
| Aura Sword | The waves' rings are drawn at their outer edge: 1, 2 and 3 blocks | `CombatSkill.auraWave` |
| Config version 2 | Files at round 1's defaults move to these (fuel 5 s, Lightning 0.5 s, Growth Aura 20%) | `BloomConfig.migrate` |

## Playtest round 2b (answers 2026-10-21)

| Topic | Decision | Where to change |
|---|---|---|
| Weapon-skill key (confirmed) | Default **R** (no longer right click). It runs every input Major, the weapon's own skill first: Wind Impact with a mace (burst on the block top you aim at, or under you if you're on the ground), Lightning Summon with a trident (strike where you aim; no more sneak + left click), then hold to charge (Charge Attack / Heavy Hands), then tap to throw (Catch!). Rebinding it to right click brings back the step-aside rules | `CombatControls` |
| R-key conflicts (confirmed) | Majors that would share the key on one weapon can't be owned together (buying one offers to refund the other): Catch! vs Heavy Hands, Charge Attack, Wind Impact; Heavy Hands vs Wind Impact, Lightning Summon. Charge Attack and Heavy Hands don't conflict (same action) | `conflicts` in the Major JSONs |
| Stored Impact source (confirmed) | 50% of the fall's raw damage (1 per block past 3, times the landing block's multiplier), before Feather Falling, armor, Light armor or Titan Hide. Water, hay-style landings that deal nothing and Parkour dodges store nothing. Other fall-type damage (ender pearls) stores what it actually did | `CombatSkill.noteFall` |
| Shovel Mastery chance (confirmed) | 10% per natural dug block, plus 2% per 100 (boosted) Yield (about 12% at 100 Yield) | config `excavationTreasureBaseChance`, `excavationTreasureChancePer100Yield` |
| Shovel Mastery biomes (confirmed) | Treasure is themed by biome: nether, desert/badlands, beach/ocean/river, swamp/mangrove, mountains/caves, forest/plains/taiga/jungle/savanna, and a default pool elsewhere. No name tags or saddles anywhere. The biome lists are tags (`bloomlevelup:excavation/<pool>`, already including the common `c:` tags) and each pool is its own loot table, so modpacks can extend both | `tags/worldgen/biome/excavation/`, `loot_table/gameplay/excavation/` |

## Playtest round 3 (2026-10-22)

| Topic | Decision | Where to change |
|---|---|---|
| Lightning Shock (confirmed: 3 bouncing sparks) | Three sparks leave the struck target together, each to a different nearby enemy (nearest first); each spark then jumps on to the nearest enemy it hasn't shocked yet, 3 enemies per spark, bouncing back when few are around. Each jump takes 0.1 s and draws a streak. Every shock is 50% of the hit, so a crowd can take up to 9 shocks; still once per 2 s. Fewer other enemies than sparks means fewer sparks | config `lightningShockSparks`, `lightningShockJumps`, `lightningShockBounceTicks` (2), `lightningShockRatio` |
| Aura Sword | Waves out to 4 blocks, 0.45 s apart | config `auraSwordRadius` (4), `auraSwordPulseGapTicks` (9) |
| Major on/off | Owned Majors show a small corner dot: green when on, red when switched off (replaces the OFF text) | `HubScreen.drawMajors` |
| Stored Impact text | No chat/action-bar message any more; the HUD bar is the only display | `CombatSkill.storeImpact` |
| Become My Fuel text | The description and the weapon tooltip said 10 s; they now say 5 s (the tooltip reads the live setting) | lang, `BloomLevelUpClient` |
| Config version 4 | Round 3's defaults move over as before | `BloomConfig.migrate` |

## Predator and Combo meters (2026-10-22)

| Topic | Decision | Where to change |
|---|---|---|
| HUD meters | Predator (purple: stacks on the current target, bonus %) and Combo (blue: stacks, bonus damage) get bars left of the hotbar, mirroring Stored Impact and Bloodlust on the right. Each shows the bonus the next hit gets and empties as the stacks run out | `BloomHud`, payload `Meter` |

## Master of Arms and piercing throws (2026-10-22)

| Topic | Decision | Where to change |
|---|---|---|
| Piercing throws | Tridents and Catch! throws honour a pierce level (Piercing Shot through Copycat, a Charged Throw): they pass through that many mobs like a piercing arrow, the next one stops them as usual, then Loyalty brings them back. Vanilla tridents ignored pierce | `ThrownTridentMixin` |
| Master of Arms (confirmed: cost 3) | Universal Combat Major. Owning it lets Catch!, Heavy Hands, Charge Attack, Wind Impact and Lightning Summon be owned together (any two of them). It can't be switched off. No prerequisite. (Refunding it: see round 4) | `master_of_arms.json` (`waives`), `ProgressionService` |
| How the combos are pressed | With Master of Arms and a charge Major for the weapon, every press of R is a hold and the release decides: a full charge released on a mob in reach is the charged swing (as before); released anywhere else it's the charged combo; a tap (or an early release) is the normal skill. The server re-checks the Majors, the weapon and that the swing has recovered | `CombatControls`, `CombatAbilities` |
| Charged Throw (Charge Attack / Heavy Hands + Catch!) | 2x damage (the charge multiplier), 1.4x throw speed, goes through 3 mobs | config `chargedThrowSpeedMultiplier`, `chargedThrowPierce`, `chargeAttackMultiplier` |
| Charged Burst (Heavy Hands + Wind Impact) | A wind burst 1.3x the size: blast, push radius and push strength (a higher launch). Same cooldown and durability as a normal burst | config `chargedBurstScale` |
| Storm Call (Heavy Hands + Lightning Summon) | 3 bolts evenly around the aim point, 2.5 blocks out, 0.15 s apart. Costs and cools down 2x one bolt | config `stormCallBolts`, `stormCallRadius`, `stormCallGapTicks`, `stormCallCostMultiplier` |
| Gale Throw (Catch! + Wind Impact) | With a mace, R bursts when you aim at a block and throws otherwise. Pulls mobs in, then 0.4 s later bursts 1.4x bigger and blows them out (round 4: only for a charged throw) | config `galeThrowPullRadius`, `galeThrowPullStrength`, `galeThrowPullTicks`, `galeThrowBurstScale` |
| Data: `waives` | A Major JSON may list `waives`: Majors whose conflicts with each other don't apply while it's owned. Unknown ids are dropped with a warning | `MajorDefinition`, `DefinitionCodecs` |

## Playtest round 4 (2026-10-22)

| Topic | Decision | Where to change |
|---|---|---|
| Refunds cascade (confirmed) | Refunding a Major takes everything that depends on it along, after a confirm screen listing them: Majors whose prerequisite it was (Batter Up! when its last Sword Major goes, Heavy Hands when its last Axe Major goes), and both Majors of every pair only Master of Arms allowed. It repeats until nothing else falls; everything is refunded in full. A swap does the same for the Majors it swaps out. `/bloom refund <major>` names them; `/bloom refund <major> all` confirms. This replaces "refund blocked while required" and round 4's refund guard on Master of Arms | `ProgressionService.refundCascade`, hub `confirmRefund` |
| Charged Burst (confirmed: too weak) | A wind charge launches whoever stands on it by the same amount whatever the blast size, so 1.3x did nothing for the launch. Now the charged launch sets your upward speed to 1.65 (about 14 blocks vs a normal burst's 8) and mobs are pushed 1.7x as hard. Landing back at the launch height is still harmless | config `chargedBurstLaunchSpeed`, `chargedBurstPushScale`, `chargedBurstScale` |
| Gale Throw (confirmed) | Only a charged mace throw with Wind Impact (Heavy Hands + Catch! + Wind Impact) is a Gale Throw: pull in, then blast out. A plain thrown mace (Catch! + Wind Impact) bursts like Wind Impact where it lands. The pull reaches 6.5 blocks (was 7: a 1-block smaller ring) | config `galeThrowPullRadius`, `galeThrowBurstScale` |
| Durability (confirmed) | A Catch! throw costs 3, a charged swing 2 in all (Charge Attack or Heavy Hands; the hit's own wear counts toward it), a Charged Throw or Gale Throw 8, a Charged Burst 2 more than a normal burst (5). A throw never breaks the weapon: one too worn to pay isn't thrown ("too worn", like Lightning Summon). Storm Call stays at 2x one bolt (20% of the trident) | config `catchThrowDurabilityCost`, `chargedSwingDurabilityCost`, `chargedThrowDurabilityCost`, `chargedBurstExtraDurability` |
| Charge released on a mob | Stays a charged swing (confirmed as seen in testing) | `CombatControls.release` |

## Hub redesign (2026-10-22)

| Topic | Decision | Where to change |
|---|---|---|
| Layout (confirmed) | Every tab is one 416×320 page (its background picture's size), centred in the space right of the stats window (all of it while the window is folded). Where the page is bigger than that view it can be dragged or scrolled, but only until its edges show (confirmed: keep movement small); where it fits it doesn't move. Combat is a tree centred on the page: Master of Arms in the middle, the weapons on an inner ring clockwise from the top, each weapon's Majors in a short row just outside it. Universal Majors that need a weapon (Batter Up! and Aura Sword: Sword; Heavy Hands: Axe; Catch!: Trident; Death From Above: Mace) sit on the line between the middle and that weapon (confirmed); the other universal Majors in rows of up to 13 along the bottom. Weapon names sit on the side toward the middle. Utility, Armor and Misc use 32-pixel icons (half the 64-pixel art, so sharp) placed by hand to fit each picture (confirmed): Utility by what's in the garden (Triple Catch at the pond and dock, Master Shearing above the sheep, Growth Aura by the crops, Timber by the trees and beehive, Vein Mining under the grey rocks, Shovel Mastery on the dirt path); Armor with Iron Skin (the most expensive) in the middle and groups around it (blades & blunt: Vibranium, Adamantium, Absorb; elements & effects: Elemental Skin, Thick Blood, Unyielding; hit back: Reflect, Rebound; cheat death: Last Breath, Castle of Stone; armor style: Fitting Armor, Titan Hide; Protein, Parkour and Brace on their own); Misc with Ascension (the most expensive) on the compass and the rest in a circle around it, cheapest at the top going clockwise. Majors without a spot are scattered | `HubScreen.treeLayout`, `scatterLayout`, `clampPan` |
| Lines (confirmed: red when active) | Middle → each weapon and weapon → each of its Majors. Red while a Major on that line is on, dim otherwise. No lines to universal Majors: their prerequisites stay hidden | `HubScreen.drawCanvas` |
| Stats window (confirmed) | Compact and foldable (the arrow): + above − to the left of each icon; the icon is the on/off switch with the same green/red corner dot as the Majors; Mode and Buy point stacked under the rows | `HubScreen.addStatsWindow`, `MiniButton`, `StatIcon` |
| Background art (confirmed: one size for every tab, 416×320 to match the art) | `textures/gui/hub/background_<tab>.png` (combat, utility, armor, misc; `background.png` for any tab without one), 416×320 (FreshOrangeSlices' art: Utility the garden, Combat the dark stone cave, Armor the Nether, Misc the purple End), drawn pixel for pixel (1 picture pixel = 1 GUI pixel, like vanilla GUI art) and centred on the page; other sizes still work, centred the same way. Guides showing every node in `docs/art/hub-templates/` (`make_templates.py` redraws them when Majors change). Until a tab has art: the advancements screen's stone, darkened, filling the page | `HubScreen.drawBackground`, `docs/art/hub-templates/` |
| Weapon nodes | In vanilla's advancement goal frame (gold once you own a Major in that tree). Art `textures/icon/tree/<weapon>.png` (64×64; sword, axe, bow, crossbow, trident, mace, spear, unarmed); until then the vanilla item (a player head for Unarmed). Master of Arms sits in the challenge frame | `HubScreen.drawWeaponNode` |
| Screenshots in CI | The client test shoots the Combat tree at 1920×1080, GUI scale 3 and 4; the client-test workflow's `screenshots` job prints them as base64 JPEG annotations so they can be checked without downloading artifacts | `BloomClientGameTests`, `.github/scripts/emit_screenshot.py` |

## Playtest round 5 (2026-10-22)

| Topic | Decision | Where to change |
|---|---|---|
| Buy point with huge XP | Vanilla XP is counted in longs and spent exactly: the old int maths overflowed past about 21,800 levels (the button greyed out), and vanilla's float bar drifted when spending from a big total. Buy point also re-checks your XP every tick while the hub is open. The client test buys a point with 10,000 levels | `PlayerXpWallet`, `HubScreen.tick` |
| Category icons (confirmed: not needed) | Categories have no icon picture any more (the hub doesn't draw one); Preservation's icon is the wrench again | category JSONs, `textures/icon/skill/` |
| `hub_position` (Major JSON) | `"hub_position": [x, y]` puts a Major's icon at that spot on its tab's page, in pixels of the tab's background picture from its top-left corner (the page is 416×320). Used on tabs without weapon trees; the Combat tree ignores it. Synced to clients with the definitions | `MajorDefinition.hubPosition`, `DefinitionCodecs.HUB_POSITION` |
| Major Level counter | Moved from the top-right of the page to the strip under it (hover for "spend them anywhere"), so nothing on the page sits under it; "Drag to see more" shows on the left of that strip only when the page is bigger than the view | `HubScreen.drawWallet`, `drawStatusLine` |

## Playtest round 6 (2026-10-22)

| Topic | Decision | Where to change |
|---|---|---|
| Coverage | Every Major and stat now has a game test; the ones added this round: Brace!, Vibranium/Adamantium, Rebound, Reflect's rolls, Power/Tempo, Piercing/Straight Shot, Marksman/Reinforced Shot, Disarm, Impale, Unrestricted Riptide, the Charged Burst launch and Preservation | `src/gametest` |
| Brace! | Crouching still or raising a shield braces you at once (`braceDelayTicks` 20 → 1, moved in old config files). It has to start on the ground; after that only your own movement ends it (walking, jumping, standing up, lowering the shield). Knockback no longer breaks it, as the Armor doc always said | `ArmorSkill.tick`, config `braceDelayTicks` |
| Brace! cooldown | Once a brace ends it needs 10 seconds before it can take hold again (`braceCooldownTicks` 200), so hopping while crouched can't re-brace on every landing. Logging out or dying clears the brace without a cooldown. The braced state and the cooldown are profile counters (`armor/brace#braced`, `#ready_at`), synced to the client | `ArmorSkill.tick`, `ArmorIds` |
| Brace! on the HUD | Its icon sits right of the Bloodlust / Stored Impact bars: lit in a gold box while braced; greyed, with a shade that drains and the seconds left, while it cools down; hidden otherwise. Until the Brace! art exists it shows the vanilla shield. The old "Braced" action-bar text is gone | `BloomHud.brace` |
| Master of Arms text | Just "use them all together"; the combos are explained in the README, not the hub | lang `master_of_arms.desc` |
| Crossbow Infinity | Sits in the Crossbow tree (group `combat/crossbow`) next to Reinforced Shot and Assault Rifle. Still needs nothing to buy, and it isn't a weapon Major (no `weapon` flag), so Copycat can't copy it | `crossbow_infinity.json` |
| Bottom row | The priciest universal Major (Copycat) sits in the middle of the Combat tab's bottom row, the rest around it in their usual order | `HubScreen.treeLayout`, `make_templates.py` |
| Timber | Moved to the flowers under the big tree, left of the waterfall (`hub_position` [268, 44]) | `timber.json` |
| Hub window | The window hugs its page: the stats window's column plus the 416×320 page while the stats are open, just the page while they're folded (the folded header then sits on the page's top-left corner). The game shows, blurred, around it. On a screen too small for that it fills the screen and the page drags, as before | `HubScreen.init` |
| Gold frame and tabs | A gold frame around the window: a 28×28 nine-slice GUI sprite with a 6-pixel border, made by `docs/art/hub-templates/make_frame.py` (repaint the PNG freely; keep the border 6 wide or change the `.mcmeta`). The tabs are gold-rimmed plates in the same golds; the open tab is lit | `textures/gui/sprites/hub/frame.png`, `HubTab` |

## Playtest round 7 (2026-10-23)

| Topic | Decision | Where to change |
|---|---|---|
| Art | Every Major has its 64×64 art now, plus the Power, Tempo, Critical and Penetration stat icons. Weapon nodes stay the vanilla items (confirmed) | `textures/icon/` |
| Category tabs | Small gold plates with an item instead of a name (Utility a diamond pickaxe, Combat a diamond sword, Armor a diamond chestplate, Misc a Regeneration potion), centred in the tab row; the name shows on hover | `HubScreen.tabItem`, `HubTab` |
| Bloom+ button | Top left of the tab row, the red arrows (`gui/hub/reset_levels.png`): the existing Bloom+ reset (every category to level 0, natural stat points back, every Major refunded; invested points and unlocks kept; +1 bonus Major Level per 50 levels reached in a category, up to 2 each and 6 in all). The tooltip and the confirm screen say what it would pay right now | `HubScreen.addResetButtons`, action `BLOOM_RESET` |
| Reset Majors button | Top right, the white arrows (`gui/hub/reset_majors.png`): refunds every Major in the open tab (and anything elsewhere that needs one of them, like Rainbow after Crossbow Infinity) and pays nothing extra. Then that tab can't be reset again for 20 minutes of game time (`majorResetCooldownTicks` 24000, a profile counter per category, so it survives relogging). Right-click refunds of single Majors stay as they were | `ProgressionService.resetCategoryMajors`, action `RESET_MAJORS` |
| Taking stat points back | The minus buttons only show once the category is level 100, and the server refuses a refund below that (`statRefundAtCapOnly`, on by default), so early points are a commitment. Bloom+ drops the level to 0, so they disappear again until the next 100. XP-bought points can always be taken back (round 7b) | `ProgressionService.allocate`, `canRefundStats` |

## Round 7b: resets and builds (2026-10-24)

| Topic | Decision | Where to change |
|---|---|---|
| Single refunds | Gone for players: right-click on an owned Major does nothing (round 8 turns it into a details window), and `/bloom refund` answers "switched off" (`allowMajorRefund` false). The tab's white reset button is the way to refund. Admins: `/bloom admin refund <players> <major>` | `BloomSync.handle`, `BloomCommands` |
| Swaps | Clashing Majors (all in Combat today) can still be swapped, but only if the new one can be paid for up front; the swapped-out one comes back in full afterwards (so a clever order can win levels back, accepted) | `ProgressionService.purchase` |
| Red button (Bloom+) | Per tab now: that category to level 0, its natural points back, its Majors refunded in both builds (with anything elsewhere that needs them). +1 bonus Major Level per 50 levels, up to 2 per tab and 8 in all (`prestigeTotalCap` 6 → 8, moved in old config files). Any time, no wait, asks twice. `/bloom bloomreset <category> confirm` does the same | `bloomResetCategory`, `HubScreen.confirmBloom` |
| White button | Unchanged: one 20-minute game-time wait per tab, shared by both builds; resets the build in use | `resetCategoryMajors` |
| Waits on/off | `/bloom admin waits on\|off` switches both waits (white button, build switch) for the server; saved to the config and sent to everyone | `waitsEnabled` |
| Buy point | Shift-click buys as many points as your XP pays for, up to 10 | `HubScreen.addStatsWindow` |
| Builds (presets) | Two builds; the second unlocks the first time any tab reaches 100 and starts as a copy of the first. Each holds its own Majors (with on/off and Copycat's pick), natural stat points and stat switches, from the same Major Level and stat point pools. Shared: levels, XP-bought points, each Major's own progress (Protein! meals are only cleared when neither build owns it). Switching waits 2 minutes of game time (`buildSwitchCooldownTicks`). Bloom+ clears that tab's part of both builds. Hub: an iron helmet (Build 1) and a golden helmet (Build 2) in the left corner of the strip under the page (the status text moves right of them), the one in use lit; `/bloom build` too | `SavedBuild`, `switchBuild`, `HubScreen.addBuildButtons` |
| Strip under the page | 20 pixels tall now (the build helmets) | `HubScreen.STRIP_H` |

## Round 8: Major details window (2026-10-24)

| Topic | Decision | Where to change |
|---|---|---|
| Opening it | Right-click an owned Major in the hub. A window over the dimmed hub shows its icon, name, tab, tree and cost, then sections: What it does, The numbers, How to use it, Catches, and Combos you own. Esc, the × or a click outside closes it; the wheel scrolls | `HubScreen.drawDetails` |
| Numbers | Written in the lang file as `{configField}` tokens (`:pct`, `:sec`, `:min`, `:x` units) and filled from the server's synced config, so a changed config shows its own numbers. Values that are code constants are written out | `MajorDetails.fill`, `<major>.detail.*` lang keys |
| Combos | Only combos the player owns every piece of are shown, on each piece's page (all of them on Master of Arms). Nothing is shown about combos the player can't use yet | `MajorDetails.COMBOS` |
| Copycat | Its page also says what it's copying right now | `MajorDetails.sections` |
| Font | The normal Minecraft font everywhere (headings and names in bold). A fancy font was tried and dropped at FreshOrangeSlices' request | `MajorDetails.heading` |

## Round 8b: the stat sheet (2026-10-24)

| Topic | Decision | Where to change |
|---|---|---|
| Where | A Stats button (bar-chart picture, placeholder art) left of the white reset button opens a window over the hub for the open tab; switching tabs closes it | `HubScreen.addResetButtons`, `StatSheetWindow` |
| Who computes | The server, with the game's own rules: attributes with the chosen item swapped in, enchantments' damage and protection rules run against stand-in mobs (never added to the world), vanilla's armor formula, Bloom's hooks in the same order as a real hit. The client only draws finished lines and sends what was picked; it asks again every second (at most 20 requests a second are answered) | `sheet/`, `SheetPayloads` |
| Combat | Any weapon in the inventory (or bare hands) can be picked; the summary shows hit, crit, attacks per second, damage per second, reach, sweep, throw; the full breakdown lists every source (base, weapon, Power, Brawler, other effects, enchantments with their undead/arthropod/water extras, each Bloom bonus, crit multiplier, target armor and Penetration, speed, area effects, on-hit effects, arrows) | `CombatSheet` |
| Target | No armor / iron (15) / diamond (20, 8 toughness) / netherite (20, 12) | `CombatSheet.TARGET_ARMOR` |
| "Show it at max" | One switch per effect the player owns that works with that weapon: Charged, Charged Throw, Falling (10 blocks), Stored Impact, Combo, First Strike, Predator, Bloodlust, Marksman (Armor: Braced). Off = what you have right now (Stored Impact shows what's stored) | `CombatSheet.build` |
| Picture | The vanilla sword + Sharpness level whose plain hit is closest; past Netherite + Sharpness V, the netherite sword with a gold "×N" (how many times harder). Armor: the full vanilla set + Protection level letting the closest amount of the same mob hit through ("×N" = how many times less). Utility: the same kind of tool + Efficiency | `swordGauge`, `armorGauge`, `UtilitySheet.gauge` |
| Armor | A picker for the size of the incoming hit (5/10/20/50, 10 first); what reaches your health from a mob, blade (if Vibranium), mace (if Adamantium), arrow, fire, explosion, fall and magic hit; the mob hit step by step (armor, Resistance, Protection, each Armor layer, Absorb); dodge and other ways to avoid a hit; health and the rest | `ArmorSheet` |
| Utility, Misc | Utility: tool picker, mining speed and break time on the tool's block (stone, dirt, oak log, hay), Yield, Preservation with Unbreaking, Utility Majors. Misc: Wisdom, Endurance, Speed (walking blocks per second), effects and storage Majors | `UtilitySheet`, `MiscSheet` |
| Not counted | Difficulty scaling of mob hits (the sheet's "hit of 10" is after it), blocking with a shield, absorption hearts, Shields' and helmets' special cases | |

## Round 8c: stat sheet follow-ups (2026-10-24)

| Topic | Decision | Where to change |
|---|---|---|
| Switches | Three ways each, clicked in turn: **Live** (what you have right now: armed charge, your fall in progress, what Stored Impact holds, Combo/Predator/Bloodlust stacks, Braced; effects that depend on the target or distance, like First Strike and Marksman, are off when Live), **Max**, **Off**. An **All** switch sets them all together. Live is the default | `CombatSheet.switches`, `StatSheetWindow.modeChip` |
| Mob-specific enchantments | Smite (green), Bane of Arthropods (purple) and Impaling (aqua) show as a coloured "+x" after Hit, Critical hit, Damage per second, Sweep and Thrown, with a key under the summary; in All stats every enchantment has its own line, the mob-specific part in its colour | `CombatSheet.Kind`, `enchantmentRows` |
| Remembered | Each tab keeps its weapon/tool, target, switches and page in `config/bloomlevelup-sheet.json` (survives quitting) | `SheetMemory` |
| Keybind | "Open Stat Sheet" (unbound by default) opens the sheet for the tab last open in the hub; closing it closes everything | `BloomLevelUpClient`, `HubScreen.onStatSheet` |
| Headings | Normal font, bold, 1.5× size (details window headings and name, sheet title and headings) | `MajorDetails.big` |
| Stats button | A plain white bar chart, like the other two buttons | `textures/gui/hub/stat_sheet.png` |

## Round 8d: comparing on the stat sheet (2026-10-24)

| Topic | Decision | Where to change |
|---|---|---|
| Pin a weapon or tool | Right-click one in the weapon/tool row to pin it (blue outline and corner mark); right-click again to unpin. The sheet then shows the picked item's numbers, each followed by "(+x)" or "(-x)" against the pinned one: green when better, red when worse. The item in hand is pinned by the slot it's in, so scrolling the hotbar doesn't move the pin. Not remembered after the window closes | `StatSheetWindow.togglePin`, `StatSheets.build` |
| Build compare | Once there is a second build, a "Build N" chip in the sheet's header shows the other build's numbers (its Majors, natural points and stat switches) with the difference from the build in use. Pinning and the build chip are either/or. Worked out by putting the other build in place for a moment on the server and back at once (nothing is saved or sent in between) | `ProgressionService.withOtherBuild`, `StatSheets.build` |
| Which lines are compared | Lines are matched by label with any numbers in it ignored (so "Endurance (12)" matches "Endurance (13)"), the n-th of a name with the n-th; the first number of each value is compared. Lower is better for health lost, waiting times (time to full strength, draw, load, break time) and cuts shown as a green "-x%"; higher is better otherwise | `StatSheets.pair`, `lowerIsBetter` |
| One more point | Tried (hovering a stat's + listed what one more point would change) and dropped: too much detail for the stats window, and how slowly stats scale should be felt in play rather than read off a tooltip. The + keeps its plain tooltip | — |
| Details → sheet | A Major's details window has a bar-chart button (left of the ×) that opens its tab's sheet showing it at work: its switch on Max (Predator, Bloodlust, Combo, First Strike, Stored Impact, Charge Attack / Heavy Hands → Charged, Death From Above → Falling, Marksman, Master of Arms → Charged Throw, Brace! → Braced) and, for a weapon tree, the held weapon if it's of that tree, else the first one in the inventory (bare hands for Unarmed) | `StatSheetWindow.focus`, `LINKED_SWITCH` |
| Requests | A compare request counts once against the sheet's limit of 20 requests a second per player (the server works out two sheets for it) | `StatSheets.PER_SECOND` |

## Round 9: Better Combat (2026-10-24)

Better Combat is optional: Bloom compiles against it but never bundles it, and every change below only switches on when it is installed. Without it Bloom is exactly as before. CI runs the game tests twice, once plain and once with Better Combat, Cloth Config and Player Animation Library loaded (`runGameTestBetterCombat`).

| Topic | Decision | Where to change |
|---|---|---|
| Swings that hit several mobs | Every mob a Better Combat swing hits is a normal hit for Bloom (each goes through `Player.attack`), so each gets the full set of bonuses and effects. A stored charge and Stored Impact are spent on the first | `CombatSkill.applyBonuses` |
| Predator | With Better Combat installed, any mob keeps the stacks going (not just the same target), for melee and projectiles | `CombatSkill.applyBonuses` |
| Combo | Unchanged rule: each full-strength hit adds a stack, so a swing that hits three mobs adds three | `CombatSkill.applyBonuses` |
| Crowd penalty | Better Combat weakens a swing that hits more mobs (down to -50% at 5 mobs, by default) and turns the sweep ratio into a bonus. Full Sweep (swords) and Batter Up! (every other melee weapon) cancel the penalty: every mob takes the whole hit, and Full Sweep's own ratio adds no bonus. Done with a transient attack-damage modifier for the length of the swing, sized from the number of mobs in Better Combat's request (its server handler runs the whole swing at once; Bloom marks the player around it) | `compat/BetterCombat.crowdCounter`, `BetterCombatServerNetworkMixin` |
| Batter Up!'s own sweep | Skipped inside a Better Combat swing (it already hits the whole arc) | `CombatSkill.onHitEffects` |
| Charged swing | Letting go plays a slam instead of the next combo swing: Better Combat's one-handed slam, the two-handed heavy slam for two-handed weapons, an uppercut for fist weapons and bare hands. The hit lands with a dust ring, a small blast and a heavy thud. The armed charge waits 2 s (instead of 1 s) for the swing's wind-up | `client/compat/BetterCombatClient`, `BetterCombatAttackInteractorMixin`, `BetterCombat.slamShockwave`, `CombatAbilities.armCharge` |
| Spears | Better Combat turns jabs into normal swings, so Bloom's spear Majors work off those; Long Reach still adds reach (Better Combat reads the same attribute) | — |
| Stat sheet | For weapons Better Combat handles: each swing of its combo (shape, arc, damage) and what each mob takes when a swing hits 2 to 5 mobs, in place of the vanilla sweep. The summary shows "Hitting 3 mobs, each" | `CombatSheet.betterCombatRows` |
| Details window | Predator, Combo, Full Sweep, Batter Up!, Charge Attack and Heavy Hands get a "With Better Combat" section, only shown when it's installed | `MajorDetails.sections`, `*.detail.bettercombat` |

## Round 10: the Bloom Pack (2026-10-24)

| Topic | Decision | Where to change |
|---|---|---|
| What's in it | The "Start with" mods from the Bloom Pack mod list, plus GeckoLib (World Bosses needs it) and every required dependency Modrinth lists. Added after the first pack: Origins: Legacy, Villagers at Work / after Work (with Fabric Language Kotlin), Fresh Animations: Player Extension (with the EMF compat mods for Not Enough Animations and Better Combat), Aerial Hell, Moog's Soaring and End Structures, Homesteads, Ambient Creatures, Wandering Trader Maps and the Cave Horror datapack. Left out on purpose: Repurposed Structures, Undead Riders, Friends&Foes, Tiny Skeletons, Distant Horizons (performance), More Villagers (Villagers at Work has no routines for its new jobs) | `pack/pack.json` |
| Versions | Each build takes the newest 26.3 Fabric build of each mod (releases before betas before alphas) | `pack/build_pack.py` |
| Packed inside | Bloom (from the same build), World Bosses (CurseForge's Fabric 26.3 file 2.0.2) and Villagers at Work / after Work 1.3.3. Fine for sharing privately; publishing it would need those authors' permission | `pack/pack.json` "external" |
| Datapacks | A datapack-only project (Cave Horror) goes in the server zip's `world/datapacks` (on for the server's world on its first start) and in the instance's `datapacks` folder for adding to singleplayer worlds by hand | `pack/build_pack.py` |
| Server-only structure mods | Moog's Soaring / End Structures say "server only"; they go on the client too (with their library), so singleplayer worlds have them | `pack/pack.json` "singleplayer_too" |
| The server download | One zip that is a whole server folder: Fabric's server launcher, mods, `world/datapacks`, start.bat / start.sh (5 GB heap), README, `server.properties` (view 10, simulation 8, no spawn protection, flight allowed for Origins) and `eula.txt` set to false for the owner to accept. On a host only `mods` and `world` are uploaded | `pack/build_pack.py` |
| Sides | A mod Modrinth marks "unsupported" on a side stays off that side; "optional" counts as required (so launchers don't ask). Cloth Config and Player Animation Library are forced onto the server (Better Combat needs them there) | `pack/pack.json` "env_overrides" |
| First launch | Fresh Animations is on and the Complementary shader selected (client-only files, applied when the pack is installed) | `pack/pack.json` "client_presets" |
| Checks | Every jar's required mod ids must be in the pack on the client and on the server, or the build fails; the server zip then starts the way a friend would start it (EULA accepted, start.sh), and the run notes how long it took, its memory and the datapacks on | `pack/build_pack.py`, `pack/boot_server.py`, `build.yml` job `pack` |
| Pack launch test | A dev client starts with the pack's whole client side (Bloom from the commit; the pack's resource packs, shader and settings), makes a normal world with structures and mobs, picks an origin, and opens the hub; screenshots of the title, join, world, player, a Better Combat swing, the hub and the shader. Sodium and Iris put the game on OpenGL, which Xvfb can't give it, so this run uses SDL's offscreen video driver (Mesa EGL, software). Software drawing with the shader takes seconds a frame: the world part runs with the shader off and view distance 3, and the shader comes back for the last picture | `client-test.yml` jobs `client-test-pack`, `pack-screenshots`, `pack-screenshots-2`; `BloomClientGameTests.packLaunch` |

## Round 11: Execute and World Bosses (2026-10-24)

| Topic | Decision | Where to change |
|---|---|---|
| Execute timing | Execute finishes a target that was already at or under the line before the hit landed: the hit that takes it under doesn't execute, the next sword hit does (any hit that lands and does damage, sweeps and splash included). Health before the hit counts absorption | `CombatSkill.onHitEffects`, `docs/design/COMBAT.md` |
| World Bosses: bosses | Its seven world bosses (Astrael, Aureon, Elyndra, Morvath, Nekhraz, Sylvaron, Varkhul), four realm bosses (Dragon Mother, Shadow King, The Shadow Weaver, Storm Griffin) and the Shard Sentinel (it has a boss bar) are in `bloomlevelup:bosses` (Execute at 10%). Optional entries, so nothing changes without the mod. No extra Combat XP multiplier yet: XP already scales with the damage dealt | `data/bloomlevelup/tags/entity_type/bosses.json` |
| World Bosses: armor | Worldbreaker (4/9/7/4, toughness 3.5) and Shadowbreaker (5/10/8/5, toughness 6) both beat netherite, so both are heavy | `data/bloomlevelup/tags/item/armor/heavy.json` |

## Round 12: mob looks in the pack (2026-10-24)

| Topic | Decision | Where to change |
|---|---|---|
| Better Animations Collection | Out of the pack. It re-models most of the mobs Fresh Animations does (villager, sheep, cow, chicken, pig, creeper, zombie, skeleton...), and with both, Fresh Animations' models load but don't animate: mobs with their eyes shut, some mobs looking vanilla. The CI close-ups with and without it showed the difference (sheep with eyes, snowy villagers in their coats) | `pack/pack.json` |
| Villager Guards | Out of the pack (FreshOrangeSlices' call). The datapack makes each guard an invisible zombie, skeleton or piglin wearing iron and chainmail armor and a villager-face player head, so the see-through limbs and flat face are its design. Guard Villagers (a mod with real guard models) is the candidate once it has a 26.3 build. Guards already in a world stay as invisible armored mobs: `/kill @e[name="Villager Guard"]` | `pack/pack.json` |
| Villagers' model | Fresh Animations does villagers too. Tried and undone: switching its villager model off with EMF's per-model switch left villagers with their eyes shut, because Fresh Animations also replaces the villager skins with the eyes painted out (its model draws them as separate pieces). With Fresh Animations' villager, Villagers at Work's optional visuals (separate arms swinging the tool) can't move its arms, so working villagers don't swing; the village itself runs the same. A Fresh Animations × Villagers at Work model pack, like the one Villagers at Work's author made for Human Era, would add that (`client_presets.files` in pack.json can still write client config files) | `pack/pack.json` |
| Mob close-ups in CI | The pack launch test photographs villager, sheep and cow; zombie, skeleton and creeper; pig, iron golem and chicken (pack_mobs_1-3), and can compare variants of the pack (`without:` in the client-test matrix, `build_pack.py --without`) | `client-test.yml`, `BloomClientGameTests.mobGallery` |

## Build numbers

| Topic | Decision | Where to change |
|---|---|---|
| Versioning | CI builds are versioned `0.1.0+build.<run number>` (the jar name too); local builds `0.1.0+local`. The version shows in the hub's bottom-right corner, Mod Menu, the server log and the run's jar-check notice | `build.gradle` |

## Still open (need a design call)

- Nothing blocking.

- Nothing blocking. Playtest results may retune the Combat numbers and the Misc XP rate.

## Hub UI requirements (from FreshOrangeSlices, for the hub chunk)

- A button in the inventory screen opens the hub; optional unbound keybind.
- A toggle button next to each levelable stat.
- Major icons: glowing when owned and on, dimmed when owned and off, shadowed when not owned.
- Icon art is 64×64 PNG (confirmed). The hub draws each icon in a 32×32 GUI-pixel slot, so it is pixel-exact at GUI scale 2 and downscaled cleanly at scale 1; use nearest-neighbour filtering.
