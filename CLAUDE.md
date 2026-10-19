# Bloom Level-Up — notes for Claude

- Fabric mod, MC 26.3, Java 25, Mojang official names (no mappings). Versions in `gradle.properties` come from the official Fabric template.
- The sandbox cannot reach Fabric/Mojang maven, so `./gradlew build` only runs in GitHub Actions. Verify by pushing and reading the Actions run.
- The pure-Java core (`definition/`, `config/BloomConfig`, `player/{BloomProfile,CategoryProgress,OwnedMajor}`, `progression/`, `stats/`) must not import Minecraft classes, so it can be compiled and unit-tested locally with plain `javac` (Java 21 locally: avoid Java 22+ syntax there).
- Design docs in `docs/design/` are the source of truth; record any choice the docs leave open in `docs/DECISIONS.md`.
- Server is authoritative; client only renders synced state. All profile changes go through `ProgressionService`.
- New game hooks are registered from `registry/BloomRegistries`.
- The user works in chunks: finish one chunk, push, report, then wait.
