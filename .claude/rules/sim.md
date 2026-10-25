---
paths:
  - "pc-sim/**"
---

# pc-sim (ArchUnit-enforced, GDD 05 §5.1.2, §5.2.4)

- No Minecraft, NeoForge or Create imports in `..sim..`.
- Determinism: randomness only from `SplittableRandom` with a derived seed, never `Math.random`. Time only from
  `SimClock`, never `System.currentTimeMillis`. No `new Thread` outside `sim.core.exec`.
- Units and time domains follow `00-overview` (look up with `pc-gdd`). Don't invent constants: cite the `§` or the
  reference script in `pc-sim/tools/` (`if97_reference.py`, `ideal_gas_reference.py`).
- Test loop: `./gradlew :pc-sim:test --tests "<Class>" -q`. Hot-path changes: check `./gradlew :pc-sim:jmh`
  against the numbers in GDD 05 §5.7.6.
