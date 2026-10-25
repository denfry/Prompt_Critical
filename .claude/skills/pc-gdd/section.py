"""Print one section of the private GDD (Prompt_Critical-design) instead of reading a 100-360 KB file.

    python .claude/skills/pc-gdd/section.py "03d §3d.3.1"   # file 03d, section 3d.3.1 with its subsections
    python .claude/skills/pc-gdd/section.py §5.2.4           # file inferred from the section number
    python .claude/skills/pc-gdd/section.py 5.2.4 --no-sub   # stop at the first subsection
    python .claude/skills/pc-gdd/section.py --toc 05 [depth]  # headings with line numbers

GDD location: $PC_GDD, else the sibling directory Prompt_Critical-design.
"""
import os
import re
import sys
from pathlib import Path

MAX_LINES = 300
HEADING = re.compile(r"^(#+)\s+(\S+)")


def gdd_dir():
    env = os.environ.get("PC_GDD")
    return Path(env) if env else Path(__file__).resolve().parents[3].parent / "Prompt_Critical-design"


def find_file(prefix):
    """'3d' / '03d' / '5' / '05' -> the GDD file whose name starts with the two-digit prefix."""
    m = re.fullmatch(r"(\d+)([a-d]?)", prefix)
    if not m:
        sys.exit(f"bad file prefix: {prefix}")
    name = f"{int(m.group(1)):02d}{m.group(2)}-"
    hits = sorted(p for p in gdd_dir().glob(name + "*.md"))
    if not hits:
        sys.exit(f"no GDD file {name}*.md in {gdd_dir()}")
    return hits[0]


def parse_ref(args):
    """Return (file prefix, section number) from '03d §3d.3.1', '§0.12', '05 5.2.4', 'GDD §0.12'."""
    text = " ".join(args).replace("GDD", " ").replace("§", " ")
    parts = text.split()
    if len(parts) == 2:
        return parts[0], parts[1].rstrip(".")
    if len(parts) == 1:
        section = parts[0].rstrip(".")
        return section.split(".")[0], section
    sys.exit(__doc__)


def toc(prefix, depth):
    path = find_file(prefix)
    print(path.name)
    for n, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        m = HEADING.match(line)
        if m and len(m.group(1)) <= depth:
            print(f"{n:6d}  {line}")


def section(prefix, number, with_sub):
    path = find_file(prefix)
    lines = path.read_text(encoding="utf-8").splitlines()
    start = level = None
    for i, line in enumerate(lines):
        m = HEADING.match(line)
        if not m:
            continue
        if start is None:
            if m.group(2).rstrip(".") == number:
                start, level = i, len(m.group(1))
        elif len(m.group(1)) <= level or not with_sub:
            end = i
            break
    else:
        end = len(lines)
    if start is None:
        sys.exit(f"section {number} not found in {path.name}; try --toc {prefix}")
    body = lines[start:end]
    print(f"{path.name}:{start + 1}-{end}")
    print("\n".join(body[:MAX_LINES]))
    if len(body) > MAX_LINES:
        print(f"... {len(body) - MAX_LINES} more lines: Read {path} offset={start + 1 + MAX_LINES}")


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    args = sys.argv[1:]
    if not args:
        sys.exit(__doc__)
    if args[0] == "--toc":
        toc(args[1], int(args[2]) if len(args) > 2 else 3)
        return
    with_sub = "--no-sub" not in args
    prefix, number = parse_ref([a for a in args if a != "--no-sub"])
    section(prefix, number, with_sub)


if __name__ == "__main__":
    main()
