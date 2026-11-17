# Power system: generation, transmission, storage, loads

Goal: physics as in real life for everything that produces electricity, for
transformers, substations and lines, and for the places surplus energy can go. Evidence
labels are explained in [README.md](README.md). Most numbers here are textbook values
written from memory **[M]** and marked as such; they are orientation, not constants to
copy into code.

## What the grid already has

The network core in `grid/` is further along than the generators that feed it:

- synchronous machine with swing equation, flux decay and an SEXS-type exciter
  (`PowerGrid`, `MachineParams` with presets for a turbogenerator, a diesel set and the
  Create shaft generator);
- load flow on a sparse complex LU (`PowerFlow`, `ComplexSparseLU`), islanding;
- transformers with a thermal model and tap changer, lines with ampacity and sag,
  breakers, lightning;
- protection: inverse-time overcurrent, distance, loss of field, under-frequency load
  shedding, three relay generations (`RelayGeneration`: electromechanical, static,
  numeric);
- induction motors (`InductionMotorSpec`, `MotorUnit`);
- synchroscope and closing checks.

Prime movers are the thin part. `PrimeMover` has two implementations: `Governed`, a
generic "valve plus droop governor" where mechanical power is proportional to valve
opening, and `CreateShaft`, which takes power from a Create kinetic network. The steam
turbine, gas turbine and diesel models that the Javadoc mentions are not written.

Two findings of the audit bear directly on new generators and should be fixed first:
the machine model has x_q = x′_d (H3) and the out-of-phase closing torque is half the
real value (H2). See [physics-audit.md](physics-audit.md).

## Generators

A new source is mostly a new prime mover plus a model of its resource. The resource
models for wind and water are in [environment.md](environment.md).

### Steam turbine (coal, later nuclear)

Boilers (fire-tube and water-tube), a reciprocating steam engine and IF97 water
properties exist. The turbine does not; `plant/SteamPlant` drives the engine. The
turbine closes the chain from coal to the grid.

- Flow through a stage group: Stodola's ellipse,
  `ṁ·√T_in / p_in = K·√(1 − (p_out/p_in)²)`.
- Work: isentropic enthalpy drop from IF97 times stage-group efficiency; wetness loss by
  Baumann's rule, about 1 % of efficiency per 1 % of moisture **[M]**.
- Drum level and pressure: the Åström–Bell drum boiler model, which reproduces shrink
  and swell.
- Governor and valve dynamics: the IEEE steam turbine models (TGOV1, IEEEG1 families).
- Around it: condenser vacuum set by cooling water temperature, feedwater heaters,
  deaerator (exists), cooling tower.

References: Åström and Bell, "Drum-boiler dynamics", *Automatica* 36 (2000); IEEE
Committee Report, "Dynamic models for steam and hydro turbines in power system studies"
(1973); Cotton, *Evaluating and Improving Steam Turbine Performance*.

### Hydro

- Power: `P = η·ρ·g·Q·H`.
- Turbine type by head **[M]**: Pelton above roughly 300 m, Francis from about 40 to
  600 m, Kaplan below about 40–70 m; selection by specific speed.
- Penstock: water starting time `T_w = L·Q / (g·A·H)`, typically 0.5 to 4 s **[M]**. The
  ideal turbine responds to gate movement as `(1 − T_w·s) / (1 + 0.5·T_w·s)`: opening
  the gate first lowers the power. A plain droop governor is unstable with it; hydro
  governors need transient droop.
- Water hammer on fast closure: `Δp = ρ·a·Δv` (Joukowsky), surge tanks.
- Cavitation limits the setting height (Thoma number).
- Efficiency depends on gate and head (hill chart); Francis units have a rough zone at
  part load.

Already usable: `hydraulics/LumpedFluidNetwork` has the Joukowsky estimate, and
`HomologousCurve` holds four-quadrant pump curves, which is what a reversible
pump-turbine needs.

References: IEEE Working Group, "Hydraulic turbine and turbine control models for system
dynamic studies", *IEEE Trans. Power Systems* 7(1), 1992 **[S]** (citation only);
Kundur, ch. 9; Chaudhry, *Applied Hydraulic Transients*.

### Wind

- Power: `P = ½·ρ·A·v³·C_p(λ, β)`, tip-speed ratio `λ = Ω·R / v`. The Betz limit is
  `C_p = 16/27` **[S]**; real rotors reach about 0.45–0.50 **[M]**.
- Operating range **[M]**: cut-in near 3–4 m/s, rated near 11–13 m/s, cut-out near
  25 m/s. Between cut-in and rated the cube law rules: a gust 20 % above the mean
  carries 73 % more power.
- Drive train as two masses with a soft shaft; tower shadow and wind shear give a
  periodic torque ripple.
- Wake behind a turbine (Jensen model) reduces the wind for the next one.
- Generator types: fixed-speed induction machine (the first Danish designs), doubly-fed
  induction, full converter.

A fixed-speed turbine with an induction generator is the natural first step, since the
induction machine model exists. Whether `MotorUnit` runs correctly at negative slip was
not checked.

References: Burton et al., *Wind Energy Handbook*; Heier, *Grid Integration of Wind
Energy*; Slootweg et al., general variable-speed turbine model (2003);
[Dirscherl, Hackl and Schechner, modelling and control of wind turbine systems](https://arxiv.org/pdf/1703.08661) **[S]**.

### Gas turbine and diesel

- Brayton cycle: ideal efficiency `1 − r^(−(γ−1)/γ)`, corrected by compressor and
  turbine isentropic efficiencies. Output falls as intake air warms, roughly 0.7 % per
  kelvin **[M]**.
- Dynamics: Rowen's simplified heavy-duty gas turbine model (1983), with the
  exhaust-temperature limiter.
- Diesel: mean-value engine model, a governor with a combustion dead time; specific
  fuel consumption near 200 g/kWh **[M]**.

`PrimeMover.Governed` is the stub both would replace.

### Solar

- Array output (PVWatts form): `P_dc = P_stc·(G/1000)·[1 + γ·(T_cell − 25)]`; γ is
  about −0.47 % per °C for a standard module (PVWatts v5, via a secondary
  source **[S]**).
- Cell temperature from ambient and irradiance (NOCT relation).
- Full I–V curve: the five-parameter single-diode model (De Soto et al., 2006).
- Irradiance needs sun position, cloud cover and season; see
  [environment.md](environment.md).

Solar, full-converter wind and batteries connect through inverters. They have no
inertia and limited fault current (about 1.1–1.2 pu **[M]**), and they follow the grid
voltage instead of forming it. The grid model today is built on synchronous machines; an
inverter-based source is a new kind of bus injection and should be designed as one, not
faked as a small generator.

### Others, briefly

Geothermal (flash or binary cycle), biomass and waste (a boiler with another fuel),
radioisotope generators (already in the roadmap for the nuclear era), fuel cells, tidal
barrage and tidal stream (see [environment.md](environment.md)).

## Transmission and substations

Present: lines, transformers, breakers, relays, load shedding, lightning. Missing for the
full picture:

- reactive power compensation: shunt capacitors and reactors, synchronous condensers;
- corona loss and audible noise at high voltage (Peek's law);
- automatic reclosing after transient faults;
- secondary frequency control across several plants, and dispatch;
- instrument transformers with saturation, which matter for protection behaviour;
- HVDC links, as a late-game item;
- dynamic line rating: conductor cooling depends on wind (IEEE 738). The convection
  coefficient is currently a constant per weather state; see the wind section of
  [environment.md](environment.md).

With low-inertia sources the rate of change of frequency after losing ΔP is
`df/dt = ΔP·f₀ / (2·H·S)`. This is the number that makes inertia a gameplay resource.

## Storage

| Kind | Physics | Round trip **[M]** |
|---|---|---|
| Pumped hydro | The hydro model run both ways; pump-turbine on four-quadrant curves | 70–85 % |
| Lead-acid battery | Peukert capacity law, 30–40 Wh/kg | ~80 % |
| Nickel-iron (Edison) | Fits the early eras historically; robust, low efficiency | ~65 % |
| Lithium-ion | Equivalent circuit: open-circuit voltage against state of charge plus resistance and RC branches; 150–250 Wh/kg | 90–95 % |
| Flywheel | `E = ½·I·ω²`; specific energy limited by hoop stress, proportional to σ/ρ | ~90 %, with standing loss |
| Compressed air | Polytropic compression, heat management decides efficiency | 40–70 % |
| Hydrogen | Electrolysis at 4.5–5.5 kWh per Nm³, then fuel cell or turbine | 30–40 % |

Reference for battery dynamics: Tremblay and Dessaint (2009); Plett, *Battery
Management Systems*.

## Where surplus energy goes

Industrial loads that fit what the mod has or plans:

| Load | Specific energy | Fit |
|---|---|---|
| Electric arc furnace | 350–450 kWh per tonne of steel **[M]** | Graphite electrodes are already produced (`ElectrodeExtruder`, `BakingFurnace`) |
| Submerged arc furnace for silicon | 11–13 kWh per kg **[S]** | First step of the chip chain, see [electronics.md](electronics.md) |
| Polysilicon deposition (Siemens) | 45 to 200 kWh per kg depending on plant generation **[S]** | Same chain |
| Aluminium smelting (Hall–Héroult) | 13–15 kWh per kg **[M]** | Chemistry era |
| Chlor-alkali | 2.1–2.6 MWh per tonne of chlorine **[M]** | Chemistry era; also the source of HCl |
| Water electrolysis | 4.5–5.5 kWh per Nm³ H₂ **[M]** | Hydrogen for chemistry and chip making |
| Air separation | 0.42 kWh per Nm³ O₂ (value in `AirSeparationUnit`) | Exists |
| Uranium enrichment | Gaseous diffusion about 2400 kWh per SWU, centrifuges about 50 **[M]** | Nuclear era; historically the largest single loads ever built |
| Graphitisation furnace | 3–5 MWh per tonne **[M]** | Reactor graphite and electrodes |

Loads with thermal or product storage (electrolysis, air separation, furnaces between
heats) can also follow the surplus, which makes them a control problem for the player.

### Personal equipment

IC2-style armour was the starting point. Equipment with real physics that suits the mod's
systems:

- **Electric cap lamp** replacing the flame safety lamp (`mining/SafetyLamp`). This is
  the historical sequence in coal mines.
- **Powered respirator** against mine gases, tied to `mining/GasExposure`.
- **Heated and cooled suit**, tied to the existing Cold Sweat integration.
- **Powered exoskeleton**, electric hand tools, a mine locomotive on trolley wire or
  batteries.

Capacity follows from battery specific energy: a 10 kg lead-acid pack holds about
0.35 kWh, a lithium-ion pack of the same mass about 2 kWh **[M]**. A projected "energy
shield" has no physical counterpart and would be the one invented item in the list.

## Proposed order

1. Steam turbine. It completes the coal-to-grid chain that era I1 promises.
2. Hydro. It reuses the fluid network and needs the transient-droop governor.
3. Fixed-speed wind with an induction generator.
4. Diesel and gas turbine in place of the generic governed prime mover.
5. Solar, batteries and full-converter wind, once the inverter-based injection exists.

Each step reuses more of the existing code than the next. The order is a proposal.

## Sources

- [IEEE Working Group, hydraulic turbine and turbine control models, 1992](https://ieeexplore.ieee.org/document/141700)
- [Dirscherl, Hackl and Schechner, modelling and control of modern wind turbine systems](https://arxiv.org/pdf/1703.08661)
- [Dobos, *PVWatts Version 5 Manual*, NREL](https://www.osti.gov/biblio/1158421)
- [De Soto single-diode model, pvlib documentation](https://pvlib-python.readthedocs.io/en/stable/reference/generated/pvlib.pvsystem.calcparams_desoto.html)
- [NREL-hosted paper on silicon production energy](https://nrel.gov/docs/fy25osti/89464.pdf)
- [Fraunhofer ISE, electricity consumption of polysilicon production](https://www.ise.fraunhofer.de/content/dam/ise/en/documents/publications/studies/25_en_ISE_Report_Analysis-of-the-Electricity-Consumption-for-the-Production-of-Electronic-Grade-Polysilicon.pdf)
