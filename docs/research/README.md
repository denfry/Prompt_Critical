# Research notes

Working notes from the physics review of `pc-sim` and the design research that followed it
(2026-11-17). They record what was checked, what was found, and which physical
models the planned features need. These notes change no code; fixes and features are
separate work.

## Contents

| File | What it holds |
|---|---|
| [physics-audit.md](physics-audit.md) | Review of the existing `pc-sim` models against the literature: 4 high-severity errors, 13 medium, a list of low ones, what was confirmed correct, what was not checked |
| [tech-stack.md](tech-stack.md) | Whether anything besides Java is needed for heavy computation, and the reference-tooling pattern |
| [power-system.md](power-system.md) | Every way of generating electricity, transmission gaps, storage, and where surplus energy can go |
| [environment.md](environment.md) | The missing environment layer: wind and gusts, river discharge and reservoirs, tides and moon phases, seasons |
| [electronics.md](electronics.md) | Semiconductor chain from quartz to packaged chip, boards and components, computers and monitors |
| [machine-building.md](machine-building.md) | Machine tools made by the player, the precision ladder, factory automation |
| [documentation.md](documentation.md) | Plant paperwork written by the player by hand, then documents and tables on computers |
| [gtnh-ideas.md](gtnh-ideas.md) | Which mechanics of GregTech: New Horizons are worth borrowing as ideas, and which are not |

## Evidence labels

Every claim that matters carries one of these:

| Label | Meaning |
|---|---|
| **[S]** | The source was opened and the value read from it |
| **[D]** | Derived or recomputed for these notes (algebra, or a numeric calculation) |
| **[A]** | Found in a second, quicker pass and not independently re-checked |
| **[M]** | Textbook knowledge written from memory. Verify against the cited book before it goes into code |

A link is given only for a source that was actually seen. Books cited without a link are
standard references named so the value can be looked up.

## Baseline

- Code reviewed at commit `d154547` (branch `feat/jei-tags-jade`). Every `file:line` in
  [physics-audit.md](physics-audit.md) was re-read at that commit. The same `pc-sim` files
  are unchanged at `b2d699c` (`feat/t0-graphite-pile`), which only adds `sim/reactor`.
- Paths are relative to `pc-sim/src/main/java/dev/denfry/promptcritical/sim/` unless they
  start with `pc-mod/`.
- Not run: tests, build, JMH benchmarks. The working tree was in use for other work, so
  the review was read-only.
- The design document the code comments cite (`01 §…`, `07 §…`) is not in the repository
  and was not consulted. Where a test pins a value "from the GDD", the comparison here is
  with the literature, not with that document.

## Goals behind these notes

In the order they were set:

1. Check the maths and physics of the mod against the literature (documentation,
   papers, theses), and settle whether the stack needs anything besides Java for heavy
   computation.
2. Physics as in real life for everything that generates electricity (wind turbines,
   dams, coal plants and the rest), for transformers, substations and transmission
   lines, and for the things surplus energy is spent on, including personal gear in the
   spirit of IC2 armour.
3. A full, reproducible production chain for computer chips: semiconductors, process
   nodes.
4. The mod itself adds the environment the generators need: windy weather and gusts,
   water flow.
5. Players build machine tools for their own plant to make work easier, and can make
   computers and monitors.
6. Water and wind go in completely: tides, moon phases, Serene Seasons.
7. Before players have computers they write the technical documents for their machines
   by hand; with computers they work with documents and tables on them, as in real life.
8. Borrow what is worth borrowing, as ideas, from GT New Horizons.

## Open decisions

Still to decide:

- **Order of work.** Proposed: fix the four high-severity errors first; then the
  environment layer (small, and it improves machines that already exist); then the steam
  turbine, hydro, fixed-speed wind, gas turbine and diesel, and last the inverter-based
  sources; semiconductors after the chemistry era, because the chain needs HCl and
  hydrogen.
- **How far the chip chain goes.** Proposed: from discrete transistors and a ~10 µm planar
  process down to about 1 µm. Deep-UV and EUV are each a plant of their own.
- **Whether tides and reservoirs move real water blocks.** Proposed: a virtual level for
  machines everywhere, real block changes only inside basins the player has enclosed.
- **River discharge** has to be a designed quantity, because vanilla rivers lie at sea
  level and do not flow. Everything downstream of it can be physical.
- **Computers without CC: Tweaked.** The chip chain ends in computers and monitors, and
  the documentation system moves onto them. Whether that requires CC: Tweaked or the mod
  gets a computer block of its own is not decided.
- **How much paperwork is mandatory.** Proposed: drawings and passports have mechanical
  consequences; logs and procedures stay optional tools.
- **Language of these notes.** Written in English to match the other Markdown in the
  repository.
