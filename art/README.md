# Арт: модели и текстуры

Плейсхолдер-набор для всех блоков, предметов и жидкостей из реестров GDD
(00 §0.7.1, 02 §2.12, 03a, 03b/03c, 04a–04d, 04b §4b.17). Правила — GDD §0.12:
Java Block/Item, текстуры 16×16 (шкалы приборов 32×32), UV per-face, движущиеся
детали — отдельные partial-модели с осью через `[8, 8, 8]`.

| Что | Сколько |
|-----|---------|
| Модели блоков (включая состояния) | 269 |
| Partial-модели (двигает код через Create/Flywheel) | 46 |
| 3D-модель предмета (`fuel_assembly`) | 1 |
| Плоские иконки предметов | 94 |
| Жидкости и газы (still + flow, анимированные) | 35 |
| Слои брони СИЗ (64×32) | 9 |
| Текстур всего | 498 + 70 жидкостных |

## Превью

- `preview/models/<категория>/<id>.png` — рендер каждой модели из Blockbench (прозрачный фон).
- `preview/sheets/models_<категория>.png` — листы моделей по категориям.
- `preview/sheets/textures_<область>.png` — текстуры блоков; `items.png`, `armor.png`, `fluids.png`.

## Где лежит

```
art/blockbench/<категория>/<id>.bbmodel          исходники Blockbench
art/blockbench/<категория>/<id>/<part>.bbmodel   partial-детали
src/main/resources/assets/promptcritical/
  models/block/<id>.json, models/block/<id>/partial/<part>.json
  models/item/<id>.json                           item/generated для иконок
  textures/block/..., textures/item/..., textures/models/armor/...
  textures/block/fluid/<id>_still.png(.mcmeta), <id>_flow.png(.mcmeta)
```

Элементы с префиксом `glow_` получают `neoforge_data: {block_light: 15, sky_light: 15}`.
Цвета трубопроводов и сред — по ГОСТ 14202.

## Пересборка

Генераторы — `tools/`: `pclib.py` (общие помощники и реестры), `tex_<область>.py`
и `spec_<область>.py` (base, mining, fuelcycle, plant).

```
python art/tools/build.py . <tmp>/spec.json            # текстуры, иконки, жидкости, spec + registry.json
# в Blockbench (desktop), консоль или MCP:
#   eval(require('fs').readFileSync('<repo>/art/tools/blockbench_build.js', 'utf8'))
#   pcRun('<repo>', '<tmp>/spec.json', {from: 0, count: 30}); pcFlush()   — повторять пачками
python art/tools/make_previews.py . <tmp>/registry.json
```

`build.py --only-check` проверяет ограничения Java-моделей (−16…32, углы ±22,5/±45,
одна ось), дубли ID и ссылки на текстуры. После ручной доработки модели в
Blockbench источником правды становится её `.bbmodel`: повторный прогон
генератора перезапишет и её, и PNG.
