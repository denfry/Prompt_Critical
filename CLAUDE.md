# Prompt Critical

Public repo `denfry/Prompt_Critical`. Industrial-complex mod for Minecraft 1.21.1, NeoForge 21.1 (libs.versions),
Java 21, built on Create and CC: Tweaked. Released as 1.0.1: eras I0-I4, reactor tiers T0-T4, plant I&C,
operations and accident layers are in the game. Talk to the user in Russian. This file is the map: use it instead of exploring the repo.

## Map

| Path | What |
|---|---|
| `pc-sim/` | pure deterministic simulation `dev.denfry.promptcritical.sim.*` (thermal, hydraulics, grid, mining, metallurgy, neutronics, protection, ...). No Minecraft. Tests `src/test`, benchmarks `src/jmh` |
| `pc-api/` | tiny public API (2 classes) |
| `pc-mod/` | the NeoForge mod `dev.denfry.promptcritical.mod.*` (binding, block, blockentity, boiler, mine, grid, panel, pa, multiblock, data, client, ...) |
| `pc-mod/src/gametest/` | GameTests (`gametest/`) and film shots (`film/`). Also the source set of `runClient` |
| `pc-mod/src/generated/` | datagen output (lang, models, blockstates, ponder, formula PNGs). Never hand-edit |
| `art/` | `blockbench/` model sources, `tools/` generators, `preview/` PNG renders (25 MB), `voice/`, `film/` |
| `site/` | static project site (`node site/build.mjs`) |
| `build/`, `*/build/`, `pc-mod/run*/` | outputs. Don't search or read them unless the task is about them |

**GDD (design, the authority)** lives outside the repo in `D:/Projects/Prompt_Critical-design` (private git). It is
3.3 MB, 100–360 KB per file. **Never Read a GDD file whole.** Code cites it as `03d §3d.3.1`, `GDD §0.12`,
`§5.2.4`: fetch exactly that section with skill `pc-gdd`. `00-overview §0.13` (reconciliation decisions) overrides
the other sections.

## Commands (repo root, Git Bash `./gradlew`)

| Task | Command |
|---|---|
| Full gate (compile, unit, ArchUnit, `checkTranslations`) | `./gradlew build` (skill `pc-build`) |
| Sim only (fast, no Minecraft) | `./gradlew :pc-sim:test --tests "<Class>"` |
| GameTests (CI runs them too) | `./gradlew :pc-mod:runGameTestServer` |
| Datagen after lang, model or blockstate provider changes | `./gradlew :pc-mod:runData` |
| Dev client / film | `./gradlew :pc-mod:runClient -PquickPlay=<world>` / `:pc-mod:runFilm -PquickPlay=<world>` |
| Translations | `:pc-mod:checkTranslations`, `:pc-mod:translationStatus` |
| Benchmarks | `./gradlew :pc-sim:jmh` |
| Site data / site | `./gradlew :pc-sim:siteData`, `node site/build.mjs` |
| Art | see `art/README.md` (`python art/tools/build.py . <tmp>/spec.json`, `--only-check`) |
| Voice | `python art/voice/generate.py pa` (key `ELEVENLABS_API_KEY` in `.env.local`; responses cached) |

## Token and time rules

1. Don't re-explore: use the map, then `Grep` with a `path`. No `ls -R`, `find .`, `tree`, no Explore/Plan agents
   for lookups that take 1–3 tool calls.
2. GDD only through `pc-gdd`. Big Java classes: Grep the member, then `Read` with `offset`/`limit`.
3. Don't open PNGs in `art/preview/` or `pc-mod/src/generated/` unless you have to judge a visual.
4. Gradle, game runs and art batches → `run_in_background` immediately. No sleep or poll loops.
5. Never dump full Gradle or game logs: log to a file, then grep (see `pc-build`).
6. Edit with small unique anchors. Don't re-read a file after editing it.
7. Final report: short, Russian. What changed, what was checked (command → result), what is still open.

## Definition of done

1. `./gradlew build` is green in this turn. GameTests pass when `pc-mod` behaviour changed.
2. New content has en and ru texts and passes `checkTranslations`. New GDD-specified behaviour cites its `§`.
3. Anything not run is named as "not checked". Never claim "done" or "works" without this turn's evidence.

## Project rules

- Public repo: no secrets, no private GDD text dumps in commits. Keys only in `.env.local` (gitignored).
- Commit only when asked. Message style from `git log`: one plain English sentence that starts with a verb
  ("Add ...", "Fit ...", "Fix ..."), detailed, no conventional-commit prefix.
- `pc-sim` must stay Minecraft-free and deterministic (ArchUnit enforces this; see `.claude/rules/sim.md`).
