# Bloom Level-Up — notes for Claude

## This branch: `forge-1.20.1`

- A separate build of Bloom for **Forge 1.20.1** (Forge 47.x, ForgeGradle 6, Gradle 8.8, Java 17, official mappings). `main` stays the Fabric 26.3 mod: never merge this branch into main or the other way round; port changes across by hand.
- CI (`.github/workflows/build.yml` on this branch) runs `./gradlew build runGameTestServer`; same annotation trick as below for errors. The jar players install is the un-classified one in `build/libs` (MixinExtras inside, reobfuscated); `-slim` is the bare one.
- Ported in chunks. Files not ported yet stay where they are (unchanged 26.3 code, for reference) and are excluded in `build.gradle` (`notPortedYet`); a chunk ports its files and deletes their lines there.
- Forge stand-ins: profile = capability (`player/BloomAttachments`), packets = one `SimpleChannel` (`network/BloomNetwork`, client handlers in `client/ClientPackets`), data reload = `AddReloadListenerEvent`, attribute modifiers keyed by UUIDs made from Bloom ids (`StatEffects.ModifierId`). Data folders use 1.20.1 names (`tags/items`, `tags/blocks`, `tags/entity_types`, `loot_tables`, `structures`).
- Game tests: `src/gametest/java/**/gametest/forge/` (`@GameTestHolder`, template `bloomlevelup:empty`); the Fabric ones beside them are ported with their chunks.
- Client: drawing goes through `client/gui/BloomGraphics` (26.3-style calls on 1.20.1's `GuiGraphics`), so GUI code stays close to main's. Client mixins are in `bloomlevelup.forge.client.mixins.json`.
- Better Combat (1.9.0+1.20.1, optional): `compat/BetterCombat*` and `client/compat/BetterCombatClient*`; no mixins into Better Combat (its swing is recognised by its attack damage modifiers). `-PwithBetterCombat` adds it, Player Animator and Cloth Config to the dev runs; CI's `build` and `client-test` jobs run once without and once with it (matrix `variant`).
- Forge pack: `pack/` is **LegionWareX** (FreshOrangeSlices' medieval pack around Bloom; pack.json in cumulative `layers`, build_pack.py `--layers N`, boot_server.py, launch_test.py). CI's `pack` job (matrix `layers` 1-4) builds each layer, boots its server zip, starts a real Forge client (portablemc) to the title screen and into the server; `pack-screenshots` prints what it saw (`shot-L<n>_bloom_pack_*`). The full pack is the `legionwarex` artifact. Mod versions are pinned in `pack/pack.lock.json` (the last tested); `--update` shows newer ones, and the lock only moves after a test. Keep Bloom standalone: pack support goes in as optional tag entries or the pack's own config, never Bloom's defaults. Resource packs are switched on by `client/mixin/OptionsMixin` (Forge reads options before mods load).
- CI's `client-test` job boots a real client on a virtual display (`gametest/forge/client/ClientSmokeTest`, on when `BLOOM_CLIENT_TEST` is set) and screenshots the hub; the `screenshots` jobs print each picture as annotations (`shot-<name>-NN`, base64 JPEG) to rebuild from the checks API.

## Shared notes (written for main)

- Fabric mod, MC 26.3, Java 25, Mojang official names (no mappings). Versions in `gradle.properties` come from the official Fabric template.
- The sandbox cannot reach Fabric/Mojang maven, so `./gradlew build` only runs in GitHub Actions. Verify by pushing and reading the run. Job logs are not reachable from the sandbox; on failure the build workflow attaches compile/test errors as annotations (`build-errors-NN`), read via `GET /repos/FreshOrangeSlices/BloomLevel-Up/check-runs/<job id>/annotations`.
- 26.3 renamed much of the client API (`GuiGraphicsExtractor`, `extractRenderState`, `setScreenAndShow`, ...). Look up exact signatures rather than guessing: the javap API-dump tool was removed from the tree but lives in git history (`git show c987f4a:tools/api-dump/README.md`); run it from a throwaway `ci/api-dump*` branch, never main. Fabric API's own source (branch `26.3`) is also a good reference.
- The pure-Java core (`definition/`, `config/BloomConfig`, `player/{BloomProfile,CategoryProgress,OwnedMajor}`, `progression/`, `stats/`) must not import Minecraft classes, so it can be compiled and unit-tested locally with plain `javac` (Java 21 locally: avoid Java 22+ syntax there).
- Design docs in `docs/design/` are the source of truth; record any choice the docs leave open in `docs/DECISIONS.md`.
- Server is authoritative; client only renders synced state. All profile changes go through `ProgressionService`.
- New game hooks are registered from `registry/BloomRegistries`.
- The user works in chunks: finish one chunk, push, report, then wait.
