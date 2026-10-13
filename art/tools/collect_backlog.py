"""Собирает сводный арт-бэклог из подразделов «Арт-реестр» разделов GDD.

usage: python art/tools/collect_backlog.py <repo_root>

Читает docs/gdd/*.md, находит в каждом файле заголовок со словами
«Арт-реестр», берёт следующую за ним markdown-таблицу (формат GDD 00 §0.12.5)
и пишет docs/gdd/art-backlog.md. Статус строки:
  DONE / MODELED — если указан вручную в art/backlog_status.json;
  PLACEHOLDER    — если есть art/blockbench/**/<id>.bbmodel;
  TODO           — иначе.
Строки, где в колонке id перечислено несколько ID или описана группа,
попадают в бэклог одной строкой; статус по ним — по первому ID.
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

COLUMNS = ["id", "категория", "вид", "габарит", "состояния", "partial",
           "текстуры", "референс", "приоритет", "веха"]


def existing_models():
    ids = set()
    for _, _, files in os.walk(BB):
        for f in files:
            if f.endswith(".bbmodel"):
                ids.add(f[:-len(".bbmodel")])
    return ids


def split_row(line):
    cells = [c.strip() for c in line.strip().strip("|").split("|")]
    return cells


def art_tables(path):
    """Возвращает (заголовок раздела, [строки-словари]) для всех арт-реестров файла."""
    lines = open(path, encoding="utf-8").read().splitlines()
    result = []
    i = 0
    while i < len(lines):
        if lines[i].startswith("#") and "Арт-реестр" in lines[i]:
            # первая таблица после заголовка
            j = i + 1
            while j < len(lines) and not lines[j].lstrip().startswith("|"):
                if lines[j].startswith("#"):
                    break
                j += 1
            if j < len(lines) and lines[j].lstrip().startswith("|"):
                header = [h.lower() for h in split_row(lines[j])]
                if not header or header[0] != "id":
                    i = j + 1  # таблица-описание формата (00 §0.12.5), не реестр
                    continue
                rows = []
                k = j + 2  # пропуск строки-разделителя
                while k < len(lines) and lines[k].lstrip().startswith("|"):
                    cells = split_row(lines[k])
                    rows.append(dict(zip(header, cells)))
                    k += 1
                result.append(rows)
                i = k
                continue
        i += 1
    return result


def first_id(cell):
    m = re.search(r"`([a-z0-9_]+)`", cell)
    return m.group(1) if m else None


def norm(row, key):
    for k, v in row.items():
        if k.startswith(key[:5]):
            return v
    return ""


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
                status = manual.get(rid) or ("PLACEHOLDER" if rid in models else "TODO")
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
