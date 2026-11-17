# Stack: is anything needed besides Java?

Short answer: at runtime, no. Outside the game, yes: a second language is worth having
as the source of reference values. Evidence labels are explained in [README.md](README.md).

## Runtime: Java 21 only

Minecraft 1.21.1 runs on Java 21, and each alternative fails for a concrete reason.

| Option | Why it does not fit a distributed mod |
|---|---|
| Vector API (SIMD) | Still an incubator module in JDK 21 (JEP 448, sixth incubator) **[S]**. It needs `--add-modules jdk.incubator.vector` on the JVM command line, which a mod cannot make players pass |
| Panama, Foreign Function & Memory API | Third preview in JDK 21 (JEP 442) **[S]**, so it needs `--enable-preview`. Final only in JDK 22 (JEP 454) **[M]** |
| JNI with C, C++ or Rust | Works on Java 21, but means shipping native libraries for five platforms (Windows x64, Linux x64 and arm64, macOS x64 and arm64), and any defect in them takes the whole server JVM down |
| GPU compute | Dedicated servers have no GPU, and the simulation is server-side |

The workload is ordinary differential equations and sparse networks of a few hundred
nodes. Allocation-free Java handles that class of problem; HotSpot's C2 compiles scalar
`double` loops to code close to what a C compiler gives.

What already exists in `pc-sim`:

- five JMH benchmarks under `pc-sim/src/jmh`: `FluidNetworkBench`, `MineAtmosphereBench`,
  `PointKineticsBench`, `PowerGridBench`, `ReactorFastBench`;
- `core/exec/WorkerPool`, the one place that creates threads, for SLOW and STORAGE jobs
  whose results merge on a fixed tick so the outcome does not depend on thread count;
- build-time generated tables for IAPWS-IF97 and for critical two-phase flow.

The benchmarks were **not run** for these notes, so real milliseconds per tick are
unknown. The budget in the README is 3 ms per tick at p95 for the reference complex.

### If the budget is ever exceeded

In this order:

1. Algorithm. Reuse the sparse factorisation while the topology is unchanged, put quiet
   islands and sleeping network regions to rest, replace iteration with a lookup.
2. Precomputed tables, the pattern already used for IF97. Candidates: the 2006 CHF
   look-up table (the code notes it is missing, `thermal/HeatTransferCorrelations.java:150`),
   cross sections against temperature, `C_p(λ, β)` surfaces for wind rotors, hill charts
   for hydro turbines.
3. More work on `WorkerPool`.
4. Native code: not at all.

None of the features in the other notes changes this. Wind is a noise field with a
filter, hydrology is a handful of balance equations, the chip chain is closed-form
diffusion profiles and yield formulas.

## Outside the game: Python as the source of reference values

The repository already does this for water and gases: `pc-sim/tools/if97_reference.py`
and `pc-sim/tools/ideal_gas_reference.py`. The pattern is worth extending, for a reason
the audit made plain: almost every error it found is pinned by a test whose expected
number came from the same design document as the code. A test that restates the design
cannot catch a mistake in the design.

The pattern:

1. A Python script computes reference values with an established library.
2. The values are written to a file that is committed, or generated in CI.
3. A JUnit test compares the Java implementation against that file with a stated tolerance.

Nothing from Python ships in the jar.

| Domain | Reference tool | What it replaces or checks |
|---|---|---|
| Water and steam | CoolProp (IAPWS-95 and IF97) | Property tables, saturation line, transport properties |
| Combustion and gases | Cantera | NASA polynomial data, adiabatic flame temperature, equilibrium composition, heating values |
| Neutronics | OpenMC | k∞ of a lattice, temperature coefficients, spectrum-averaged cross sections and g-factors. Replaces tables typed in by hand |
| Power grid | pandapower or PyPSA | Load flow, short-circuit currents to IEC 60909 |
| Stiff ODEs | SciPy `solve_ivp` (Radau, BDF) | Reference solutions for point kinetics and the machine equations |
| Wind | NREL TurbSim and OpenFAST | Turbulent wind time series with the IEC spectra, rotor curves |
| Solar | pvlib | Irradiance transposition, cell temperature, single-diode model |
| Thermal cycles | TESPy | Rankine and Brayton cycle balances |
| Dynamic plant models | OpenModelica | Swing equation, drum boiler, hydraulic transients |
| Semiconductor devices | DEVSIM, ngspice | Device curves and circuit behaviour for a given process |

Both existing scripts use CoolProp and write CSV files to
`pc-sim/src/test/resources/validation/`; the rest of the table is a proposal. Package
names are from memory **[M]**; confirm each exists and is maintained before depending on it.

## What stays as it is

- `pc-sim` keeps zero runtime dependencies.
- Lua on CC: Tweaked stays the player-facing automation language. It is not a
  computation layer for the simulation.
- Client-side effects (glow, haze, particles) are shaders and rendering, and are
  unrelated to this question.

## Sources

- [JEP 448, Vector API in JDK 21](https://inside.java/2023/05/03/jep-448-proposed-to-target-jdk21)
- [Java 21 feature list, InfoQ](https://www.infoq.com/news/2023/09/java-21-so-far)
