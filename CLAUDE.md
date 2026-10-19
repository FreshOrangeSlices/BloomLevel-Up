# Bloom Level-Up — notes for Claude

- Fabric mod, MC 26.3, Java 25, Mojang official names (no mappings). Versions in `gradle.properties` come from the official Fabric template.
- The sandbox cannot reach Fabric/Mojang maven, so `./gradlew build` only runs in GitHub Actions. Verify by pushing and reading the run. Job logs are not reachable from the sandbox; on failure the build workflow attaches compile/test errors as annotations (`build-errors-NN`), read via `GET /repos/FreshOrangeSlices/BloomLevel-Up/check-runs/<job id>/annotations`.
- 26.3 renamed much of the client API (`GuiGraphicsExtractor`, `extractRenderState`, `setScreenAndShow`, ...). Look up exact signatures rather than guessing: the javap API-dump tool was removed from the tree but lives in git history (`git show c987f4a:tools/api-dump/README.md`); run it from a throwaway `ci/api-dump*` branch, never main. Fabric API's own source (branch `26.3`) is also a good reference.
- The pure-Java core (`definition/`, `config/BloomConfig`, `player/{BloomProfile,CategoryProgress,OwnedMajor}`, `progression/`, `stats/`) must not import Minecraft classes, so it can be compiled and unit-tested locally with plain `javac` (Java 21 locally: avoid Java 22+ syntax there).
- Design docs in `docs/design/` are the source of truth; record any choice the docs leave open in `docs/DECISIONS.md`.
- Server is authoritative; client only renders synced state. All profile changes go through `ProgressionService`.
- New game hooks are registered from `registry/BloomRegistries`.
- The user works in chunks: finish one chunk, push, report, then wait.
