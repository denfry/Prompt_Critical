---
paths:
  - "pc-mod/**"
---

# pc-mod (NeoForge 1.21.1 + Create)

- Don't guess vanilla or NeoForge signatures and don't web-search them. Grep the merged sources jar:
  ```bash
  J=$(ls pc-mod/build/moddev/artifacts/neoforge-*-sources.jar | head -1)
  unzip -l "$J" | grep -i "BlockEntity.java"                        # find the class
  unzip -p "$J" net/minecraft/world/level/block/entity/BlockEntity.java | grep -n "saveAdditional"
  ```
  Create jars (binary only, no sources) are in `~/.gradle/caches/modules-2/files-2.1/com.simibubi.create/`:
  `unzip -l` lists classes. For behaviour, read how existing `pc-mod` code already uses the Create API first.
- `src/generated/` is datagen output. Change the provider in `mod/data/` (`PcLanguageProvider`,
  `PcBlockStateProvider`, ...), run `./gradlew :pc-mod:runData`, and commit the regenerated files with the change.
- Handbook texts are hand-written in `src/main/resources/data/promptcritical/handbook/` (entries + `lang/{en_us,ru_ru}.json`).
  Formula pages: `src/main/formulas/<name>.tex` → `renderHandbookFormulas`.
- Client-only classes live under `mod/client/`. Common code never references them (the dedicated server must load).
- Behaviour changes get a GameTest in `src/gametest/java/.../gametest/`.
