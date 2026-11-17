# Physics audit of `pc-sim`

A review of the implemented models against the engineering literature. Baseline, path
conventions and evidence labels are in [README.md](README.md).

## How it was done

Three passes over about 40 000 lines:

1. Neutronics, nuclide, thermal, thermo and protection: read directly.
2. Grid, hydraulics, utilities, mechanics and the wear models in `ops`: a quicker second pass.
3. Metallurgy, combustion and mining: a quicker third pass.

The high-severity findings of passes 2 and 3 were re-derived from the formulas and
re-read in the code. Their medium and low findings carry the label **[A]**. Glue classes
(`*Unit`, `*Log`, `*Subsystem`), `automation` and `core` were not reviewed for physics.

Tests and the build were not run. Several findings name a test that pins the wrong
value; those tests will fail once the code is fixed and must change with it.

## Summary

| ID | Where | What is wrong |
|---|---|---|
| H1 | `thermal/HeatTransferCorrelations.java:174` | Minimum film boiling temperature is 273 K too high: Kelvin conversion applied twice |
| H2 | `grid/SyncScope.java:27` | Peak torque of out-of-phase closing is about half the real value and peaks at the wrong angle |
| H3 | `grid/PowerGrid.java:1127`, `:1315` | Synchronous machine has x_q = x′_d; a machine with no excitation stays in step |
| H4 | `mining/MethaneField.java:655` | Explosion energy has no oxygen limit |
| M1 | `neutronics/Westcott.java:25` | Xe-135 g-factors too high above 400 °C |
| M2 | `neutronics/LatticeModel.java:47` | Thermal cross sections are a mix of libraries, not ENDF/B-VII.1 as the comment says |
| M3 | `neutronics/DelayedNeutronData.java:31` | Photoneutron group split puts most of the yield in long-lived groups |
| M4 | `thermal/HeatTransferCorrelations.java:195` | Bromley coefficient 0.943 used with the Taylor wavelength (unconfirmed) |
| M5 | `metallurgy/BlastFurnace.java:196` | Ore oxygen is not balanced; η_CO and direct reduction are set independently |
| M6 | `metallurgy/BlastFurnace.java:212` | Heat of Si and Mn reduction missing from the hearth balance |
| M7 | `metallurgy/RollingMill.java:48` | Hot flow stress rises too steeply as temperature falls |
| M8 | `metallurgy/CastAlloy.java:13` | Room-temperature c_p used up to the melting point |
| M9 | `combustion/Combustor.java:107` | Unburnt carbon cuts the heat but not the products or the oxygen demand |
| M10 | `combustion/FlammabilityLimits.java:46` | Upper limit of inert-diluted gas clamps to 100 % |
| M11 | `grid/ConductorSpec.java:30` | Copper conductor ampacity is not the outdoor copper value |
| M12 | `grid/TransformerThermal.java:27` | Winding exponent of the power ONAN transformer |
| M13 | `hydraulics/LumpedFluidNetwork.java:804` | Pump losses leave the energy balance |

## High severity

### H1. Minimum film boiling temperature

**Where.** `thermal/HeatTransferCorrelations.java:174`

```java
if (pMPa <= 9) return 557.85 + 44.1 * pMPa - 3.72 * pMPa * pMPa + 273.15;
```

**Should be.** The Groeneveld–Stewart (1982) correlation, as quoted in Duarte's thesis
(eqs. 2-11 to 2-13) **[S]**:

- P ≤ 9 MPa, x ≥ 0: `T_MFB [°C] = 284.7 + 44.1·P − 3.72·P²`, P in MPa;
- P ≤ 9 MPa, x < 0: the same minus `x·10⁴ / (2.82 + 1.22·P)`, with
  `x = (h_l − h_l,sat)/h_fg`;
- P > 9 MPa: `T_MFB = ΔT_MFB(9 MPa)·(P_crit − P)/(P_crit − 9) + T_sat(P)`.

284.7 °C is 557.85 K. The constant in the code is already in kelvin, and 273.15 is added
to it a second time.

| P, MPa | Correct, K | Code, K |
|---|---|---|
| 0.1 | 562.2 | 835.4 |
| 5 | 685.4 | 958.5 |
| 7 | 684.3 | 957.4 |
| 9 | 653.4 | 926.6 |

**Effect.** `ConvectiveSurface.flux` switches to film boiling only at `tw >= tMin`
(line 189), stretches the transition-boiling interpolation up to `tMin` (lines 193–197)
and uses it for rewetting (line 233). After a boiling crisis the wall stays in the
high-flux transition regime 273 K longer than it should, so the cladding temperature
excursion is underestimated. The error is on the non-conservative side.

**Second point.** Above 9 MPa the code interpolates the temperature itself linearly to
T_crit. The correlation interpolates the superheat above T_sat. Both reach T_crit at
P_crit; the difference in between is a few kelvin once the constant is fixed.

**Tests that pin it.** `thermal/HeatTransferValidationTest.java:176` expects 835.3 K at
0.1 MPa. `thermal/HeatStructureBehaviourTest.java:202` expects
`tMinFilmBoiling(5) - 273.15 ≈ 685`: the number 685 is right, but it is kelvin, not
degrees Celsius.

**Fix.** Drop `+ 273.15`; interpolate the superheat above 9 MPa; correct both tests from
the correlation, not from the design document.

### H2. Peak torque of out-of-phase synchronising

**Where.** `grid/SyncScope.java:27`. The code computes `T_pk = E″·|E″ − V·e^(−jΔθ)| / X″`.

**Should be.** With the stator flux frozen at the instant of closing, the current is the
AC component plus a DC offset, `i(t) = i_ac·(1 − e^(−jωt))`, and the air-gap power is

```
P(t) = P0·(1 − cos ωt) + Q0·sin ωt
P0 = E″·V·sin Δθ / X″
Q0 = E″·(E″ − V·cos Δθ) / X″
```

Its maximum is `P0 + √(P0² + Q0²)`, that is

```
T_pk = (E″/X″)·(|E″ − V·e^(−jΔθ)| + V·|sin Δθ|)
```

**[D]**. For V = 0 this reduces to E″²/X″, the known peak torque of a three-phase short
circuit.

**Effect.** The code leaves out the unidirectional term. With E″ = V = 1:

| Δθ | Code, ·1/X″ | Correct, ·1/X″ |
|---|---|---|
| 10° | 0.17 | 0.35 |
| 30° | 0.52 | 1.02 |
| 60° | 1.00 | 1.87 |
| 120° | 1.73 | 2.60 |
| 180° | 2.00 | 2.00 |

The code is low by half at small angles and puts the worst case at 180°. The real worst
case for shaft torque is near 120°, which matches the published result that the greatest
mechanical hazard is at about 120° and the greatest thermal one at 180° (Gozdowiak
et al., *Energies* 13(20):5491) **[A]**.

**Tests that pin it.** `GridSynchronizationTest.java:101-102` (tag T7.6) **[A]**.

**Fix.** Add `+ v * Math.abs(Math.sin(angleRad))` inside the bracket and retune the
damage thresholds that were calibrated against the old values.

### H3. Synchronous machine: x_q equal to x′_d

**Where.** `grid/PowerGrid.java:1127-1129` injects the EMF `E′q` at the rotor angle δ
behind x′_d on both axes. `:1315-1317` advances `E′q` with
`T′d0·dE′q/dt = E_fd − E′q − (x_d − x′_d)·i_d`. `MachineParams` has no x_q at all.

**What it means.** With `E′q` alone behind x′_d the stator equation is
`V = E′q∠δ − j·x′_d·I`, which is a machine with x_q = x′_d. Its steady state is

```
P = E_fd·V·sin δ / x_d + (V²/2)·(1/x′_d − 1/x_d)·sin 2δ
```

**[D]**. The second term is a reluctance torque that a round-rotor machine does not
have, because there x_q ≈ x_d.

**Effect.**

- Amplitude of the spurious term: 1.72 pu for the turbogenerator preset (x_d 1.8,
  x′_d 0.25) and 1.42 pu for the shaft generator (2.0 and 0.30) **[D]**. With the field
  breaker open (E_fd = 0) the machine carries full load as a reluctance machine. A real
  one slips poles and runs as an induction generator.
- The loss-of-excitation relay (function 40) is implemented, but the trajectory it is
  meant to catch cannot develop.
- Rated load angle comes out near 10° instead of about 35° **[A]**.
- Field current at rated 0.8 power factor is 2.30 in the dynamic model, while
  `MachineParams.ratedFieldPu()` and the capability curve assume x_q = x_d and give
  2.53 **[A]**. The over-excitation limit therefore sits beyond the diagram.

**Fix.** Two-axis model: add the parameters x_q, x′_q, T′q0 and the state `E′d` with
`T′q0·dE′d/dt = −E′d + (x_q − x′_q)·i_q`, and inject `E′d + j·E′q` behind the transient
reactance. Choosing x′_q = x′_d keeps the Norton interface to the network as it is.
Reference: Sauer and Pai, *Power System Dynamics and Stability*; Kundur, ch. 5 **[M]**.

### H4. Methane and dust explosion energy

**Where.** `mining/MethaneField.java:655`

```java
double energy = cfg.explosionBlastFraction() * (xCh4 * vol * Q_CH4 + cd * vol / 1000 * Q_DUST);
```

**What is wrong.** Energy is counted for all the fuel present. The lines right below
(657–664) do an oxygen balance for the after-damp composition, but the energy does not
use it.

**Effect.** At 15 % CH₄ the code gives 0.15 × 35.8 = 5.37 MJ/m³. The air in that mixture
holds 0.85 × 20.95 % = 17.8 % O₂, enough for 8.9 % CH₄, or about 3.2 MJ/m³ **[D]**. Real
explosion pressure peaks near 10 % methane and falls toward the upper limit; the code
grows linearly through it. For dust there is no bound: 720 g/m³ gives 19.8 MJ/m³, 5.4
times what the oxygen supports **[A]**.

Related, from pass 3 **[A]**: `Q_CH4` is per normal cubic metre but is applied to
volume at node temperature (+7 % at 20 °C), and line 658 burns only the volatile matter
for the gas composition while the energy counts the whole dust mass.

**Tests that pin it.** `ExplosionFrontTest:75` **[A]**.

**Fix.** Cap the energy by the oxygen consumed, using the same `toCo`, `toCo2` and
hydrogen terms. A convenient constant is 13.1 MJ per kg of O₂ (Huggett). Use one fuel
basis for energy and stoichiometry, and convert to normal volume by 273.15/T.

## Medium severity

### M1. Xe-135 g-factors

`neutronics/Westcott.java:25`. The first three points agree with every source; the last
four do not.

| T, °C | 20 | 100 | 200 | 400 | 600 | 800 | 1000 |
|---|---|---|---|---|---|---|---|
| Code | 1.1581 | 1.2103 | 1.2360 | 1.2208 | 1.1634 | 1.0960 | 1.0333 |
| Computed **[D]** | 1.155 | 1.214 | 1.234 | 1.180 | 1.080 | 0.976 | 0.880 |
| Lamarsh table 3.2, as recalled **[M]** | 1.1581 | 1.2103 | 1.2360 | 1.1864 | 1.0914 | 0.9887 | 0.8858 |

The computed row integrates a single-level Breit–Wigner resonance at 0.0842 eV over a
Maxwellian, with the widths fitted to the first three points (Γγ = 0.082 eV,
Γn = 0.031 eV). Nominal widths (0.0906 and 0.0257 eV) give the same shape. The table in
Lamarsh was not opened; the calculation is the evidence.

Effect: xenon absorption is overstated by about 3 % at a neutron temperature of 350 °C
and by 10 % at 700 °C. The factor is declared but not used yet (`XE135_A` has no caller),
so the error is latent until xenon dynamics are written.

### M2. Thermal cross sections

`neutronics/LatticeModel.java:47-49`. The comment says "ENDF/B-VII.1, rounded". Compared
with the ENDF/B-VII.1 column of Pritychenko and Mughabghab **[S]**:

| Nuclide | Code σ_a | ENDF/B-VII.1 σ_γ + σ_f | Difference |
|---|---|---|---|
| U-235 | 680.9 | 98.69 + 585.0 = 683.7 | −0.4 % |
| U-236 | 5.1 | 5.13 | ok |
| U-238 | 2.68 | 2.683 | ok |
| Np-237 | 175.9 | 175.4 + 0.02 | +0.3 % |
| Np-239 | 45 | 45.0 | ok |
| Pu-238 | 558 | 412.9 + 17.8 = 430.7 | +30 % |
| Pu-239 | 1017.3 | 270.7 + 747.9 = 1018.6 | −0.1 % |
| Pu-240 | 289.5 | 287.6 + 0.06 | +0.6 % |
| Pu-241 | 1373 | 363.0 + 1012 = 1375 | −0.1 % |
| Pu-242 | 18.5 | 21.3 | −13 % |
| Am-241 | 603 | 684.3 + 3.1 = 687.4 | −12 % |
| Am-243 | 79 | 80.4 + 0.08 | −2 % |
| Cm-242 | 20 | 19.1 + 4.7 = 23.8 | −16 % |
| Cm-244 | 15 | 15.2 + 1.0 = 16.3 | −8 % |

The U-235 value 680.9 is the older figure from Lamarsh; Pu-238 and Pu-242 match the
*Atlas of Neutron Resonances*; Cm-242 and Cm-244 look like capture alone entered as
absorption. The U-235 difference shifts η by about +0.15 %. The others matter only for
burnt or mixed-oxide fuel.

Fix: take every row from one library and say which, or generate the table (see
[tech-stack.md](tech-stack.md)).

For later use, same source: Xe-135 capture 2.665 × 10⁶ b, Sm-149 capture 4.051 × 10⁴ b.

### M3. Photoneutron groups

`neutronics/DelayedNeutronData.java:31-32` uses three groups with half-lives 2.5 min,
1.1 h and 12.8 d and shares 0.6, 0.3, 0.1.

The measurements behind the standard tabulation give eight groups from 2.5 s to 53 h, the
two shortest holding about 85 % of the photoneutrons, plus a low-yield ninth group at
12.8 d **[S]** (abstracts only: Bernstein et al. 1947, and a Canadian Nuclear Society
paper on Keepin's tabulation).

Effect: in a heavy-water or beryllium core, 10 % of the photoneutron source decaying
with a 12.8-day half-life keeps the shutdown neutron level far above reality for weeks.
If the split is a deliberate balance value it should be marked as one.

### M4. Film boiling coefficient

`thermal/HeatTransferCorrelations.java:195` uses 0.943 with the characteristic length
capped at the Taylor wavelength; the Javadoc calls it "modified Bromley, as in TRACE".

The replacement of the length by the critical wavelength for vertical surfaces is
confirmed (Leonard et al.) **[S]**. The coefficient that goes with it is recalled as
0.62 **[M]**, which would make the code 52 % high, but no source giving the coefficient
could be opened. Treat this as a question to settle against the TRACE theory manual,
not as a confirmed error.

### M5. Blast furnace oxygen balance **[A]**

`metallurgy/BlastFurnace.java:196-199`, also `CarbonBalance.java:43-44`. The gas
utilisation η_CO = 0.47 and the direct-reduction degree r_d = 0.4 are imposed
independently, and the oxygen of the ore is never balanced. At the 1100 Nm³/t point the
top gas carries 40.97 kmol O against 44.74 kmol supplied by blast and ore: 60 kg of
oxygen per tonne of hot metal disappears (88 kg for hematite). Closing the balance needs
η_CO = 0.605 at r_d = 0.4, or r_d = 0.62 at η_CO = 0.47. `DUST_LOSS = 0.08` (34 kg C/t)
compensates. Pinned by `BlastFurnaceTest:100,138`.

Fix: compute CO₂ from the ore oxygen (O_ore − O_direct − O_H₂), report η_CO as a result,
then retune r_d to about 0.5–0.55 and the dust loss to about 0.02. Reference: Rist
operating line; Peacey and Davenport, *The Iron Blast Furnace*.

### M6. Heat of silicon and manganese reduction **[A]**

`metallurgy/BlastFurnace.java:212-214`. The carbon for Si and Mn reduction is consumed,
but its heat is not in `qOut`. SiO₂ + 2C → Si + 2CO takes 689.8 kJ/mol, 24.56 MJ per kg
of Si (NIST formation enthalpies, opened in pass 3). At 0.6 % Si that is
147 MJ per tonne of hot metal, about 4 % of the hearth heat. Fix: add
`siKg·24.6e6 + mnKg·5.0e6` to `qOut`.

### M7. Hot flow stress in rolling **[A]**

`metallurgy/RollingMill.java:48` doubles the flow stress for every 100 K of cooling:
240 MPa at 1000 °C, 441 MPa at 900 °C, 60 MPa at 1200 °C. Misaka's mean flow stress for
0.15 % C steel gives about 119, 148 and 84 MPa, a factor near 1.2 per 100 K **[M]**.
`RollingAndPipesTest:113` pins 240 MPa. Either use an Arrhenius form such as
`120 MPa·exp(3100·(1/T − 1/1373))` or mark the law as a balance value.

### M8. Specific heat of cast alloys **[A]**

`metallurgy/CastAlloy.java:13-14` uses the room-temperature c_p as the mean up to
melting. Integrating the NIST Shomate fits: copper needs 466.9 kJ/kg from 298 to 1358 K
(mean 440 J/kg·K, code 385); aluminium 666.5 kJ/kg from 298 to 933 K (mean 1048, code
900). Heat to melt from 20 °C is 8.9 % low for both. Suggested means: Cu 440, Al 1050,
steel about 690, bronze about 420.

### M9. Unburnt carbon **[A]**

`combustion/Combustor.java:107-122`. The mechanical loss q₄ reduces the heat released,
but the products are still built from fully burnt carbon. For grate coal with q₄ = 5 %
about 6 % of the carbon is counted as CO₂ and as oxygen demand while it should leave as
solid residue. Fix: unburnt carbon = q₄·LHV / 32.76 MJ/kg; remove it from products and
air demand, add it to ash and particulates.

### M10. Flammability of inert-diluted gas **[A]**

`combustion/FlammabilityLimits.java:46` divides the Le Chatelier upper limit by the
combustible fraction. For blast furnace gas this gives 296 %, clamped to 100 %, so pure
gas with no air counts as explosive in the boiler check. The literature range is roughly
35–74 % **[M]**. Fix: pair inerts with combustibles (Coward and Jones), or cap the upper
limit so the mixture stays above the limiting oxygen concentration.

### M11. Bare conductor ampacity **[A]**

`grid/ConductorSpec.java:29-35` gives M-35 as 170 A and M-70 as 265 A. Table 1.3.29 of
the PUE gives 223 A and 337 A for copper outdoors; the ACSR entries in the same file do
use the outdoor column. Related, `ConductorSpec.java:78`: one convection coefficient for
every diameter, where IEEE 738 gives a dependence close to d^−0.48 **[M]**.

### M12. Transformer winding exponent **[A]**

`grid/TransformerThermal.java:26-28`. `onanPower()` uses y = 1.6. IEC 60076-7 gives 1.6
for distribution transformers and 1.3 for medium and large ONAN units **[M]**. At a load
factor of 1.5 the hot-spot gradient is 49.7 K instead of 44.0 K and ageing runs 1.9
times faster. Test T7.5 (`GridLinesAndTransformersTest.java:106`) pins the value.

### M13. Pump energy balance **[A]**

`hydraulics/LumpedFluidNetwork.java:804-805`. The fluid receives ṁ·Δp/ρ while the shaft
pays T_hyd·ω (`PumpModel.java:105`). The loss share (1/η − 1) disappears, and for
reverse flow against positive head the fluid term goes negative while the shaft still
puts power in. Fix: deposit T_hyd·ω in the fluid.

## Low severity

Deviations of a few percent, wrong attributions, numerical details.

Neutronics and thermal:

- `neutronics/Medium.java:17`: beryllium σ_a 7.6 mb; ENDF/B-VII.1 gives 10.0 mb, the
  Atlas 8.49 mb **[S]**. It changes the diffusion length in beryllium.
- `neutronics/DelayedNeutronData.java:20`: Pu-241 λ₂ = 0.0297 s⁻¹, recalled as
  0.0299 **[M]**.
- `neutronics/LatticeModel.java:326-328`: bare extrapolation distance taken as 2D; the
  comment's own 0.71·λ_tr is 2.13D **[D]**.
- `neutronics/CladMaterial.java:9`: `STEEL` uses the absorption of pure iron (2.56 b);
  stainless steel is higher because of chromium, nickel and manganese **[M]**.
- `thermo/VoidFraction.java:12-13`: C₀ = 1.13 is attributed to Ishii 1977; the value is
  Zuber and Findlay's **[M]**.

Grid, hydraulics, utilities **[A]**:

- `grid/PowerGrid.java:1054-1056`: single exponentials for oil and winding. IEC 60076-7
  uses k11·τ_o and a two-term winding function with k21, k22.
- `grid/PowerGrid.java:1053`: load factor K = |S|/S_r; the standard uses I/I_r, which
  differs by 23 % in losses at 0.9 pu voltage.
- `grid/PowerFlow.java:11`: absolute tolerance 10⁻¹¹ pu with a forward-difference
  Jacobian; for very short lines rounding reaches the tolerance and the solver reports
  non-convergence. Scale the tolerance by the largest admittance.
- `grid/InductionMotorSpec.java:66-69`: rated torque based on P_n/ω_s, so rated shaft
  output is (1 − s_n)·P_n.
- `hydraulics/HomologousCurve.java:11`: 36 points at 10°; the rated point at 225° falls
  between nodes (h(1,1) = 1.0015). Wylie and Streeter use 89 points.
- `utilities/WaterQuality.java:27`: `ACID_CONDUCTANCE` 0.035 is the H⁺ ion alone; HCl is
  426 S·cm²/eq, so cation conductivity reads 18 % low.
- `utilities/WaterTreatmentTrain.java:43`: deaerator steam fixed at 2 % of water, while
  `Deaerator.waterPerSteam` gives about 15 % for heating from 25 to 104.8 °C.

Metallurgy, combustion, mining **[A]**:

- `metallurgy/BlastFurnace.java:39`: `DIRECT_HEAT` 12.7 MJ/kg C; from NIST 298 K
  enthalpies it is 13.45. The value in the code is a 1000 °C figure mixed with 298 K
  terms.
- `combustion/FuelGas.java:10-11`: propane and butane heating values are real-gas
  figures divided by ideal-gas density, 2.2 % and 4.2 % high.
- `mining/RopeConstruction.java:11-12`: rope factors differ from EN 12385-4.
- `mining/MethaneField.java:30`: CO per O₂ consumed of 0.01 for ordinary oxidation;
  Graham's ratio up to 0.4 % is normal and about 1 % already signals heating.
- `metallurgy/HeatTreatment.java:38-39`: solution anneal of maraging steel set at
  1030 °C or more; standard practice is about 820 °C.
- `metallurgy/BlastFurnace.java:461`: comment says 125 kJ/kg where the formula gives 128
  to 137.

## Confirmed correct

Checked in the first pass:

- Six-group delayed neutron data for U-235, U-238 and Pu-239 match Keepin's
  tables **[M]**.
- Hellstrand resonance integrals and Doppler coefficients for UO₂ and uranium metal.
- Thermal utilisation with the Bessel-function shielding and excess-absorption terms
  (Lamarsh); resonance escape; reflector savings; geometric buckling.
- Moderator data: diffusion coefficients, ages, and the density scaling of graphite from
  1.60 to 1.65 g/cm³.
- Hydrogen, oxygen, carbon, sodium, aluminium and zirconium absorption against the
  ENDF/B-VII.1 table **[S]**; natural boron at 767 b **[M]**.
- Point kinetics: the exponentially fitted scheme, the series for small arguments, and
  the equilibrium initialisation.
- Nodal solver: the Marshak boundary term D/(ℓ + 2D), Wielandt shift on the correct
  side, Rayleigh quotient for a symmetric operator; first-order perturbation weights.
- Dittus–Boelter, the Weisman bundle factor, Chen (F, S and Forster–Zuber), Cooper, the
  SI form of W-3, Zuber's pool CHF, Dougall–Rohsenow, Nusselt condensation.
- UO₂ conductivity and heat capacity (Fink 2000), melting point and latent heat;
  Zircaloy conductivity and the α–β heat capacity peak (MATPRO); gap gas conductivities;
  the Vitanza threshold.
- NASA-7 coefficients for N₂, O₂, H₂, H₂O and CO₂.
- IAPWS-IF97: `If97VerificationTest` checks the standard's own tables 5, 15 and 33 and
  the 2008, 2011 and 2014 transport and surface-tension releases.
- Drift-flux void fraction; the cosine axial shape in the DNBR calculator.

Reported correct in passes 2 and 3 **[A]**:

- Inverse-time relay constants 0.14/0.02, 13.5/1, 80/2 (IEC 60255-151); loss-of-field
  offset mho; Kloss torque curve; symmetrical-component fault connections; the swing
  equation and J = 2HS/ω_m²; per-unit conversion of transformer data.
- Haaland friction factor with the 2300–4000 transition; Friedel two-phase multiplier;
  Joukowsky pressure rise; the conservative mass and energy update of the fluid network.
- Weibull life maths; air composition and the specific energy of air separation;
  Langelier index.
- Reaction heats in the converter (C→CO 9.20, C→CO₂ 32.76, Si 32.4, Fe 4.87 MJ/kg);
  the [C][O] product; the liquidus slope; coke battery yields summing to 100 %.
- The Gurvich furnace method; the Zeldovich activation temperature; combustion
  stoichiometry and mass balance; Mendeleev's formula.
- ASME B31G including the long-defect branch; the closed-form Paris integral.
- Atkinson resistance and the loop solver of mine ventilation; the methane limits;
  boiler explosion energy.

## Known simplifications worth writing down

From passes 2 and 3 **[A]**. None is an error; each should be visible to players
or contributors somewhere.

Grid:

1. Faults use z₂ = z₁ and a fixed z₀ ratio; no DC offset, no IEC 60909 voltage factor.
2. The distance relay uses positive-sequence quantities and underreaches on unbalanced
   faults.
3. Machines have no saturation and no stator resistance; reactances are not scaled with
   frequency.
4. Motor starting current and Kloss torque come from different equivalent circuits.
5. The over-excitation limit is tighter than IEEE C50.13.
6. Line resistance is fixed at 20 °C; reactance constants are for 50 Hz.

Hydraulics and utilities:

7. No NPSH or cavitation anywhere; the default homologous curve is a declared stub.
8. Water hammer is an event estimate, not a wave solution.
9. Fixed 15 µg/kg oxygen after the deaerator; cation-only treatment train; no blowdown
   model.
10. The plain bearing uses rolling-bearing life statistics, no Sommerfeld or Stribeck.

Metallurgy, combustion, mining:

11. Thermal NOx keeps the 38 370 K activation with a √O₂ term and no dissociation.
12. Blast furnace: hydrogen leaves unreacted; limestone CO₂ does not attack coke.
13. Converter: off-gas sensible heat and slag formation are folded into 298 K values.
14. Ladle deoxidation uses fixed oxygen floors.
15. Rock dusting: 80 % inert only at high volatile matter, where 30 CFR 75.403 requires
    80 % everywhere.
16. After-damp fractions are not renormalised after water condenses.
17. Gas exposure uses a 30-minute time constant against a human half-life of hours.
18. Coking time goes as width squared; practice is closer to the power 1.2–1.6.
19. Mine air density is taken at surface pressure whatever the depth.
20. Hoist safety factors include acceleration; the rules use static load.

## Promised in the README, absent from `pc-sim`

- Xenon and samarium dynamics. Only the inputs exist (`CellFeedbackState.xenonAbs`,
  `samariumAbs`).
- Decay heat.

When they are written, the data should come from an evaluated library, not from a
textbook table. Starting points **[M]**: I-135 half-life 6.57 h, Xe-135 9.14 h,
Pm-149 53.1 h; cumulative yields from the fission-yield files; decay heat from
ANSI/ANS-5.1. The thermal capture cross sections are in M2 above.

## Not checked

- Medium and low findings labelled **[A]**, and the Bromley coefficient (M4).
- Normative values that could not be opened: hoist rope safety factors and
  sheave ratio, NFPA 85 purge timings, the IEC 60076-7 table, IEEE 1243, GOST test
  voltages and conductor data.
- `ComplexSparseLU` pivoting, the EOS derivatives in `thermo`, the critical-flow tables.
- Everything in `automation`, `core`, `plant`, and the `*Unit` glue.

## The common cause, and the order of repair

The same thing lies behind H1, H2, M5, M7 and M12: the test expects the number the
design document gives, so code and test agree with each other and with nothing else. The
IF97 tests are the counter-example: they check against the standard's own tables and a
CoolProp reference, and that part of the code has no findings.

Suggested order:

1. H1 to H4, each with a regression test whose expected value comes from the source
   cited here.
2. M1 to M3 before xenon and photoneutron behaviour is built on them.
3. M5 and M6 together, since the dust-loss constant has to be retuned once.
4. The rest as the affected machines are next touched.

## Sources

Opened in the first pass:

- [Duarte, *Understanding the Minimum Film Boiling Temperature in Two-Phase Flow*, PhD thesis, UW–Madison, 2018](https://asset.library.wisc.edu/1711.dl/DWROWJG4AHS3B8F/R/file-41954.pdf)
- [Pritychenko and Mughabghab, thermal cross sections and Westcott factors from evaluated libraries](https://arxiv.org/pdf/1208.2879)
- [Bernstein et al., photoneutrons from fission products in heavy water (abstract)](https://scilit.com/publications/4ef27dc608f11ca3091ceabbf3f027e7)
- [CNS paper noting the low-yield 12.8-day group](https://proceedings.cns-snc.ca/index.php/pcns/article/download/3042/3041/3077)
- [Modified Bromley correlation, lecture notes](https://ocw.snu.ac.kr/sites/default/files/NOTE/Lecture06-4.pdf)

Cited from passes 2 and 3:

- [Gozdowiak et al., out-of-phase synchronising, *Energies* 2020](https://mdpi.com/1996-1073/13/20/5491)
- [Sauer and Pai, *Power System Dynamics and Stability*](https://courses.physics.illinois.edu/ece576/sp2018/Sauer%20and%20Pai%20book%20-%20Jan%202007.pdf)
- [PUE, table 1.3.29](https://dnaop.com/html/46303_7.html)
- [NIST Chemistry WebBook](https://webbook.nist.gov)
- [Huggett, heat release per unit of oxygen](https://nvlpubs.nist.gov/nistpubs/sp958-lide/280-282.pdf)
- [Rutkowski and Szczygieł, the Gurvich method](https://czasopisma.pan.pl//Content/121375/PDF/art01_final.pdf)
- [ANSYS Fluent theory guide, thermal NOx](https://www.afs.enea.it/project/neptunius/docs/fluent/html/th/node212.htm)
- [30 CFR 75.403, rock dusting](https://www.law.cornell.edu/cfr/text/30/75.403)

Books named as references, not opened: Lamarsh, *Introduction to Nuclear Reactor
Theory*; Keepin, *Physics of Nuclear Kinetics*; Todreas and Kazimi, *Nuclear Systems*;
Kundur, *Power System Stability and Control*; Wylie and Streeter, *Fluid Transients in
Systems*; Geerdes et al., *Modern Blast Furnace Ironmaking*; Turkdogan, *Fundamentals of
Steelmaking*; McPherson, *Subsurface Ventilation Engineering*.
