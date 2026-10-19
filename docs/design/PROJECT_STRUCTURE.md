Universal Skill System
Project / File Structure for Claude

*Suggested Fabric architecture • structure first, mechanics stay modular*

**Purpose. **This sheet is not a second design document for individual skills. It tells Claude how to lay the mod out so the four skill tabs, Major system, UI hub, persistence, compatibility, future effects, and future art do not end up tangled together. The exact mod name/package can be changed later.

**Target assumption. **Fabric project. Use the current Fabric template/Gradle-Loom setup for the exact Minecraft target version; the folders below describe our desired architecture rather than locking dependency versions.

# 1. Architecture Principles

**Server/common owns truth: **Levels, XP, purchases, stat math, Major ownership, cooldown/state, and persistent data must be authoritative outside the client UI.

**Client owns presentation: **The hub screen, icons, hover descriptions, animations, particles, glow/orbit effects, and textures should be client-side presentation fed by synchronized state.

**Four tabs, shared framework: **Utility, Combat, Armor, and Misc should use the same core interfaces/registries where possible, but each tab keeps its own XP rules and mechanics.

**Majors are modular: **Avoid one giant switch statement. Each complex Major should have an isolated implementation/handler so it can be tested, toggled, replaced, or disabled without touching unrelated skills.

**Data/config first for classifications: **Weapon classes, armor classes, food categories, damage categories, modded compatibility, costs, icons, prerequisites, conflicts, and simple metadata should be data/tag/config driven whenever practical.

**Leave visual hooks open: **A Major may have an icon today and later gain particles, sounds, an overlay, animation, shader/post effect, or custom texture without rewriting its progression logic.

# 2. Suggested Repository Tree

UniversalSkillSystem/

├─ README.md

├─ docs/

│  ├─ GENERAL_CONCEPT.md

│  ├─ SKILL_RULES.md

│  └─ COMPATIBILITY.md

├─ config_examples/

├─ tools/

├─ src/

│  ├─ main/

│  │  ├─ java/com/.../universalskills/

│  │  │  ├─ UniversalSkillsMod.java

│  │  │  ├─ api/

│  │  │  ├─ config/

│  │  │  ├─ data/

│  │  │  ├─ registry/

│  │  │  ├─ network/

│  │  │  ├─ player/

│  │  │  ├─ progression/

│  │  │  ├─ stats/

│  │  │  ├─ skill/

│  │  │  │  ├─ utility/

│  │  │  │  ├─ combat/

│  │  │  │  ├─ armor/

│  │  │  │  └─ misc/

│  │  │  ├─ major/

│  │  │  │  ├─ utility/

│  │  │  │  ├─ combat/

│  │  │  │  ├─ armor/

│  │  │  │  └─ misc/

│  │  │  ├─ effect/

│  │  │  ├─ compat/

│  │  │  │  ├─ pets/

│  │  │  │  └─ modded/

│  │  │  └─ util/

│  │  └─ resources/

│  │     ├─ fabric.mod.json

│  │     ├─ assets/<modid>/

│  │     │  ├─ lang/

│  │     │  ├─ textures/

│  │     │  │  ├─ gui/hub/

│  │     │  │  ├─ icon/skill/

│  │     │  │  ├─ icon/major/

│  │     │  │  ├─ icon/status/

│  │     │  │  ├─ effect/

│  │     │  │  ├─ particle/

│  │     │  │  └─ entity/

│  │     │  ├─ models/

│  │     │  ├─ sounds/

│  │     │  ├─ particles/

│  │     │  └─ shaders/

│  │     └─ data/<modid>/

│  │        ├─ skill/

│  │        ├─ major/{utility,combat,armor,misc}/

│  │        ├─ tag/item/{weapon_class,armor_class,food}/

│  │        ├─ tag/damage_type/

│  │        └─ compat/

│  ├─ client/

│  │  └─ java/com/.../universalskills/client/

│  │     ├─ UniversalSkillsClient.java

│  │     ├─ gui/hub/

│  │     ├─ gui/widget/

│  │     ├─ render/

│  │     └─ effect/

│  └─ test/

│     └─ java/com/.../universalskills/

└─ (Gradle/Loom files from the Fabric template)

**Note: **Use the exact resource/data-pack directory naming required by the target Minecraft version. The custom folders such as data/<modid>/major are our own loaders and can remain stable even if vanilla data-pack folder names change.

# 3. Java Package Responsibilities

| Area | Responsibility |
| --- | --- |
| player/ | Persistent per-player state: four levels, XP, 100-point allocations, Major ownership/spent count, toggles, one-time Major rewards, prestige/reset flags, Rune Embedding slots, Last Breath ladder, etc. |
| progression/ | Central XP routing and level-up rules. Handles activity XP, mirrored Misc orb XP, manual vanilla-XP investment before 100, Major Level grants, reset/Bloom+ rules, and post-100 investment for tabs that have it. |
| stats/ | All reusable formulas: Utility stats, Combat stats, Armor stats, Misc stats, diminishing Dodge, over-cap armor mitigation, multiplicative reductions, durability backup checks, etc. |
| skill/<tab>/ | Tab-specific XP sources and mechanics. Example: Combat damage/kill XP, Armor damage/block/dodge XP, Misc orb collection. |
| major/<tab>/ | Runtime behavior for Major skills. Prefer one class/handler per complex Major or small closely-related family. |
| registry/ | Registers skill categories, stat types, Major ids, packets, custom effects/components if needed, and data reload listeners. |
| data/ | Parses JSON metadata/definitions and validates ids, costs, prerequisites, conflicts, icon/effect references, compatibility lists. |
| network/ | Client/server requests and synchronization: buy/refund Major, toggle Major, allocate stat, reset, open/sync hub, Rune socket swaps, etc. |
| compat/ | Optional integration layer. Item/entity/damage classification should live here rather than being scattered through gameplay code. |
| api/ | Small public hooks for modpack/addon authors: query level/stat/Major state, award XP, register compatibility, react to Major events. |
| effect/ | Gameplay-side effect helpers/events that are not tied to rendering. Keep visual particle/render code out of common/server code. |

# 4. Hub / UI Structure

**The hub should be a client screen backed by synchronized player state, not the place where gameplay math lives. **The same screen framework should render four category pages while each category supplies its own stat icons and Major icon layout.

**HubScreen: **Top-level navigation between Utility, Combat, Armor, and Misc; shows current category level/XP and available/spent Major Levels.

**StatIconWidget: **A levelable stat icon. Handles hover text, current points, add/remove controls, disabled state, and future animation/texture hooks.

**MajorIconWidget: **A purchasable Major icon. Handles cost, generic prerequisite lock, conflict prompt, ownership, toggle state, and active glow/orbit visual.

**Tooltip/Info panel: **Uses translation/data keys instead of hardcoded paragraphs inside screen classes.

**Effect layer: **Optional layer for particles, icon pulses, background effects, shaders, or category-specific themes later.

# 5. Data-Driven Major / Skill Metadata

The mechanics do not all need to be JSON. The useful split is: metadata in data files, complex behavior in Java. That lets costs/icons/prerequisites/conflicts change without rewriting a screen or registry.

{

  "id": "universalskills:example_major",

  "category": "misc",

  "cost": 2,

  "icon": "universalskills:textures/icon/major/example_major.png",

  "toggleable": true,

  "prerequisites": [],

  "conflicts": [],

  "effect_asset": null

}

**Possible metadata fields later: **display-name key, description key, icon, cost, category, prerequisite ids, conflict ids, toggleable, hidden/visible state, sort/layout coordinates, visual-effect id, sound id, tags, config key, and implementation id.

# 6. Leave Room for Textures, Effects, Sounds, and Particles

**textures/icon/skill/ **Base stat/skill icons used by the hub.

**textures/icon/major/ **Major icons. Each Major should reference an asset id rather than assuming a hardcoded file path.

**textures/icon/status/ **Small temporary/state indicators if we later decide to expose them.

**textures/gui/hub/ **Backgrounds, frames, category tabs, bars, sockets, connectors, hover states.

**textures/effect/ + client/effect/ **Future full-screen/character overlays, glows, aura layers, hit effects, and ability-specific visuals.

**particles/ **JSON particle definitions plus matching textures. Majors can reference optional particle ids.

**sounds/ **Acquisition, toggle, level-up, proc, cooldown, flight, rune, and other future sounds.

**shaders/ **Optional only. Keep hooks available but do not require shaders for core mechanics.

**textures/entity/ **Reserved for the separate future Pet system or any visible summoned/companion content.

# 7. Player State and Persistence

Use one versioned player-state model that can be serialized/deserialized and migrated. Do not store the only copy of progression in GUI classes or transient effects.

Per-category level and current XP.

Natural stat allocations and permanent post-100 purchased points where applicable.

Available and spent global Major Levels; one-time natural Major grants must not be farmable through resets.

Owned Major ids, enabled/disabled state, selected Copycat source, conflicts, and prerequisites.

Permanent unlock flags (for example, whether a category ever reached 100 / unlocked its investment tab).

Prestige/Bloom+ state and bonus Major rewards.

Persistent Major-specific data: Protein count, Rune books, Hoarder pocket inventory, Last Breath window/tier, Castle of Stone recharge, weapon-imprinted effects, etc.

A data version integer so later schema changes can migrate old saves.

# 8. Compatibility / Modpack Hooks

The mod should assume other mods add weapons, armor, foods, mobs, damage types, enchantments, ammo, crops, and pets. Compatibility must be additive instead of requiring a source-code patch for every mod.

Weapon classification tags/config: sword, axe, bow, crossbow, trident, mace, blunt, blade, etc.

Armor classification tags/config: Light, Medium, Heavy.

Food tags/config: meat, vegetable, unsafe/poisonous foods, special exclusions.

Damage classification tags/config: elemental, blade, blunt, projectile, environmental, etc.

Entity XP multiplier config for bosses/modded mobs.

Ammo compatibility for Rainbow and ranged mechanics.

Enchant allow/block lists for Rune Embedding, Overenchant, and modded enchantments.

Optional addon/API hooks instead of hard dependencies where possible.

# 9. Networking Boundary

Anything that changes progression or gameplay must be validated server-side. The client can request an action; it should not be trusted to award itself levels, stats, or Majors.

Client → server: buy/refund/toggle Major, allocate/refund stat points, request reset, change Rune books, change selectable Major settings.

Server → client: synchronized category levels/XP, stat allocations, available/spent Major Levels, owned/toggled Majors, cooldown/state values needed for display.

Do not spam full state every tick. Send deltas/events when possible and a full sync on login/respawn/opening the hub when needed.

# 10. Future-Proof Folders / Hooks

**Pets: **Keep a separate future system. Only provide bridge interfaces/events now (for example One and the Same) instead of forcing Pets into the four-tab level structure.

**Hidden titles/passives: **Reserve registry/data space for behavior counters and unlockable titles later, but keep them separate from the core build system.

**Visual effect packs: **Allow a Major to reference optional effect/sound/particle ids so art can be added after mechanics are stable.

**Addons: **Keep a small API surface so another mod/datapack can add classifications, award XP, or query skill state without editing the core mod.

# 11. What Claude Should Avoid

Do not port the old plugin as one-to-one command/event classes and call that the mod architecture.

Do not hardcode every vanilla item id directly inside Major logic.

Do not put all Major behavior in one manager/switch statement.

Do not let GUI code become the authoritative source of progression state.

Do not make textures or custom particles mandatory for a mechanic to function; visuals should be optional presentation.

Do not bake final numbers into screen code. Screens should read the same definitions/config used by gameplay logic.

Do not make future Pets depend on the internal implementation details of Combat/Armor; bridge through interfaces/events.

# 12. Recommended Build Order

Create current Fabric template project and common/client entrypoints.

Implement registries/identifiers, config/data reload layer, and versioned player state.

Implement the shared 1–100 progression, stat allocation, global Major Level economy, ownership/toggle/conflict/prerequisite rules, and reset rules.

Implement networking + a plain functional four-tab hub with placeholder icons.

Implement one simple skill/stat and one simple Major end-to-end as an architecture test.

Add Utility, Combat, Armor, and Misc mechanics in isolated modules.

Add compatibility tags/config and test modded item/entity classifications.

Only after mechanics are stable, replace placeholder icons and add optional particles/sounds/effects/animations.

**Deliverable note. **A matching folder skeleton ZIP is provided with this sheet. It is intentionally not compile-ready because Gradle/Loom/Minecraft/Fabric versions should come from the current Fabric project template rather than being frozen into this architecture document.

Universal Skill System • Suggested project structure