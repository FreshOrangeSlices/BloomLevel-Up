Misc Skill System
Misc Tab - Detailed Design Specification
The “rule-breaking” category: vanilla XP-driven progression, three simple stats, and high-impact Majors that deliberately bend Minecraft’s normal rules.
Misc is not a junk drawer. Its identity is changing general Minecraft rules that do not belong cleanly to Utility, Combat, or Armor. Its base stats stay simple so the Major pool can carry most of the tab’s complexity.
1. Misc identity and progression
• Misc Level runs from 1 to 100.
• Misc receives skill XP whenever the player collects vanilla Minecraft XP orbs.
• The collected XP orb still gives the player its normal vanilla XP. Misc progression mirrors the orb value and does not steal, redirect, or reduce the player’s XP.
• Misc uses the original/base XP-orb value for its own progression, before Wisdom multiplies the player’s vanilla XP reward.
• Mending does not reduce Misc XP gain. If an XP orb is consumed partly or fully by Mending, Misc still receives the orb’s original mirrored value.
• XP bottles count because they create collectible vanilla XP orbs.
• Misc should be tuned as the second-easiest of the four main categories to reach Level 100.
• Every 10 normal Misc levels grants 1 global Major Level, for 10 natural Major Levels from Misc 1-100.
• Vanilla XP may still be manually invested into normal Misc progression before Level 100, consistent with the broader pre-100 system.
• Wisdom can therefore create an intentional feedback loop: boosted vanilla XP can be manually reinvested into Misc, but Misc’s automatic mirrored XP always uses the original orb value.
• Misc does NOT have a post-100 XP Investment tab.
2. Three base Misc stats
Stat
 | Effect
 | 100-point target
 | 
Wisdom
 | Multiplies the player’s vanilla XP reward from collected XP. Does not multiply automatic Misc XP. With a spell mod (Spell Power, from the RPG Series) it also gives spell haste: faster casting and shorter spell cooldowns.
 | About 5x vanilla XP gain at 100. First-pass linear target: 1x at 0, 3x at 50, 5x at 100. Spell haste +0.5% per point (+50% at 100).
 | 
Speed
 | General player movement-speed increase.
 | 2x normal movement speed at 100. First-pass linear target: +1% movement speed per point.
 | 
Endurance
 | Makes food saturation last longer by reducing how quickly it is depleted.
 | About 1.5x saturation longevity at 100; implementation can reduce exhaustion gain accordingly.
 | 
Misc intentionally has only three base stats. The Majors are expected to be the most unusual and build-defining part of this tab.
3. Major pool and costs
Misc Major
 | Cost
 | Core effect
 | 
Iron Stomach
 | 2
 | Unsafe/raw/rotten/poisonous foods become safe to eat and gain a minimum nutrition floor equal to cooked chicken.
 | 
Selective Cleansing
 | 2
 | Milk and eligible vegetables remove all negative status effects while preserving positive effects. Apples and berries are excluded from the vegetable rule.
 | 
XP Insurance
 | 3
 | On death, preserve/return 50% of the vanilla XP that would normally be lost.
 | 
Second Serving
 | 4
 | Allows the player to eat any food even while completely full.
 | 
Water to Wine
 | 5
 | Toggleable. While active, newly applied finite status effects are extended to at least 10 minutes; applies to buffs and debuffs. Turning it off cuts Water-to-Wine-extended effects to 2 minutes remaining.
 | 
Overdose
 | 5
 | Repeated applications of the same effect can raise its amplifier, up to +2 levels above the original source effect. Duration receives no special extension from Overdose itself.
 | 
Hoarder’s Pocket
 | 5
 | Unlocks a 9-slot personal inventory that persists through death, dimension travel, and Bloom+/skill resets. Nested storage items are blocked.
 | 
Stack Overflow
 | 6
 | Items that are already stackable may stack up to a hard cap of 300. Weapons and armor remain non-stackable.
 | 
Rune Embedding
 | 6
 | Unlocks 5 enchantment-book rune slots: 4 generic body slots and 1 Unarmed slot. Detailed rules below.
 | 
Rainbow
 | 7
 | Requires Crossbow Infinity. Extends Infinity to bows and crossbows for all supported ammunition types, provided the player owns at least one copy of that ammo.
 | 
Become My Fuel
 | 8
 | Drink a negative potion while holding an eligible weapon to permanently imprint one negative effect onto that item. Every valid hit applies it for 10 seconds at the consumed potion’s amplifier.
 | 
Overenchant
 | 10
 | Allows enchantments above vanilla limits up to hard level 8 and uniquely removes the anvil “Too Expensive!” restriction while active.
 | 
Ascension
 | 20
 | True flight with no Elytra/fuel/XP/upkeep cost. Taking damage while flying disables flight until the player reaches the ground again.
 | 
Current Misc Major pool totals 83 Major Levels. This is intentionally far above the player’s lifetime global Major budget, forcing meaningful specialization.
4. Iron Stomach - 2 Major Levels
• Unsafe/problem foods such as raw meat, rotten flesh, spider eyes, poisonous foods, and compatible modded foods may be eaten without their normal negative side effects.
• Eligible foods use a minimum nutrition/saturation floor approximately equal to cooked chicken. Better foods keep their original higher nutrition instead of being downgraded.
• Use tags/configuration for modded food eligibility rather than hard-coding only vanilla items.
5. Selective Cleansing - 2 Major Levels
• Drinking milk removes all negative effects at once but preserves beneficial effects.
• Eating an eligible vegetable performs the same negative-effect cleanse while preserving beneficial effects.
• Apples and berries are explicitly excluded from the vegetable-cleansing rule.
• Vegetable eligibility should be tag/config driven so modded vegetables can participate cleanly.
6. XP Insurance - 3 Major Levels
• When the player dies, preserve/return 50% of the vanilla XP that would normally be lost by death.
• Misc progression is not rolled back; Misc already received its mirrored XP when the original orbs were collected.
7. Second Serving - 4 Major Levels
• The player may eat any food at any hunger level, including while the hunger bar is completely full.
• This exists primarily to enable food-effect, Suspicious Stew, modded-food, and build-combination play rather than as a raw hunger bonus.
8. Water to Wine - 5 Major Levels
• Water to Wine is individually toggleable.
• While active, any newly applied finite-duration status effect is extended to a minimum duration of 10 minutes.
• The rule is symmetrical: beneficial and harmful effects are both extended.
• If Water to Wine is toggled off while an effect that it extended is still active, that effect’s remaining duration is reduced to 2 minutes.
• Only effects actually modified/extended by Water to Wine should be cut to 2 minutes. Naturally long effects that Water to Wine did not modify should not be shortened merely because the Major is toggled off.
• Instant effects and permanent/infinite effects are unaffected.
• Reapplication/refresh while Water to Wine is active should again enforce the 10-minute minimum when applicable.
9. Overdose - 5 Major Levels
• Repeated application of the same status effect can raise that effect’s amplifier instead of merely reapplying the same level.
• The boosted effect may climb up to +2 amplifier levels above the original source effect.
• Example: a source Speed I effect may be pushed to Speed II and then Speed III, but not beyond Speed III through that source.
• Overdose does not grant special duration preservation. Duration follows the normal incoming/reapplied effect behavior unless another Major such as Water to Wine modifies it.
10. Hoarder’s Pocket - 5 Major Levels
• Unlocks a 9-slot personal inventory associated with the player.
• Contents persist through death, dimension travel, and Bloom+/skill resets.
• Nested storage items such as shulker boxes, bundles, backpacks, or equivalent container items are not allowed inside.
• Exact access UX (button, keybind, Skill Hub integration, etc.) can be chosen during UI implementation as long as the inventory remains persistent and player-bound.
11. Stack Overflow - 6 Major Levels
• Items that are already stackable may stack up to a hard maximum of 300 per stack.
• This includes items with lower normal stack caps such as 16, provided they are fundamentally stackable.
• Weapons and armor remain non-stackable.
• Implementation must preserve correct behavior across player inventories, containers, crafting, dropping/pickup, and networking. Treat this as a true mod-level inventory rule rather than a cosmetic count.
12. Rune Embedding - 6 Major Levels
• Acquiring Rune Embedding unlocks 5 enchantment-book slots on the player.
• Slots 1-4 are generic body-enchantment slots. They are not mapped to helmet/chest/leggings/boots.
• Body/armor rune effects function only while all four armor slots are empty. They do not stack with or supplement worn armor enchantments.
• Slot 5 is reserved for Unarmed and only functions while the player is bare-handed.
• The Unarmed slot is limited to enchantments normally valid for a sword.
• Embedded books are not consumed and may be freely removed/swapped.
• Duplicate enchantments are blocked entirely. To replace an enchantment with another copy/level, remove or swap the existing rune first.
• Curses are not valid rune enchantments.
• A configurable allowlist/tag should control compatible modded enchantments. Effects that only make sense on item durability may require explicit compatibility decisions.
Rune Embedding intentionally creates strong synergy with armorless builds such as Titan Hide without allowing armor-enchantment stacking on top of equipped armor.
13. Rainbow - 7 Major Levels
• Prerequisite: the existing Combat Major Crossbow Infinity.
• Extends Infinity behavior to both bows and crossbows.
• All supported ammunition types may benefit, including tipped/spectral/special arrows and compatible modded ammunition through tags/config.
• The player must still possess at least one copy of the ammunition type being fired.
• Rainbow does not remove the need for the source Infinity Major; it upgrades/generalizes that behavior.
14. Become My Fuel - 8 Major Levels
• Drink an eligible potion that applies a negative status effect while holding an eligible weapon.
• The held weapon permanently stores one negative status effect as item data.
• A weapon may hold only one stored negative effect at a time. Imprinting another eligible effect overwrites the existing one.
• Every valid hit with that weapon applies the stored effect for 10 seconds.
• The applied effect uses the same amplifier as the potion the player drank during imprinting.
• Instant effects such as Instant Damage/Harming are not eligible.
• The player still suffers the consumed negative potion normally when performing the imprint.
• The imbued effect belongs to the weapon item itself and survives death, dropping, trading, and being picked up/used by another player.
15. Overenchant - 10 Major Levels
• Allows enchantments to exceed their normal vanilla maximum through normal combining/upgrading logic.
• Hard maximum enchantment level is 8.
• While Overenchant is active, the anvil “Too Expensive!” restriction is removed for eligible operations.
• Without Overenchant, normal vanilla enchantment caps and “Too Expensive!” behavior remain in force.
16. Ascension - 20 Major Levels
• Grants true controlled flight without Elytra.
• No fuel, hunger, XP, durability, or other upkeep cost is required.
• Flight can be used as the Major’s active behavior; exact keybind/toggle UX may be chosen during implementation.
• If the player takes damage while flying, Ascension flight is immediately disabled.
• Flight remains unavailable until the player touches/reaches the ground again. After grounding, Ascension may be used normally again.
Ascension deliberately costs 20 Major Levels because true flight is a build-defining rule break that consumes a very large portion of the shared lifetime Major economy.
17. Major-system behavior inherited from the core framework
• Misc Majors spend global Major Levels, not Misc-only Major currency.
• Majors can be refunded/respecced under the system’s normal free-respec philosophy.
• Majors are individually toggleable where a toggle is meaningful; Water to Wine has explicit special toggle behavior.
• Prerequisites should use the system’s normal hidden-prerequisite UI state. Rainbow has the known prerequisite Crossbow Infinity.
• Conflicts should only be added where mechanics or controls genuinely cannot coexist.
• Modded items/effects/ammunition/foods should be integrated through tags/config/API hooks rather than vanilla-only IDs.
18. Current open implementation details
• 1. Choose the exact XP curve that makes Misc the second-easiest category to reach 100.
• 2. Finalize the exact linear/interpolation formulas for Speed and Endurance if implementation testing suggests adjustments.
• 3. Define exact default tags/allowlists for unsafe foods, vegetables, Rune-compatible enchantments, and Rainbow-compatible ammunition.
• 4. Choose Hoarder’s Pocket access UX.
• 5. Define robust Stack Overflow behavior for every relevant vanilla/modded container edge case.
• 6. Define how two held weapons are disambiguated for Become My Fuel if both hands contain eligible weapons.
• 7. Confirm any special interaction rules between Water to Wine, Overdose, Unyielding, Selective Cleansing, and modded status-effect systems during cross-system testing.
Current status: Misc has a complete progression identity, three base stats, and a priced 83-point Major pool. It is ready to be represented in the Skill Hub and implemented later in controlled chunks.
