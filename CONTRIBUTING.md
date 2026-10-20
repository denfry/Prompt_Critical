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

## Translations

English is the source language. Russian is maintained by the author. All
other languages come as pull requests from players, and any language is
welcome.

### Before you start

Open a [Translation issue](https://github.com/denfry/Prompt_Critical/issues/new?template=translation.yml)
with the language you want to take, so nobody else translates the same files.
Check `./gradlew :pc-mod:translationStatus` to see which languages already
exist and how complete they are.

### Files

Use the lowercase Minecraft locale code in file and folder names: `de_de`,
`fr_fr`, `pt_br`, `zh_cn`, `uk_ua`. Copy the English file, keep every key and
translate only the values.

| What | English source | Your translation |
|---|---|---|
| Blocks, items, fluids, GUI, advancements, chat | `pc-mod/src/generated/resources/assets/promptcritical/lang/en_us.json` | `pc-mod/src/main/resources/assets/promptcritical/lang/<locale>.json` |
| Handbook titles, captions, hints, route | `pc-mod/src/main/resources/data/promptcritical/handbook/lang/en_us.json` | `pc-mod/src/main/resources/data/promptcritical/handbook/lang/<locale>.json` |
| Handbook chapter text | `pc-mod/src/main/resources/data/promptcritical/handbook/pages/en_us/<chapter>/<file>.md` | `pc-mod/src/main/resources/data/promptcritical/handbook/pages/<locale>/<chapter>/<file>.md` |

Note the first row: the English file lives under `src/generated`, your file
goes under `src/main`. The generated folder is rewritten by datagen, so a
translation placed there would be lost.

You do not have to translate everything at once. Anything missing falls back
to English, so a partial translation is fine to merge.

### Rules

- Keep placeholders exactly as they are: `%s`, `%d`, `%1$s`, `%%`. Their
  order may change only with numbered forms like `%1$s`.
- In handbook pages, translate the text of links but keep their targets:
  in `[run-of-mine coal](item:promptcritical:raw_coal)` translate
  `run-of-mine coal` and leave `(item:promptcritical:raw_coal)` alone. The
  same goes for chapter links `(promptcritical:i0/boiler)`, formulas and
  images.
- Keep units and symbols in SI as in the English text (MPa, kW, °C). Use
  the engineering terms of your language that a plant operator would use; if
  unsure, ask in the issue.
- Do not edit `en_us` or `ru_ru` files in a translation pull request. If the
  English text is wrong, say so in the issue.

### Check before sending

```bash
./gradlew :pc-mod:checkTranslations   # unknown keys, placeholders, page links
./gradlew :pc-mod:translationStatus   # how complete each language is
```

`checkTranslations` also runs in CI as part of `./gradlew build`. To see the
translation in game, run `./gradlew :pc-mod:runClient` and switch the language
in the Minecraft settings.

When new strings appear in English, the status report shows the gap; a
follow-up pull request that fills it is always welcome.

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
