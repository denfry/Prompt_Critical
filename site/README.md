# Project site

Static site published to <https://denfry.github.io/Prompt_Critical/> by `.github/workflows/pages.yml`.

| Path | What |
|---|---|
| `src/page.html` | Single template for both languages; `{{key}}` comes from `src/i18n/{en,ru}.json` |
| `src/assets/app.js` | Charts and the in-browser reactor desk (U-235 point kinetics, constants from `pc-sim`) |
| `data/*.json` | Real `pc-sim` runs: `./gradlew :pc-sim:siteData` |
| `og/` | Share image source; `python site/og/render.py` writes `src/assets/og.png` |

```bash
node site/build.mjs                      # writes site/dist (EN at /, RU at /ru/)
cd site && python -m http.server 8000    # then open http://localhost:8000/dist/
```

Fonts: Oswald and JetBrains Mono, SIL Open Font License 1.1, subset to Latin and Cyrillic.
