---
paths:
  - "art/**"
  - "site/**"
---

# Art and site

- The art pipeline is documented in `art/README.md`. Read the section you need, not the whole file. Rules: GDD
  `00 §0.12` (`pc-gdd`).
- `art/tools/build.py` regenerates textures, icons and the Blockbench spec. A model that was finished by hand in
  Blockbench is owned by its `.bbmodel`: rerunning the generator over it overwrites the hand work. Check before you run.
- `python art/tools/build.py . <tmp>/spec.json --only-check` validates model limits, ID duplicates and texture refs.
  Run it after any art change.
- Blockbench batches go through the blockbench MCP (`pcRun`/`pcAll` from `art/tools/blockbench_build.js`) in
  batches of about 20. Previews are PNGs: look at a sheet in `art/preview/sheets/`, not at every single model.
- Site: content in `site/src/` (`i18n/{en,ru}.json`), data from real sim runs (`./gradlew :pc-sim:siteData`),
  build with `node site/build.mjs`. Never hand-edit `site/dist/`.
