# Арт: модели и текстуры

Модели и текстуры делаются вручную в Blockbench: скрипт `art/blockbench/<категория>/<id>.author.js` строит
модель и рисует текстуры на общем наборе `art/blockbench/lib/pcart.js` (материалы, заклёпки, надписи) и
`pcround.js` (круглые детали) и сохраняет `.bbmodel` и Java-модель. Плотность — 32 px на блок, цвета
трубопроводов и сред — по ГОСТ 14202, движущиеся детали — отдельные partial-модели. Генератор моделей
(`art/tools/hq*.py`, `build.py`) больше не используется; старые плейсхолдеры заменяются по мере перерисовки.

## Цельные модели машин в реальном масштабе

Машина в реальном размере (1 блок ≈ 1 м) рисуется одной моделью любого размера и режется при сохранении
на куски 3×3×3 блока, каждый в диапазоне Java-модели (−16…32 px от среднего блока куска):

```
eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcslice.js', 'utf8'))
PCSLICE.saveWhole('<repo>', '<категория>', '<id>')   // models/block/<id>/chunk/*.json + wholemodels/<id>.json
node art/tools/pcslice_test.js                        // проверки нарезки без Blockbench
```

Контроллер машины рисует куски со смещениями из манифеста (`pc-mod` `client/whole`).

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

## HQ-модели

Модули `tools/hq_<область>.py` на `tools/hqkit.py` подменяют плейсхолдеры с тем же id.
Отличия от плейсхолдеров:

- у модели свой атлас `textures/block/hq/<id>.png`, грани разложены 1 тексель = 1 единица
  модели, без растянутых пикселей;
- рисуем «материал + уровень» по рампам из семи цветов (`hqkit.Sheet`): стыки листов,
  заклёпки, шильдики, шкалы, потёки — функциями по листу размером с грань;
- блик и тень ставятся только на открытых выпуклых рёбрах, в стыках деталей запекается
  затенение (AO); грани, утопленные в соседних деталях, в модель не попадают;
- у цилиндров общая развёртка (`cyl().wrap`) и торцы (`cap0`/`cap1`); `crisp` кладёт рисунок
  торца в сетку пикселей;
- `Model(..., cullface=True)` для моделей в один блок: грани на границе блока и детали за
  ней прячутся у глухого соседа.

```
python art/tools/hqview.py . <out.png> [id ...] [--scale N] [--atlas]   # просмотр без Blockbench, свет граней как в игре
python art/tools/build.py . <tmp>/spec.json --hq-only                   # только HQ-атласы и spec из HQ-моделей
# дальше Blockbench: pcRun('<repo>', '<tmp>/spec.json'); pcFlush()
```

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

Текстуры из `art/hand_textures.json` нарисованы вручную (`art/blockbench/decor/surfaces.author.js`) поверх
путей генератора: `build.py` по-прежнему вычисляет их функции (общий шум остальных текстур не сдвигается), но
файлы не перезаписывает; `--only-check` проверяет, что они есть на диске.

`build.py --only-check` проверяет ограничения Java-моделей (−16…32, углы ±22,5/±45,
одна ось), дубли ID и ссылки на текстуры. После ручной доработки модели в
Blockbench источником правды становится её `.bbmodel`: повторный прогон
генератора перезапишет и её, и PNG.
