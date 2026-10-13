# Contributing to Prompt Critical

The project is in pre-alpha. The most useful contributions right now are
bug reports for the build, design feedback grounded in real engineering, and
small, focused pull requests.

## Before you start

- Read the [wiki](https://github.com/denfry/Prompt_Critical/wiki), especially
  [Overview and Design Pillars](https://github.com/denfry/Prompt_Critical/wiki/Overview-and-Design-Pillars)
  and [Architecture](https://github.com/denfry/Prompt_Critical/wiki/Architecture).
- Names, units and numbers come from the game design document, which is
  private. The wiki summarizes it; ask in an issue if something is unclear.
- For anything bigger than a bug fix, open an issue first so the design can be
  agreed before code is written.

## Development setup

You need JDK 21. Everything else comes through the Gradle wrapper.

```bash
./gradlew build                     # compile + unit tests + ArchUnit rules
./gradlew :pc-mod:runClient         # dev client
./gradlew :pc-mod:runGameTestServer # GameTests
./gradlew :pc-mod:runData           # data generation
```

## Rules for code

- `pc-sim` is plain Java. It must not import `net.minecraft` or NeoForge;
  an ArchUnit test fails the build if it does. Simulation logic goes there,
  Minecraft glue goes into `pc-mod`.
- Physical constants carry a source. Balance values are marked `[BALANCE]`
  and live in the config.
- New behaviour comes with tests: JUnit/jqwik in `pc-sim`, GameTest in
  `pc-mod`. Design sections list validation cases with expected values and
  tolerances; implement against those.
- Stay inside the performance budget (≤ 3 ms/tick p95 for the reference
  complex). Hot paths get a JMH benchmark in `pc-sim`.
- Follow the existing code style; source files are UTF-8.

## Rules for art

- Models are Blockbench Java Block/Item, textures 16×16 (instrument scales
  32×32). Moving parts are separate partial models.
- Pipe and medium colours follow GOST 14202.
- See [Art Pipeline](https://github.com/denfry/Prompt_Critical/wiki/Art-Pipeline)
  and [`art/README.md`](art/README.md).

## Pull requests

- One topic per pull request, with a clear description and a link to the
  issue.
- `./gradlew build` must pass; CI runs the build and GameTests.
- Update the wiki if you change names, mechanics or numbers.

## Licensing of contributions

The project is All Rights Reserved (see [LICENSE](LICENSE)). By submitting a
pull request you confirm that you wrote the contribution or have the right to
submit it, and you grant denfry a perpetual, irrevocable, worldwide,
royalty-free license to use, modify, distribute and relicense it as part of
Prompt Critical.
