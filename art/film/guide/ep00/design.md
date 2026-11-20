# Design: Prompt Critical guide

Concept: footage is the plant; the graphics are its instruments — gauges, annunciator tiles, engineering callouts and
era codes drawn over the picture like a control-room overlay.

| Token | Value | Use |
|---|---|---|
| bg | `#14110d` | cards, title plates (warm near-black) |
| fg | `#efe6d6` | text (warm paper) |
| accent | `#f2b705` | hazard yellow: era codes, callout lines, highlights |
| alarm | `#e5402a` | only for trips, alarms and accidents |
| muted | `#8f8576` | secondary labels |

Type: **Oswald** 600–700 for headlines (condensed, industrial, has Cyrillic); **IBM Plex Mono** 400–600 for codes,
readouts and labels. Headlines 72–140 px, labels 24–30 px, readouts 40–96 px.

Do: hairline-to-3px accent rules that draw in (`scaleX`), monospace metadata in corners (era, tier, coordinates),
hard cuts on the beat (130 BPM, bar = 1.846 s), whip blur on era changes, punch-in on gauges.
Don't: gradients on text, neon cyan, centered floating cards, more than one accent besides alarm red.
