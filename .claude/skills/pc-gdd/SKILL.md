---
name: pc-gdd
description: Look up a Prompt Critical GDD section (refs like "03d §3d.3.1", "GDD §0.12", "§5.2.4", "11 §11.1.2") without reading the 100–360 KB design files. Use whenever code, a task or the user cites the GDD or asks how something is specified.
---

# GDD lookup

The GDD is in `D:/Projects/Prompt_Critical-design` (`00-overview.md` … `15-renewables-storage.md`,
`art-backlog.md`). Its headings are `## 5.2 ...`, `### 5.2.4 ...`. File prefix = the section's first part
(`3d.3.1` → `03d-*.md`, `0.12` → `00-*.md`).

```bash
python .claude/skills/pc-gdd/section.py "03d §3d.3.1"   # section with subsections (max 300 lines)
python .claude/skills/pc-gdd/section.py §5.2 --no-sub    # only the intro, up to the first subsection
python .claude/skills/pc-gdd/section.py --toc 05 3       # heading index with line numbers
```

- No section number? Use `--toc <file> 2` to find it, or `Grep` a keyword with `path` set to the GDD dir and
  `-n`, then run `section.py` for the heading it hits.
- If the output says "... more lines", read only the next part you need with `Read` `offset`/`limit`.
- `00-overview.md` §0.13 ("Решения сверки") overrides every other section. Check it when two sections disagree.
- The GDD is the author's design. Don't edit it unless asked. Quote only the lines you rely on.
