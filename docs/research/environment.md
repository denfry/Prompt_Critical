# Environment layer: wind, water, tides, seasons

Wind turbines and hydro plants need a wind field and a river discharge. Neither exists:
`pc-sim` has no wind model and no notion of how much water a river carries. This note
describes one shared layer that provides them. The decision is
that all of it goes in: wind and gusts, water flow, tides, moon phases, and Serene
Seasons. Evidence labels are explained in [README.md](README.md).

## What exists today

- `pc-mod/.../mod/grid/GridWorld.java` (method `weather`) passes three numbers to
  `PowerGrid.setWeather(ambientC, convectionWm2K, solarWPerM)`: 12 °C in rain and 18 °C
  otherwise; a conductor convection coefficient of 35 in a thunderstorm, 25 in rain,
  10 in clear weather; solar heating of 10 by day without rain. This is the only
  weather coupling of the simulation, and the place where a real wind speed would
  replace three constants.
- `SandMoldBlockEntity` wets sand moulds in rain; `grid/Lightning` strikes lines.
- Cold Sweat integration gives heat sources as block temperatures.
- `mining/SimplexNoise` is a noise generator already inside `pc-sim`.
- `neoforge.mods.toml` declares CC: Tweaked as an optional dependency. Serene Seasons is
  named in the README as planned and is not declared yet.

## Shape of the layer

One `Environment` model in `pc-sim`, queried by position and time, giving:

| Quantity | Used by |
|---|---|
| Wind vector, gust, turbulence intensity | Wind turbines, chimney draft, mine ventilation, line cooling, gas plumes, cooling towers |
| Air temperature and density | Wind and gas turbine output, condenser and transformer cooling, Cold Sweat |
| Precipitation, snow store | River discharge, sand moulds, line icing |
| River discharge | Hydro plants, water wheels, cooling water intakes |
| Sea level (tide) | Tidal plants, coastal intakes |
| Sun elevation and cloud cover | Solar, line heating |
| Season | All of the above |

Requirements:

- **Deterministic.** The simulation keeps a state hash. The environment must be a pure
  function of world seed, game time and position, plus a small amount of saved slow
  state. No dependence on which chunks are loaded or how many players are online.
- **Cheap.** One slow state per dimension and a spatial modulation evaluated on demand.
  A query costs a few noise samples.
- **Vanilla-compatible.** Vanilla rain and thunder remain the visible weather; the layer
  reads them and adds what they lack.

## Wind

Vanilla Minecraft has no wind. Weather is rain or thunder for the whole dimension.

### Three time scales

Real wind speed has energy at three separated scales, and a model should have all three.

| Scale | Evidence | In the game |
|---|---|---|
| Synoptic, days | Van der Hoven's spectrum has a peak near four days, from passing pressure systems **[S]** | A slow random process per dimension, raised by rain and thunder, shifted by season |
| Diurnal | A 24-hour peak, strong inland, weak at sea **[S]** | A day–night term tied to game time |
| Turbulence, seconds to minutes | Peak near one minute; a gap between it and the synoptic range, roughly 1–10 cycles per hour **[S]** | Filtered noise on top of the slow mean |

The gap is what allows the split into "mean wind" and "turbulence around it".

### Turbulence and gusts

IEC 61400-1 gives the standard deviation of the along-wind speed in its normal
turbulence model **[S]**:

```
σ₁ = I_ref · (0.75·V_hub + 5.6 m/s)
```

with I_ref = 0.12, 0.14, 0.16 or 0.18 by site class (C, B, A, A+). Gusts come from
shaping white noise to the Kaimal spectrum of the same standard; a first- or
second-order filter per source is enough. The standard also defines deterministic
extreme events (extreme operating gust, direction change, shear) that make good scripted
hazards.

For gusts to be coherent in space the noise field should move with the mean wind
(Taylor's frozen-turbulence hypothesis). A gust then crosses a wind farm as a front,
turbine after turbine, instead of shaking each machine independently.

### Height and terrain

- Profile over flat ground: `v(z) = v_ref · ln(z/z₀) / ln(z_ref/z₀)`. Roughness length z₀
  by biome **[M]**: open water about 0.0002 m, grassland 0.03 m, forest 0.5–1 m.
- Speed-up over hills and ridges from the height map; a first estimate for a
  two-dimensional ridge is proportional to height over half-width (Jackson and Hunt).
- Air density from temperature and altitude: `ρ = p / (R·T)`.

### What else the wind field feeds

It is one model with many consumers, and most of them exist already:

- conductor cooling in `PowerGrid.setWeather`, where the audit also noted one convection
  coefficient for every diameter;
- chimney draft (`combustion/Chimney`);
- natural ventilation pressure in mines, which reverses between summer and winter in
  real shafts;
- dispersion of a gas or radioactive release (Gaussian plume with stability classes);
- visuals and sound on the client: smoke drifting, flags, particles.

References: IEC 61400-1; Van der Hoven (1957); Kaimal et al. (1972); Veers,
*Three-Dimensional Wind Simulation* (Sandia, 1988); Burton et al., *Wind Energy
Handbook*, ch. 2; Stull, *An Introduction to Boundary Layer Meteorology*. NREL's TurbSim
generates reference time series with the IEC spectra and can serve as the test oracle.

## Water

### What vanilla gives

- Rivers are carved at sea level. They have no slope and do not flow anywhere.
- Source blocks are infinite; water has a flow direction but no flow rate.

So the discharge of a river cannot be derived from the world. It has to be assigned:
a function of seed, position along the river, season and recent precipitation. This is
the one unavoidable invention. Everything downstream of it can be computed.

### Discharge

- Base flow by river size, from world-generation noise.
- Rain adds a delayed pulse (a linear reservoir: storage S, outflow S/k).
- Snow accumulates in the cold season and melts by a degree-day law,
  `melt = DDF·(T − T₀)` for T above freezing, which gives a spring flood.
- Routing between reaches by a Muskingum-type delay, one state per reach.

### Reservoir and structures

- When the player closes a valley with a dam, the mod scans the terrain behind it and
  builds a volume–level curve. This is a bounded flood fill, done once and repeated when
  the structure changes.
- Balance: `dV/dt = Q_in − Q_turbine − Q_spill − evaporation − seepage`.
- Spillway and weirs: `Q = C_d · (2/3) · √(2g) · b · h^1.5`.
- Channels and canals: Manning, `v = R^(2/3) · S^(1/2) / n`.
- Head for the turbine is reservoir level minus tailwater; the rest is in
  [power-system.md](power-system.md).

### Water wheels

For the first era, alongside Create. Measured efficiencies **[S]**: well-designed
overshot wheels about 85 %, breastshot 75–80 %, modern undershot designs (Zuppinger,
Sagebien) from 71 % up to the mid 80s. Smeaton's eighteenth-century comparison was 65 %
for overshot against 25 % for undershot, which is the progression a tech tree can use.
Power is `η·ρ·g·Q·H` like any hydraulic machine.

### Cooling water

River temperature and flow set the condenser pressure of a thermal plant. In summer,
with warm low water, the plant loses output. The same quantity limits how much heat a
plant may reject. Ice in winter restricts intakes.

References: Chow, *Open-Channel Hydraulics*; Chow, Maidment and Mays, *Applied
Hydrology*; USBR, *Design of Small Dams*; Quaranta's review of gravity water wheels;
Müller and Kauppert on water wheel performance.

## Tides and the Moon

### What vanilla gives **[S]**

- The Moon has eight phases and changes phase once per day. A full cycle is eight
  in-game days, 2 h 40 min of play.
- The Moon is always opposite the Sun in the sky, whatever its phase. The phases are
  cosmetic, not geometric.

### Real tides **[S]**

- The principal lunar constituent M2 has a period of 12.42 h, the principal solar S2
  12.00 h.
- Equilibrium amplitudes are 0.24 m and 0.11 m, a ratio of 0.46.
- The two beat with a period of 14.77 days: spring tides when Moon and Sun are aligned
  (new and full moon), neap tides at the quarters.
- Real coasts amplify this by resonance and funnelling. The Rance estuary has a mean
  range of about 8 m (sources differ, up to 10.9 m).

### Mapping to the game

Because the Moon is always opposite the Sun, lunar and solar tides have the same period
in game time and never drift apart by geometry. The spring–neap cycle therefore has to
come from the phase index, not from sky positions. A proposal:

```
phase φ ∈ [0, 8), continuous, 0 = full moon, 4 = new moon
spring–neap factor   s(φ) = 1 + 0.46 · cos(2π·φ / 4)
tide height          η(t) = A_local · s(φ) · cos(4π·t_day / 24000 − lag)
```

- Two high waters per in-game day (semidiurnal), ten minutes apart in play.
- Springs at full and new moon (s = 1.46), neaps at the quarters (s = 0.54). The
  equilibrium ratio of spring to neap range is then 2.7; real coasts are usually closer
  to 2, so 0.46 can be lowered.
- `A_local` is a designed amplification by coast shape: small on open ocean, large in
  bays and estuaries.

Slack water and the strongest currents fall between high and low water, which is what a
tidal stream turbine needs: its power is `½·ρ·A·v³·C_p`, the wind formula with sea-water
density.

### Tidal power

Energy released by emptying a basin of area A through a range R **[D]**:

```
E = ½ · ρ · g · A · R²
```

For the Rance barrage (22 km², 8 m) this is about 7 × 10¹² J, 2 GWh, per emptying; the
plant is rated 240 MW and averages roughly a quarter of that **[S]**. The R² dependence
makes the site and the spring–neap cycle decisive.

### What can and cannot move

Changing ocean water blocks with the tide is not feasible: the volume is unbounded. The
proposal:

- a virtual sea level, known to machines and instruments everywhere;
- real block changes only in basins the player has enclosed with a barrage, where the
  volume is bounded and updates can be spread over ticks;
- optionally a client-side visual near the shore.

The same rule applies to reservoirs behind dams.

## Seasons

Serene Seasons is to be added as an optional dependency. The model should not call it
directly: `pc-sim` knows only a `SeasonProvider` interface, with one adapter for Serene
Seasons in `pc-mod` and a fallback (a fixed season or a simple calendar) when it is
absent. The interface also leaves room for other season mods; Ecliptic Seasons ships a
bridge that emulates the Serene Seasons API **[S]**. The exact API classes were not
confirmed and must be read from the mod's sources.

What the season drives:

| Quantity | Effect |
|---|---|
| Air temperature | Air density, condenser and transformer cooling, line ratings, heating demand, Cold Sweat |
| Snow and melt | River discharge with a spring flood, low winter flow |
| Wind climate | Stronger and gustier in the cold season |
| Sun elevation, day length | Solar output |
| Ice | Reservoir and intake restrictions, ice load on conductors and their sag |
| Mine ventilation | Direction and strength of natural draft |

## Order of work

1. The `Environment` interface and a constant implementation, so consumers can be
   written against it.
2. Wind: slow mean plus turbulence, height profile. Connect line cooling and chimney
   draft first, since they exist.
3. Seasons through `SeasonProvider`.
4. Discharge and reservoirs.
5. Tides.

## Sources

- [Van der Hoven, power spectrum of horizontal wind speed](https://www.taylorfrancis.com/chapters/edit/10.4324/9781315793245-21/power-spectrum-horizontal-wind-speed-frequency-range-0-0007-900-cycles-per-hour-isaac-van-der-hoven)
- [Wind power variability, review with the spectral gap](https://arxiv.org/pdf/2004.13469)
- [IEC 61400-1 overview, DTU](https://wasp.dtu.dk/software/windfarm-assessment-tool/iec-61400-1)
- [Normal turbulence model, Wind Energy Science](https://wes.copernicus.org/preprints/wes-2020-76/wes-2020-76-manuscript-version3.pdf)
- [Quaranta, gravity water wheels review](https://iris.polito.it/retrieve/handle/11583/2712517/207382/1-s2.0-S1364032118306178-main%20%281%29.pdf)
- [Müller, water wheels as a power source](https://steffenreichel.hier-im-netz.de/Muehlen/Infos/ModernWaterwheels.pdf)
- [Bosboom and Stive, *Coastal Dynamics*, tidal constituents](https://geo.libretexts.org/Bookshelves/Oceanography/Coastal_Dynamics_(Bosboom_and_Stive)/03%3A_Ocean_waves/3.07%3A_Generation_of_the_tide/3.7.6%3A_Tidal_constituents)
- [NOAA, harmonic constituents](https://tidesandcurrents.noaa.gov/about_harmonic_constituents.html)
- [Tidal power case studies, University of Strathclyde](https://www.esru.strath.ac.uk/EandE/Web_sites/01-02/RE_info/tidal1.htm)
- [MacKay, *Sustainable Energy – without the hot air*, tide chapter](https://climate.lifeitself.org/without-hot-air/chapG/)
- [Minecraft wiki, Moon (mirror)](https://breezewiki.discard.no/minecraft/wiki/Moon)
- [Ecliptic Seasons](https://modrinth.com/mod/ecliptic-seasons)
