# Machine building: tools the player makes for the plant

Goal: players manufacture the machine tools and equipment of their own plant, so
that work gets easier as the plant grows. Evidence labels are explained in
[README.md](README.md).

## What exists

- A machine shop in era I0: `machine_lathe` and `drill_press`
  (`metallurgy/MachineToolUnit`), driven from Create shafts.
- Cutting physics in `metallurgy/Machining`: cutting power `P_c = k_c·a_p·f·v_c` turned
  into Create stress, and tool life by Taylor's equation `v_c·T^n = C_T`. Specific
  cutting forces: steel 2000, cast iron 1100, aluminium 700 N/mm². Tool materials: HSS
  and carbide. Several of these constants carry a "to verify" mark in the code.
- A foundry (cupola, crucible, sand moulds, shakeout) and casting specifications with
  inspection and machining flags (`CastingSpec`: radiography, ultrasonics, hydrotest;
  turned, drilled).
- Wear and maintenance in `ops/`: bearings, lubrication, gland packing, impeller wear,
  Weibull life, a maintenance panel, commissioning with assembly defects and an
  equipment passport.

So parts are already cast, machined and worn. What is missing is that the machines doing
it are themselves products of the player's shop, and that their quality matters.

## The idea in one line

A machine tool can make parts about as accurate as itself. Accuracy is therefore built
up generation by generation, and the accuracy of a part decides how the machine it goes
into behaves.

This is how it happened historically, and it gives progression without tier labels.

## Accuracy as the progression variable

- **Tolerance grades.** ISO 286 defines grades IT01 to IT18 **[M]**. Typical reach:
  sand casting IT14–16, turning IT8–11, grinding IT5–7, lapping IT3–5. A machine in the
  game carries the grade it can hold.
- **Bootstrapping.** Flat reference surfaces come from scraping three plates against
  each other (Whitworth); straightness and squareness are derived from flatness; an
  accurate lead screw is made by correcting an inaccurate one (Maudslay). Each is a
  player-run operation that raises the shop's best grade **[M]**.
- **Metrology.** A shop cannot hold what it cannot measure: surface plates,
  micrometers, gauge blocks, a reference temperature of 20 °C. Thermal growth is
  `ΔL = α·L·ΔT`, about 12 µm per metre per kelvin for steel **[M]**, so a warm shop
  loses grades.
- **What accuracy buys.** The existing wear models take it directly: bearing clearance
  and alignment set bearing life, impeller and seal clearances set pump efficiency and
  leakage, rotor balance sets vibration, blade and nozzle finish set turbine
  efficiency. A turbine built with parts from a worn lathe works, runs rough and fails
  early. No bonus multipliers are needed.

## Cutting physics to add

- **Taylor exponents.** A common textbook table gives n = 0.125 for HSS on steel, 0.25
  for carbide, about 0.6 for ceramics **[S]**; other tables give ranges (HSS 0.08–0.2,
  carbide 0.2–0.4). The carbide value in the code matches.
- **Specific cutting force** depends on chip thickness (Kienzle):
  `k_c = k_c1.1 · h^(−m_c)` **[M]**. The code uses a constant per material.
- **Surface finish in turning:** theoretical roughness `R_t ≈ f² / (8·r_ε)` from feed
  and nose radius **[M]**.
- **Chatter.** A cut is stable only below a depth that depends on spindle speed and
  machine stiffness (stability lobes) **[M]**. This is the physical reason heavy cast
  beds matter, and it makes rigidity a property of the machine the player built.
- **Heat.** Most cutting power ends up in the chip and tool; coolant extends tool life.

## Machines and what each unlocks

| Machine | Makes | Needs first |
|---|---|---|
| Lathe (exists) | Shafts, bushings, flanges | Cast bed, lead screw |
| Drill press (exists) | Holes | — |
| Shaper, planer | Flat surfaces, guideways | Lathe |
| Milling machine | Slots, gear teeth with a dividing head | Planed ways, lead screws |
| Boring mill | Large accurate bores: cylinders, casings | Lathe, planer |
| Grinder | Hardened parts, bearing races, finish below IT7 | Abrasive wheels, good spindle bearings |
| Gear hobber | Gears in quantity | Milling machine, dividing accuracy |
| Press, hammer, rolls | Forgings, plate work | Steel, foundations |

Tool materials form their own ladder: carbon tool steel, high-speed steel
(tungsten–chromium–vanadium), cemented carbide (tungsten carbide in cobalt, a powder
process), then ceramics **[M]**. Each raises cutting speed several times and needs an
industry behind it.

Machine elements are products too: plain bronze bearings exist; rolling bearings need
bearing steel and grinding; lead screws become ball screws; guideways are scraped, then
ground.

## Making life easier

The second half of the goal. Real plants removed manual work in steps, each resting on
the one before:

1. **Jigs and fixtures.** One accurate fixture lets an unskilled operation repeat a
   result.
2. **Individual electric drive** in place of line shafts. The grid and induction motor
   models exist.
3. **Cam automatics and copy lathes.** The machine repeats a part without an operator.
4. **Transfer lines.** Parts move between stations by themselves; Create already
   supplies conveying.
5. **Numerical control.** A program replaces the cam; it needs electronics from
   [electronics.md](electronics.md).
6. **Robots and flexible cells.**

In game terms each step reduces the number of player actions per part, and each is
gated by something the player has to build, not by a research counter.

## Drawings

A machine is built from drawings, and a shop that wants to build a second machine needs
them. This links machine building to the documentation system: see
[documentation.md](documentation.md).

## References

Moore, *Foundations of Mechanical Accuracy*; Roe, *English and American Tool Builders*;
Shaw, *Metal Cutting Principles*; Trent and Wright, *Metal Cutting*; Tlusty,
*Manufacturing Processes and Equipment*; Slocum, *Precision Machine Design*;
*Machinery's Handbook*; ISO 286.
