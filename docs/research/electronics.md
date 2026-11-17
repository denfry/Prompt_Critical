# Electronics: from quartz to computers and monitors

Goal: players reproduce the whole production chain of computer chips
(semiconductors, process nodes) and can build computers and monitors. Evidence labels are
explained in [README.md](README.md). The mod has no electronics items today; this note is
the physics the chain would rest on.

## Why it fits

The automation ladder of the mod already implies electronics. `grid/RelayGeneration`
defines three relay generations: electromechanical (A1), static (A2) and numeric
terminals (A3). The fourth level is a DCS on CC: Tweaked computers, an optional
dependency declared in `neoforge.mods.toml`. Today those generations are parameters.
With this chain they become things the player manufactures:

| Automation level | Hardware it needs | Where it comes from |
|---|---|---|
| A1 relay logic | Coils, contacts | Wire and steel, exists |
| A2 analog computers, static relays | Vacuum tubes, then discrete transistors | Glass, vacuum, first semiconductors |
| A3 PLC, numeric relays | Integrated circuits on boards | Planar process |
| A4 DCS and SCADA | Computers and monitors | Microprocessors, memory, displays |

## The semiconductor chain

| Step | Physics | Link to the rest of the mod |
|---|---|---|
| Quartz to metallurgical silicon | `SiO₂ + 2C → Si + 2CO` in a submerged arc furnace; 11–13 kWh per kg **[S]** | Uses the graphite electrodes already produced; a large electrical load |
| Purification, Siemens process | Silicon and HCl at about 300 °C give trichlorosilane **[S]**; distillation; deposition on heated rods. 45 to 200 kWh per kg depending on plant generation **[S]** | HCl and hydrogen come from the chemistry era |
| Single crystal | Czochralski pulling; dopant distribution along the ingot by the Scheil equation `C_s = k·C₀·(1 − f)^(k−1)`; zone refining for the highest purity **[M]** | Argon from the air separation unit |
| Wafers | Sawing, lapping, polishing; removal rate proportional to pressure times speed (Preston) **[M]** | Machine tools, abrasives |
| Oxidation | Deal–Grove: `x² + A·x = B·(t + τ)`; linear growth for thin oxide, parabolic for thick; Arrhenius rate constants; wet faster than dry **[S]** | Furnaces with programmed ramps exist |
| Lithography | Resolution `CD = k₁·λ / NA`, depth of focus `k₂·λ / NA²` (Rayleigh) **[M]** | This is what a "process node" is |
| Doping | Diffusion by Fick's law: error-function profile from a constant source, Gaussian after drive-in, `D = D₀·exp(−E_a / kT)`. Ion implantation: Gaussian profile at a projected range **[M]** | — |
| Deposition and etching | CVD limited either by gas transport or by surface reaction (Grove); sputtering; wet and plasma etching with selectivity and anisotropy **[M]** | Silane and dopant gases from chemistry |
| Metallisation | Aluminium, later copper; electromigration lifetime by Black's equation, `MTTF ∝ J^(−n)·exp(E_a / kT)` **[M]** | — |
| Test and packaging | Probe, dice, bond, encapsulate, burn-in | Gold or aluminium wire, epoxy |

### Lithography ladder

Each exposure technology sets a floor on feature size **[M]**:

| Technology | Wavelength | Roughly reaches |
|---|---|---|
| Contact and proximity printing | Mercury lamp, broadband | 10 down to about 2 µm; resolution goes as √(λ·gap) |
| Projection stepper, g-line | 436 nm | ~1 µm |
| Stepper, i-line | 365 nm | ~0.35 µm |
| KrF excimer | 248 nm | ~0.13 µm |
| ArF excimer, later immersion | 193 nm | below 0.1 µm |
| EUV | 13.5 nm | below 10 nm |

### Yield and the clean room

This is the centre of the gameplay. The fraction of good dice on a wafer **[S]**:

```
Poisson             Y = exp(−A·D₀)
negative binomial   Y = (1 + A·D₀/α)^(−α)      α about 0.3 to 7, clustering of defects
```

A is die area and D₀ the density of killer defects. D₀ comes from the air: ISO 14644-1
allows at most

```
C_n = 10^N · (0.1 / D)^2.08   particles per m³ of size ≥ D µm
```

in a room of class N **[S]** (class 5: 100 000 at 0.1 µm, 3 520 at 0.5 µm). A particle
kills a die when it is comparable to the feature, so each step down in feature size
needs a cleaner room for the same yield, and larger dice are punished exponentially.
Air changes, filters, gowning and keeping the door shut become process variables.

### From process node to chip

What the player gets for a finer process follows from scaling rules, not from a tier
table. Under constant-field (Dennard) scaling by a factor κ **[M]**: gate delay falls
as 1/κ, power per gate as 1/κ², density rises as κ². Transistor behaviour itself is
the square-law MOSFET with a threshold voltage set by oxide thickness and doping, both
outputs of the steps above. Dynamic power is `α·C·V²·f`.

So a chip is described by its process (feature size, oxide, doping) and its area;
speed, power and yield are computed.

### Supplies

- Ultrapure water, 18.2 MΩ·cm **[M]**. The water treatment train exists and would need a
  polishing stage.
- High-purity nitrogen, argon, oxygen and hydrogen. Air separation exists.
- Acids and oxidisers: HF, sulfuric acid, hydrogen peroxide, ammonia (the RCA
  cleans) **[M]**.
- Photoresist: the classic positive resist is a novolac resin with a diazoquinone
  sensitiser **[M]**. Novolac is made from phenol, and phenol comes from coal tar, which
  the coke battery already yields. This is the "connected industries" pillar working as
  intended.

### How far to go

Proposed range: discrete transistors, then a planar process at about 10 µm, down to
about 1 µm with steppers. That covers everything up to a microprocessor of the early
1980s, which is what a DCS needs. Below that each step is a separate industry: excimer
lasers, immersion optics, multiple patterning, and for EUV a tin-plasma light source and
multilayer mirrors.

Two anchors for scale and realism:

- a hobbyist built a working integrated circuit of 100 transistors in a home
  garage **[S]**, so a small player-run fab is not absurd;
- the SkyWater SKY130 process (130 nm) has openly published documentation of its layers
  and design rules **[S]**, usable as a model of a real late-game process.

## Before silicon: vacuum tubes

For level A2. A tube needs glass, a vacuum, a heated cathode and metal-to-glass seals.

- Emission: Richardson–Dushman, `J = A·T²·exp(−W / kT)`; thoriated tungsten and oxide
  cathodes lower the work function W **[M]**.
- Space-charge-limited current: Child–Langmuir, `I = K·V^(3/2)` **[M]**.
- Vacuum: a rotary pump backs a diffusion pump; getters hold the vacuum after sealing.

The same vacuum and glass technology returns in the cathode-ray tube.

## Boards and components

- Laminate: paper-phenolic first, glass-epoxy later; copper foil bonded on.
- Pattern: photoresist and etching (ferric chloride), drilling, through-hole plating,
  solder mask **[M]**.
- Solder: tin-lead eutectic 63/37 melts at 183 °C; lead-free alloys near
  217–220 °C **[M]**. Wave soldering, then reflow with a temperature profile.
- Passives: carbon and metal-film resistors; ceramic, film and electrolytic capacitors;
  inductors and transformers.
- Frequency reference: quartz resonators, grown hydrothermally and cut; for the AT cut
  the frequency is inversely proportional to plate thickness **[M]**.
- Memory before semiconductors: magnetic cores.

## Computers and monitors

A computer is a processor, memory, storage, a power supply and boards, all of which the
sections above produce.

Displays, in historical order:

- **Cathode-ray tube.** Electron gun, anode at 15–25 kV, magnetic deflection, phosphor
  screen, leaded glass, a shadow mask for colour **[M]**. It needs the glass and vacuum
  industry and high-voltage electronics, and no lithography finer than the mask.
- **Liquid-crystal panel.** Glass substrates with a transparent conductor, a thin-film
  transistor matrix (a coarse semiconductor process on glass), liquid crystal,
  polarisers, backlight **[M]**.
- **LED.** Compound semiconductors, a separate materials chain.

### In the game

CC: Tweaked provides the computer and monitor blocks used by the A4 level. When it is
installed, the natural design is that their recipes require parts made by this chain
(processor, memory, board, tube or panel), so the DCS is something the player's own
industry produces. How recipes of another mod are replaced, and what stands in for the
computer when CC: Tweaked is absent, is not designed yet.

Computers then feed back into the plant: numeric relays, CNC machine tools (see
[machine-building.md](machine-building.md)) and office work (see
[documentation.md](documentation.md)).

## References

Plummer, Deal and Griffin, *Silicon VLSI Technology*; Sze, *VLSI Technology*; Campbell,
*Fabrication Engineering at the Micro- and Nanoscale*; Jaeger, *Introduction to
Microelectronic Fabrication*; Mack, *Fundamental Principles of Optical Lithography*;
Deal and Grove, *J. Appl. Phys.* 36, 3770 (1965); Dennard et al., *IEEE J. Solid-State
Circuits* 9, 256 (1974); Stapper's papers on yield modelling.

## Sources

- [Deal–Grove model](https://en.wikipedia.org/wiki/Deal%E2%80%93Grove_model)
- [NREL-hosted paper, energy of silicon production](https://nrel.gov/docs/fy25osti/89464.pdf)
- [Fraunhofer ISE, electricity consumption of polysilicon production](https://www.ise.fraunhofer.de/content/dam/ise/en/documents/publications/studies/25_en_ISE_Report_Analysis-of-the-Electricity-Consumption-for-the-Production-of-Electronic-Grade-Polysilicon.pdf)
- [Yield models overview](https://fog.misty.com/perry/cod/references/yield_models.pdf)
- [MIT OpenCourseWare, yield modelling lecture](https://www.ocw.mit.edu/courses/2-830j-control-of-manufacturing-processes-sma-6303-spring-2008/4aff1e21de13870355ef44dbe71f45c6_lecture10.pdf)
- [ISO 14644-1 class limits](https://engineeringtoolbox.com/clean-rooms-iso-d_933.html)
- [Hackaday, the Zeloof Z2 integrated circuit](https://hackaday.com/2021/08/14/the-zeloof-z2-intergrated-circuit-has-100-transistors)
- [SkyWater SKY130 open process documentation](https://skywater-pdk.readthedocs.io/en/main/)
