package dev.denfry.promptcritical.sim.accident;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import java.util.SplittableRandom;

/**
 * Core melt progression of a light-water reactor past the cladding melting point (04c §4c.10), simplified to one
 * lumped core and one lower head. {@link PlantAccident} stops at the cladding melting point; this model carries on from
 * there on the decay heat of the uncovered core.
 *
 * <p><b>In the core</b> (§4c.10.1). The degrading core heats on its fuel and cladding heat capacity: from 2098 K the
 * unoxidised zirconium melts and candles down ({@code CLAD_MELT_CANDLING}, relocated within the core height at
 * 0.1 m/s, {@code accident.melt_relocation_speed}); at 2800 K the (U,Zr)O₂ ceramic melts into a pool
 * ({@code CERAMIC_MELT_POOL}) taking 0.36 MJ/kg (§4c.10.4). Loss of geometry Γ = (relocated + molten)/core mass
 * (§4c.10.2). Water back over the core removes heat as Γ allows: a rod bundle under water is quenched, a debris bed
 * gives up to its dry-out flux, a pool only what its crust passes ([упрощение] 0.6 / 0.3 MW/m² over the core
 * cross-section, inside the 0.3–1 and 0.2–0.5 MW/m² ranges of §4c.10.2; the bundle quenches in about a minute).
 *
 * <p><b>Lower plenum</b>. The crust fails once {@link #CRUST_FAILURE_MELT} of the core is molten [оценка по TMI-2:
 * ≈ 45 % of the core melted, ≈ 20 t reached the lower plenum]; from then on the melt drains into the lower plenum as it
 * forms. Melt pouring into water may trigger a steam explosion ({@link ReactivityInitiatedAccident#fciEnergyJ}, p = 0.1).
 *
 * <p><b>Lower head</b> (§4c.10.3). The corium sends {@code f_dn} = 0.4 of its decay heat into the hemispherical head.
 * With the cavity flooded outside, boiling takes up to 1.2 MW/m² ({@code accident.ivr_external_chf}) — below that the
 * vessel holds (in-vessel retention). Otherwise the wall heats as one slab and fails by creep at 1300 K above 10 MPa,
 * 1500 K below 1 MPa ({@code accident.lower_head_fail_temp_*}, linear between). Failure at high pressure blows the melt
 * out as droplets: 0.2 of its heat goes straight into the containment gas (direct containment heating,
 * {@code accident.dch_energy_fraction}).
 *
 * <p><b>On the concrete</b> (§4c.10.5). The corium ablates the floor under it,
 * {@code V̇ = q·A/(ρ_c·h_abl)}, 2300 kg/m³ × 2 MJ/kg = 4.6 GJ per block. Water over the melt takes 0.4 MW/m² through
 * its crust [середина диапазона 0.2–0.5] from a spread of {@code m/(8000 kg/m³ · 0.5 m)}, at least one block; the
 * rest goes into the concrete [упрощение, T-4c-16: no losses upward without water]. The concrete's water reaches the
 * remaining metal of the melt as hydrogen [оценка: 5 wt-% water in concrete, all of it reduced].
 */
public final class CoreMeltProgression {
    /** Stages of §4c.10.1 ({@code CoreDegradationStage}). */
    public enum Stage {
        INTACT, CLAD_BALLOONING_BURST, RAPID_OXIDATION, CONTROL_MATERIAL_RELOCATION, CLAD_MELT_CANDLING, CERAMIC_MELT_POOL,
        LOWER_PLENUM_RELOCATION, LOWER_HEAD_FAILURE, EX_VESSEL_MCCI
    }

    /** Zircaloy melts and candles (§4c.10.1: 2098–2123 K). */
    public static final double CANDLING_K = 2098;
    /** (U,Zr)O₂ ceramic melts (§4c.10.1: ≈ 2800 K). */
    public static final double CERAMIC_MELT_K = 2800;
    /** Latent heat of the oxide melt, J/kg (§4c.10.4). */
    public static final double FUSION_J_KG = 0.36e6;
    /** Relocation speed of molten material, m/s ({@code accident.melt_relocation_speed}) [BALANCE]. */
    public static final double RELOCATION_M_S = 0.1;
    /** Molten share of the core at which the crust gives way. */
    public static final double CRUST_FAILURE_MELT = 0.4;
    /** Share of the corium's decay heat into the lower head (§4c.10.3). */
    public static final double F_DOWN = 0.4;
    /** External critical heat flux of a flooded cavity, W/m² ({@code accident.ivr_external_chf}) [BALANCE]. */
    public static final double IVR_CHF_W_M2 = 1.2e6;
    /** Creep failure temperature of the head above 10 MPa and below 1 MPa, K [BALANCE]. */
    public static final double FAIL_HIGH_P_K = 1300, FAIL_LOW_P_K = 1500;
    /** Direct containment heating share of the ejected melt's heat ({@code accident.dch_energy_fraction}) [BALANCE]. */
    public static final double DCH_FRACTION = 0.2;
    /** Primary pressure above which a failing head ejects the melt as droplets (high-pressure melt ejection), Pa [оценка]. */
    public static final double HPME_PA = 2e6;
    /** Concrete: density, kg/m³, and ablation enthalpy, J/kg (§4c.10.5). */
    public static final double CONCRETE_KG_M3 = 2300, ABLATION_J_KG = 2.0e6;
    /** Energy to ablate one block (1 m³) of concrete, J. */
    public static final double BLOCK_J = CONCRETE_KG_M3 * ABLATION_J_KG;
    /** Heat a crust under water passes up from the melt, W/m² [середина 0.2–0.5 MW/m², §4c.10.5]. */
    public static final double CRUST_W_M2 = 0.4e6;
    /**
     * Coolability of the in-core debris under water by Γ (§4c.10.2): a rod bundle (Γ &lt; 0.1) quenches on its whole rod
     * surface [оценка: τ = 60 s]; a debris bed passes its dry-out flux, a pool what its crust lets through, W/m² over the
     * core cross-section [упрощение: 0.6 and 0.3 MW/m², inside 0.3–1 and 0.2–0.5].
     */
    static final double QUENCH_S = 60, BED_W_M2 = 0.6e6, POOL_W_M2 = 0.3e6;
    /** Corium spread: density, kg/m³, and depth, m [оценка]. */
    public static final double CORIUM_KG_M3 = 8000, SPREAD_DEPTH_M = 0.5;
    /** Water in concrete, kg/kg, all reduced to hydrogen by the melt's metal [оценка]. */
    public static final double CONCRETE_WATER = 0.05;
    /** Steel of the lower head: density × specific heat, J/(m³·K). */
    private static final double STEEL_J_M3K = 7800 * 500;

    /**
     * Vessel geometry.
     *
     * @param innerRadiusM     inner radius of the vessel and its hemispherical lower head
     * @param headThicknessM   lower head wall
     * @param coreHeightM      active fuel height (relocation time)
     */
    public record Vessel(double innerRadiusM, double headThicknessM, double coreHeightM, double coreRadiusM) {
        public double headAreaM2() {
            return 2 * Math.PI * innerRadiusM * innerRadiusM;
        }

        public double coreAreaM2() {
            return Math.PI * coreRadiusM * coreRadiusM;
        }

        public double headHeatCapacityJK() {
            return headAreaM2() * headThicknessM * STEEL_J_M3K;
        }
    }

    /**
     * T2a VVER, 100 MWt: 19 VVER-440 assemblies on a 147 mm pitch (core radius 0.34 m, 2.42 m high) in a vessel of
     * 0.6 m inner radius with a 140 mm lower head (VVER-440's head scaled down; [оценка]).
     */
    public static final Vessel PWR_T2A = new Vessel(0.6, 0.14, 2.42, 0.34);
    /** T2b BWR, 100 MWt: 3.7 m core, larger vessel with steam separators, 1.0 m inner radius, 150 mm head [оценка]. */
    public static final Vessel BWR_T2B = new Vessel(1.0, 0.15, 3.7, 0.45);

    /** The vessel of a plant design, or null (channel reactors have no lower head). */
    public static Vessel forDesign(String id) {
        return switch (id) {
            case "pwr" -> PWR_T2A;
            case "bwr" -> BWR_T2B;
            default -> null;
        };
    }

    private final Vessel v;
    private final double uo2Kg, zrKg, heatJK, coreKg;

    private Stage stage = Stage.INTACT;
    private boolean started, cavityFlooded, vesselFailed;
    private double debrisK, meltKg, debrisKg, candledKg, plenumKg, exKg, headK = 560, ablationJ, ablatedM3, h2Kg;

    // per-step events
    private boolean stageEvent, pourEvent, failureEvent, ivr;
    private double fciJ, dchJ, pourKg, ejectedKg;
    private int ablatedBlocks;

    /**
     * @param uo2Kg  fuel in the core
     * @param zrKg   cladding zirconium
     * @param heatJK heat capacity of fuel and cladding, J/K
     */
    public CoreMeltProgression(Vessel vessel, double uo2Kg, double zrKg, double heatJK) {
        this.v = vessel;
        this.uo2Kg = uo2Kg;
        this.zrKg = zrKg;
        this.heatJK = heatJK;
        this.coreKg = uo2Kg + zrKg;
    }

    /**
     * A melt of {@code coriumKg} already through the vessel and on the concrete (T-4c-16; a corium pour handed over
     * on its own).
     */
    public static CoreMeltProgression onConcrete(double coriumKg) {
        CoreMeltProgression m = new CoreMeltProgression(PWR_T2A, coriumKg, 0, coriumKg * 300);
        m.started = true;
        m.vesselFailed = true;
        m.exKg = coriumKg;
        m.debrisK = CERAMIC_MELT_K;
        m.stage = Stage.EX_VESSEL_MCCI;
        return m;
    }

    public Vessel vessel() {
        return v;
    }

    /** Water stands in the reactor cavity around the lower head (in-vessel retention, ex-vessel quench). */
    public void setCavityFlooded(boolean on) {
        cavityFlooded = on;
    }

    public boolean cavityFlooded() {
        return cavityFlooded;
    }

    /**
     * Records the stages {@link PlantAccident} itself resolves (burst, rapid oxidation, the absorber melting at 1073 K).
     */
    void observeCladding(double peakCladK) {
        if (stage.ordinal() >= Stage.CLAD_MELT_CANDLING.ordinal()) return;
        Stage s = stage;
        if (peakCladK > 1500) s = Stage.RAPID_OXIDATION;
        else if (peakCladK > PlantAccident.BURST_K && s.ordinal() < Stage.CLAD_BALLOONING_BURST.ordinal()) s = Stage.CLAD_BALLOONING_BURST;
        // Ag-In-Cd melts at 1073 K, the B4C–steel eutectic at about 1500 K: past rapid oxidation the absorber is gone
        if (peakCladK > 1700) s = Stage.CONTROL_MATERIAL_RELOCATION;
        setStage(s);
    }

    /**
     * Advances the degraded core.
     *
     * @param coreW       decay heat of the whole core (fission products where they are), W
     * @param coverage    share of the core height under water
     * @param pressurePa  primary pressure
     * @param waterKg     water left in the vessel (lower plenum first)
     * @param plenumWaterKg water the lower plenum holds when full
     * @param cladK       cladding temperature from the accident layer (capped at its melting point)
     * @param oxidized    share of the zirconium already oxide (it does not candle)
     */
    void step(double dt, double coreW, double coverage, double pressurePa, double waterKg, double plenumWaterKg, double cladK,
            double oxidized, double tSatK, SplittableRandom random) {
        if (!started && cladK >= LossOfCoolantTransient.CLAD_MELT_K - 1 && coverage < 1) {
            started = true;
            debrisK = cladK;
        }
        if (!started || !(dt > 0)) return;
        coreW = Math.max(0, coreW);
        double inCoreKg = Math.max(0, coreKg - plenumKg - exKg);
        double coreShare = coreKg > 0 ? inCoreKg / coreKg : 0;

        // ── in the core: heat-up, candling, melting ──
        if (inCoreKg > 1) {
            double heat = coreW * coreShare;
            // the covered part boils its share away; water back over the whole core takes what Γ lets through
            double gamma = gamma();
            double cap = Math.max(1, heatJK * coreShare);
            double removed;
            if (coverage <= 0.999) removed = heat * coverage;
            else if (gamma < 0.1) removed = heat + (debrisK - tSatK) * cap / QUENCH_S; // rod geometry: the bundle quenches
            else removed = (gamma < 0.5 ? BED_W_M2 : POOL_W_M2) * v.coreAreaM2();
            double e = (heat - removed) * dt;
            if (e > 0) {
                double room = Math.max(0, CERAMIC_MELT_K - debrisK) * cap;
                debrisK = Math.min(CERAMIC_MELT_K, debrisK + e / cap);
                e = Math.max(0, e - room);
                if (e > 0) meltKg = Math.min(Math.max(0, inCoreKg - candledKg - debrisKg), meltKg + e / FUSION_J_KG);
            } else if (e < 0) {
                // cooling freezes the pool into debris first, then cools the debris
                double freeze = Math.min(meltKg, -e / FUSION_J_KG);
                meltKg -= freeze;
                debrisKg += freeze;
                e += freeze * FUSION_J_KG;
                if (e < 0) debrisK = Math.max(tSatK, debrisK + e / cap);
            }
            if (debrisK >= CANDLING_K) {
                double metal = zrKg * Math.max(0, 1 - oxidized);
                double rate = metal * RELOCATION_M_S / v.coreHeightM();
                candledKg = Math.min(metal, candledKg + rate * dt);
                setStage(Stage.CLAD_MELT_CANDLING);
            }
            if (meltKg > 0) setStage(Stage.CERAMIC_MELT_POOL);
        }

        // ── crust failure: the melt drains into the lower plenum ──
        if (!vesselFailed && (plenumKg > 0 || meltKg >= CRUST_FAILURE_MELT * coreKg) && meltKg > 1) {
            double pour = meltKg;
            // §4c.10.4: the trigger is rolled per melt-entry event — the crust failure; the melt draining behind it
            // step by step is a stream into a plenum already holding corium, not a new premixed pour
            boolean entry = plenumKg == 0;
            meltKg = 0;
            plenumKg += pour;
            pourKg += pour;
            pourEvent = true;
            if (stage.ordinal() < Stage.LOWER_PLENUM_RELOCATION.ordinal()) headK = tSatK;
            setStage(Stage.LOWER_PLENUM_RELOCATION);
            boolean water = waterKg > 0.2 * plenumWaterKg;
            if (entry && water && random.nextDouble() < ReactivityInitiatedAccident.FCI_TRIGGER_PROB)
                fciJ += ReactivityInitiatedAccident.fciEnergyJ(pour, random);
        }

        // ── the lower head ──
        if (!vesselFailed && plenumKg > 0) {
            double pc = coreW * plenumKg / coreKg;
            double down = F_DOWN * pc;
            double flux = down / v.headAreaM2();
            ivr = cavityFlooded && flux < IVR_CHF_W_M2;
            if (ivr) {
                // boiling outside carries the whole downward flux: the wall sits near the cavity water
                headK += (400 - headK) * Math.min(1, dt / 600);
            } else {
                headK += down * dt / v.headHeatCapacityJK();
            }
            if (headK >= failureK(pressurePa)) {
                vesselFailed = true;
                failureEvent = true;
                ejectedKg = plenumKg;
                if (pressurePa > HPME_PA) dchJ = DCH_FRACTION * plenumKg * ReactivityInitiatedAccident.MELT_HEAT_J_KG;
                exKg += plenumKg;
                plenumKg = 0;
                setStage(Stage.LOWER_HEAD_FAILURE);
                if (cavityFlooded && random.nextDouble() < ReactivityInitiatedAccident.FCI_TRIGGER_PROB)
                    fciJ += ReactivityInitiatedAccident.fciEnergyJ(ejectedKg, random);
            }
        }

        // ── on the concrete ──
        if (vesselFailed && !failureEvent) {
            // what stayed in the core keeps melting through the open head
            if (meltKg > 0) {
                exKg += meltKg;
                meltKg = 0;
            }
            double pc = coreW * Math.min(1, exKg / coreKg);
            double spread = Math.max(1, exKg / (CORIUM_KG_M3 * SPREAD_DEPTH_M));
            double removed = cavityFlooded ? CRUST_W_M2 * spread : 0;
            double into = Math.max(0, pc - removed);
            ablationJ += into * dt;
            while (ablationJ >= BLOCK_J) {
                ablationJ -= BLOCK_J;
                ablatedM3 += 1;
                ablatedBlocks++;
                double h2 = CONCRETE_KG_M3 * CONCRETE_WATER * 2.016 / 18.015;
                h2Kg += h2;
            }
            if (exKg > 0) setStage(Stage.EX_VESSEL_MCCI);
        }
    }

    /** Creep failure temperature at primary pressure {@code p}: 1300 K above 10 MPa, 1500 K below 1 MPa, linear between. */
    public static double failureK(double p) {
        if (p >= 10e6) return FAIL_HIGH_P_K;
        if (p <= 1e6) return FAIL_LOW_P_K;
        return FAIL_LOW_P_K + (FAIL_HIGH_P_K - FAIL_LOW_P_K) * (p - 1e6) / 9e6;
    }

    /** Clears the per-step events; called by the owner before each step. */
    void beginStep() {
        stageEvent = pourEvent = failureEvent = false;
        fciJ = dchJ = pourKg = ejectedKg = 0;
        ablatedBlocks = 0;
    }

    private void setStage(Stage s) {
        if (s.ordinal() > stage.ordinal()) {
            stage = s;
            stageEvent = true;
        }
    }

    // ------------------------------------------------------------------ outputs

    public Stage stage() {
        return stage;
    }

    /** Melt progression has started (the cladding reached its melting point uncovered). */
    public boolean started() {
        return started;
    }

    /** Loss of geometry Γ = (relocated + molten)/core mass (§4c.10.2). */
    public double gamma() {
        return coreKg > 0 ? Math.min(1, (candledKg + meltKg + debrisKg + plenumKg + exKg) / coreKg) : 0;
    }

    public double debrisK() {
        return debrisK;
    }

    public double meltKg() {
        return meltKg;
    }

    public double lowerPlenumKg() {
        return plenumKg;
    }

    public double lowerHeadK() {
        return headK;
    }

    public boolean vesselFailed() {
        return vesselFailed;
    }

    /** In-vessel retention: corium in the lower plenum held by the flooded cavity. */
    public boolean retainedInVessel() {
        return !vesselFailed && plenumKg > 0 && ivr;
    }

    public double exVesselKg() {
        return exKg;
    }

    public double ablatedM3() {
        return ablatedM3;
    }

    /** Hydrogen made by the melt on the concrete, kg (cumulative). */
    public double mcciHydrogenKg() {
        return h2Kg;
    }

    public boolean stageEvent() {
        return stageEvent;
    }

    public boolean pourEvent() {
        return pourEvent;
    }

    public double pouredKg() {
        return pourKg;
    }

    public boolean failureEvent() {
        return failureEvent;
    }

    /** Corium leaving the vessel this step, kg. */
    public double ejectedKg() {
        return ejectedKg;
    }

    /** Steam explosion work this step, J (0 — none). */
    public double steamExplosionJ() {
        return fciJ;
    }

    /** Direct containment heating this step, J. */
    public double directHeatingJ() {
        return dchJ;
    }

    /** Concrete blocks the corium ate through this step. */
    public int ablatedBlocks() {
        return ablatedBlocks;
    }

    // ------------------------------------------------------------------ state

    public void save(StateWriter out) {
        out.putInt("stage", stage.ordinal());
        out.putBoolean("started", started);
        out.putBoolean("failed", vesselFailed);
        out.putBoolean("flooded", cavityFlooded);
        out.putDoubles("s", new double[] {debrisK, meltKg, candledKg, plenumKg, exKg, headK, ablationJ, ablatedM3, h2Kg, debrisKg}, 10);
    }

    public void load(StateReader in) {
        Stage[] all = Stage.values();
        stage = all[Math.max(0, Math.min(all.length - 1, in.optInt("stage", 0)))];
        started = in.optInt("started", 0) != 0;
        vesselFailed = in.optInt("failed", 0) != 0;
        cavityFlooded = in.optInt("flooded", 0) != 0;
        if (in.has("s")) {
            double[] s = in.getDoubles("s");
            if (s.length >= 9) {
                debrisK = s[0];
                meltKg = s[1];
                candledKg = s[2];
                plenumKg = s[3];
                exKg = s[4];
                headK = s[5];
                ablationJ = s[6];
                ablatedM3 = s[7];
                h2Kg = s[8];
            }
            if (s.length >= 10) debrisKg = s[9];
        }
    }
}
