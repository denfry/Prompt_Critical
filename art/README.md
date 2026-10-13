# Арт: модели и текстуры

Плейсхолдер-набор для всех блоков, предметов и жидкостей из арт-реестров GDD
(00 §0.7.1, 02 §2.12, 03a–03d, 04a–04d, 04b §4b.17, 07–14) на всех вехах — каждая
строка `docs/gdd/art-backlog.md` имеет плейсхолдер. Правила — GDD §0.12:
Java Block/Item, текстуры 16×16 (шкалы приборов 32×32), UV per-face, движущиеся
детали — отдельные partial-модели с осью через `[8, 8, 8]`.

| Что | Сколько |
|-----|---------|
| Модели блоков (включая состояния) | 1091 |
| Partial-модели (двигает код через Create/Flywheel) | 213 |
| 3D-модели предметов | 22 |
| Плоские иконки предметов | 357 |
| Жидкости и газы (still + flow, анимированные) | 126 |
| Слои брони СИЗ (64×32) | 17 |
| Иллюстрации справочника (`textures/gui/handbook/`) | 35 |
| Текстур всего | 1135 + 252 жидкостных |

## Превью

- `preview/models/<категория>/<id>.png` — рендер каждой модели из Blockbench (прозрачный фон).
- `preview/sheets/models_<категория>.png` — листы моделей по категориям.
- `preview/sheets/textures_<область>.png` — текстуры блоков; `items.png`, `armor.png`, `fluids.png`.

## Где лежит

```
art/blockbench/<категория>/<id>.bbmodel          исходники Blockbench
art/blockbench/<категория>/<id>/<part>.bbmodel   partial-детали
pc-mod/src/main/resources/assets/promptcritical/
  models/block/<id>.json, models/block/<id>/partial/<part>.json
  models/item/<id>.json                           item/generated для иконок
  textures/block/..., textures/item/..., textures/models/armor/...
  textures/block/fluid/<id>_still.png(.mcmeta), <id>_flow.png(.mcmeta)
```

Элементы с префиксом `glow_` получают `neoforge_data: {block_light: 15, sky_light: 15}`.
Цвета трубопроводов и сред — по ГОСТ 14202.

## Пересборка

Генераторы — `tools/`: `pclib.py` (общие помощники и реестры), `tex_<область>.py`
и `spec_<область>.py` (base, mining, fuelcycle, plant; automation — 03d, grid — 07,
metallurgy и metitems — 08, content и decor — 11). Новые области берут пул текстур,
`mk` и рисовалки из `artkit.py`; `build.py` грузит исходные четыре области первыми,
чтобы новые модули не сдвигали шум уже готовых текстур.

Вторая волна (вехи после M3) — модули на `artkit2.py` (общие окраски `w2_*`, сосуды,
шкафы, штуцеры, иконки, примитивы схем справочника): `automation2` (03c/03d),
`grid2` (07), `metallurgy2` (08), `content2` (11, иллюстрации и уроки), `oilgas` (09),
`petrochem` (10), `isotopes` (12), `ops` (13, слои брони СИЗ), `environment` (14).
`build.py` грузит их последними (группа после `WAVE1`), поэтому они не трогают шум
волн 1–2; новые модули добавляйте туда же. Проверка `--only-check` ловит и дубли жидкостей.

Модели крупнее блока: по оси размера 2 элементы идут 0…32 от блока-якоря, по оси
размера 3 якорь — средний блок (−16…32). Мультиблоки — 1–3 характерных сегмента
`<id>`, `<id>_<сегмент>`. Состояния — `<id>_<состояние>` только для визуально
различимых вариантов. Руды 08 — подложка `host` + наложение `<id>_overlay`
(модели `<id>`, `<id>_deepslate`, `<id>_tuff`). Декор 11 §11.6.2 — полные блоки и
фигурные элементы; `slab`/`stairs`/`wall` строит datagen из тех же текстур.

```
python art/tools/build.py . <tmp>/spec.json            # текстуры, иконки, жидкости, spec + registry.json
# в Blockbench (desktop), консоль или MCP:
#   eval(require('fs').readFileSync('<repo>/art/tools/blockbench_build.js', 'utf8'))
#   pcRun('<repo>', '<tmp>/spec.json', {from: 0, count: 30}); pcFlush()   — повторять пачками
#   или одним вызовом: await pcAll('<repo>', '<tmp>/spec.json', 0, N)   — пачки по 20; превью — офскрин-вид mcp_offscreen_pc
python art/tools/make_previews.py . <tmp>/registry.json
```

`build.py --only-check` проверяет ограничения Java-моделей (−16…32, углы ±22,5/±45,
одна ось), дубли ID и ссылки на текстуры. После ручной доработки модели в
Blockbench источником правды становится её `.bbmodel`: повторный прогон
генератора перезапишет и её, и PNG.
