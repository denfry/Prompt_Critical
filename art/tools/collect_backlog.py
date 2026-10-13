"""Собирает сводный арт-бэклог из подразделов «Арт-реестр» разделов GDD.

usage: python art/tools/collect_backlog.py <repo_root>

Читает docs/gdd/*.md, находит в каждом файле заголовок со словами
«Арт-реестр», берёт все таблицы с колонкой id в нём и его подразделах (формат GDD 00 §0.12.5)
и пишет docs/gdd/art-backlog.md. Статус строки:
  DONE / MODELED — если указан вручную в art/backlog_status.json;
  PLACEHOLDER    — если есть art/blockbench/**/<id>.bbmodel (или папка partial-деталей <id>/),
                   слой брони models/armor/<id>_layer_1.png, плоская иконка
                   models/item/<id>.json, текстура жидкости <id>_still.png, текстура
                   block/<путь id>.png (для id с «/») или иллюстрация gui/handbook/<id>.png;
  TODO           — иначе.
Строки, где в колонке id перечислено несколько ID или описана группа,
попадают в бэклог одной строкой; статус по ним — по первому ID; группа `prefix_*` —
по любому ID с этим префиксом; строка без ID — по непустой папке категории.
"""
import json
import os
import re
import sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
GDD = os.path.join(ROOT, "docs", "gdd")
BB = os.path.join(ROOT, "art", "blockbench")
STATUS_FILE = os.path.join(ROOT, "art", "backlog_status.json")
OUT = os.path.join(GDD, "art-backlog.md")
ASSETS = os.path.join(ROOT, "pc-mod", "src", "main", "resources", "assets", "promptcritical")

COLUMNS = ["id", "категория", "вид", "габарит", "состояния", "partial",
           "текстуры", "референс", "приоритет", "веха"]


def existing_models():
    ids = set()
    for d, _, files in os.walk(BB):
        for f in files:
            if f.endswith(".bbmodel"):
                ids.add(f[:-len(".bbmodel")])
        # блок, у которого есть только partial-детали (`top_drive/motor`)
        if os.path.relpath(d, BB).count(os.sep) == 1:
            ids.add(os.path.basename(d))
    items = os.path.join(ASSETS, "models", "item")
    if os.path.isdir(items):
        ids.update(f[:-len(".json")] for f in os.listdir(items) if f.endswith(".json"))
    # текстуры без модели (провода BER: `wire/copper` -> textures/block/wire/copper.png)
    tex = os.path.join(ASSETS, "textures", "block")
    for d, _, files in os.walk(tex):
        rel = os.path.relpath(d, tex).replace(os.sep, "/")
        if rel != ".":
            ids.update(f"{rel}/{f[:-len('.png')]}" for f in files if f.endswith(".png"))
    # иллюстрации справочника (GUI)
    gui = os.path.join(ASSETS, "textures", "gui", "handbook")
    if os.path.isdir(gui):
        ids.update(f[:-len(".png")] for f in os.listdir(gui) if f.endswith(".png"))
    # слои брони СИЗ
    armor = os.path.join(ASSETS, "textures", "models", "armor")
    if os.path.isdir(armor):
        ids.update(f[:-len("_layer_1.png")] for f in os.listdir(armor) if f.endswith("_layer_1.png"))
    fluids = os.path.join(ASSETS, "textures", "block", "fluid")
    if os.path.isdir(fluids):
        ids.update(f[:-len("_still.png")] for f in os.listdir(fluids) if f.endswith("_still.png"))
    return ids


def split_row(line):
    # `\|` внутри ячейки — экранированный символ, не граница столбца
    cells = re.split(r"(?<!\\)\|", line.strip().strip("|"))
    return [c.strip() for c in cells]


def art_tables(path):
    """Возвращает (заголовок раздела, [строки-словари]) для всех арт-реестров файла."""
    lines = open(path, encoding="utf-8").read().splitlines()
    result = []
    i = 0
    while i < len(lines):
        if lines[i].startswith("#") and "Арт-реестр" in lines[i]:
            # все таблицы с колонкой id до следующего заголовка того же или
            # более высокого уровня (подразделы «Блоки», «Предметы», ...)
            level = len(lines[i]) - len(lines[i].lstrip("#"))
            j = i + 1
            while j < len(lines):
                line = lines[j]
                if line.startswith("#"):
                    if len(line) - len(line.lstrip("#")) <= level:
                        break
                    j += 1
                    continue
                if not line.lstrip().startswith("|"):
                    j += 1
                    continue
                header = [h.lower() for h in split_row(line)]
                rows = []
                k = j + 2  # пропуск строки-разделителя
                while k < len(lines) and lines[k].lstrip().startswith("|"):
                    rows.append(dict(zip(header, split_row(lines[k]))))
                    k += 1
                # таблица-описание формата (00 §0.12.5) — не реестр
                if header and header[0] == "id":
                    result.append(rows)
                j = k
            i = j
            continue
        i += 1
    return result


def first_id(cell):
    m = re.search(r"`([a-z0-9_/]+)\*?`", cell)  # `prefix_*` -> prefix_
    return m.group(1) if m else None


def norm(row, key):
    for k, v in row.items():
        if k.startswith(key[:5]):
            return v
    return ""


def covered(rid, row, models):
    """Есть ли плейсхолдер: точный ID; группа `prefix_*` — любой ID с префиксом;
    строка-описание без ID (семейства декора) — непустая папка категории в art/blockbench."""
    if rid.endswith("_"):
        return any(m.startswith(rid) for m in models)
    if not rid:
        cat = norm(row, "категория").strip()
        d = os.path.join(BB, cat)
        return bool(cat) and os.path.isdir(d) and any(f.endswith(".bbmodel") for f in os.listdir(d))
    return rid in models


def main():
    models = existing_models()
    manual = {}
    if os.path.exists(STATUS_FILE):
        manual = json.load(open(STATUS_FILE, encoding="utf-8"))
    out_rows = []
    for name in sorted(os.listdir(GDD)):
        if not name.endswith(".md") or name == "art-backlog.md":
            continue
        section = name.split("-")[0]
        for rows in art_tables(os.path.join(GDD, name)):
            for r in rows:
                if not r.get("id", "").strip():
                    continue
                rid = first_id(r.get("id", "")) or ""
                status = manual.get(rid) or ("PLACEHOLDER" if covered(rid, r, models) else "TODO")
                out_rows.append((section, status, r))
    counts = {}
    for _, st, _ in out_rows:
        counts[st] = counts.get(st, 0) + 1
    with open(OUT, "w", encoding="utf-8") as f:
        f.write("# Арт-бэклог (сводный)\n\n")
        f.write("> Генерируется `python art/tools/collect_backlog.py .` из подразделов\n")
        f.write("> «Арт-реестр» разделов GDD (формат 00 §0.12.5). Руками не править:\n")
        f.write("> правьте арт-реестр раздела или `art/backlog_status.json`\n")
        f.write("> (`{\"<id>\": \"MODELED\"}` / `\"DONE\"`).\n\n")
        f.write("Статусы: `TODO` → `PLACEHOLDER` (есть `.bbmodel` от генератора `spec_*.py`) →\n")
        f.write("`MODELED` (доработано в Blockbench) → `DONE` (проверено в игре).\n\n")
        f.write("Существующие плейсхолдеры разделов 00–05 (269 моделей) описаны в\n")
        f.write("`art/README.md` и здесь не перечисляются, если у раздела нет арт-реестра.\n\n")
        f.write("| Статус | Строк |\n|---|---|\n")
        for st in ("TODO", "PLACEHOLDER", "MODELED", "DONE"):
            f.write(f"| {st} | {counts.get(st, 0)} |\n")
        f.write(f"| **Всего** | **{len(out_rows)}** |\n\n")
        f.write("| Раздел | Статус | " + " | ".join(COLUMNS) + " |\n")
        f.write("|" + "---|" * (len(COLUMNS) + 2) + "\n")
        for section, st, r in out_rows:
            cells = [norm(r, c) for c in COLUMNS]
            f.write(f"| {section} | {st} | " + " | ".join(cells) + " |\n")
    print(f"{len(out_rows)} rows -> {OUT}; {counts}")


if __name__ == "__main__":
    main()
