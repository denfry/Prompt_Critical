---
name: pc-build
description: Build, unit-test and GameTest Prompt Critical with Gradle and read the result cheaply. Use for "собери", "build", "тесты", "gametest", compile, ArchUnit or translation failures, and before claiming a change works.
---

# Build and test

Always `run_in_background: true`. The full log goes to a file and only the summary comes back.

```bash
cd /d/Projects/Prompt_Critical && ./gradlew build --console=plain > "$TEMP/pc-build.log" 2>&1; \
grep -E "error:|FAILED|BUILD (SUCCESSFUL|FAILED)|Architecture Violation|checkTranslations" "$TEMP/pc-build.log" | head -40
```

Faster loops, pick the narrowest that proves the change:

- Sim change: `./gradlew :pc-sim:test --tests "dev.denfry.promptcritical.sim.<pkg>.<Class>" -q`.
- Mod compile only: `./gradlew :pc-mod:compileJava -q`.
- GameTests:
  ```bash
  ./gradlew :pc-mod:runGameTestServer --console=plain > "$TEMP/pc-gt.log" 2>&1; \
  grep -E "required tests|failed|Exception|BUILD (SUCCESSFUL|FAILED)" "$TEMP/pc-gt.log" | head -40
  ```
- Run the full `build` once at the end, not after every edit.

## Reading failures

- Unit test details: `grep -l "<failure" */build/test-results/test/*.xml`, then grep that file for `<failure`
  with `-A8`. Don't open the HTML reports.
- ArchUnit violation: the message names the class and the rule (Minecraft import in `pc-sim`, `Math.random`,
  `System.currentTimeMillis`, `new Thread`). Fix the code, never the rule.
- `checkTranslations`: add the missing en/ru text in the provider or handbook lang file, then rerun.
- Vanilla/NeoForge API questions: grep the sources jar (see `.claude/rules/mod.md`). Don't guess or web-search.

Report the build as green only if this turn's log says `BUILD SUCCESSFUL`.
