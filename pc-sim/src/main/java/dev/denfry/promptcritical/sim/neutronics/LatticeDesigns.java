package dev.denfry.promptcritical.sim.neutronics;

import java.util.Map;
import java.util.Set;

/**
 * Референсные решётки тиров (01 §1.6.1) и их опорные рабочие состояния. GDD предполагает загрузку
 * из data-пака, чтобы 02 добавлял свои ТВС без кода; пока набор фиксирован — загрузчик появится
 * вместе с биндингом 02.
 */
public final class LatticeDesigns {
    public static final String T0_SLUG = "t0_slug", T1_CLUSTER = "t1_cluster", T2_HEX = "t2_hex",
            T3_CLUSTER_D2O = "t3_cluster_d2o";

    /**
     * Примесь графита ядерной чистоты по умолчанию, ppm бор-эквивалента [BALANCE, третья константа
     * калибровки §1.6.2]: k∞ T0 = 1,07 (§1.14.4), длина диффузии в графите 49 см (чистый углерод — 59).
     */
    public static final double NUCLEAR_GRAPHITE_IMPURITY_PPM = 2.0;

    private static final Map<String, LatticeDesign> DESIGNS = Map.of(
            // Блочки ⌀28 мм прир. U в Al-оболочке, каналы 44×44 мм, шаг 203 мм (24 канала/м²).
            T0_SLUG, new LatticeDesign(T0_SLUG, FuelGeometry.SLUG_IN_CHANNEL, FuelMaterial.U_METAL,
                    CladMaterial.ALUMINIUM, CoolantKind.AIR, ModeratorKind.GRAPHITE, 0.0145, 0.014, 0.0005, 0.203,
                    1, 0.0248, 0, 24, 24, 0.015, 0.002, 0.030, 0.953),
            // 18 стержней ⌀13,6 мм в трубе ⌀88×4 мм (Zr-Nb), UO₂ 2 %, 16 каналов/м².
            T1_CLUSTER, new LatticeDesign(T1_CLUSTER, FuelGeometry.CLUSTER_IN_TUBE, FuelMaterial.UO2,
                    CladMaterial.ZR_NB, CoolantKind.H2O, ModeratorKind.GRAPHITE, 0.0068, 0.00575, 0.0009, 0.25, 18,
                    0.040, 0.004, 16, 288, 0.030, 0.029, 0.035, 0.84),
            // Треугольная решётка ⌀9,1 мм, шаг 12,75 мм, 6000 ТВЭЛов/м², UO₂ 3–5 %.
            T2_HEX, new LatticeDesign(T2_HEX, FuelGeometry.ROD_LATTICE, FuelMaterial.UO2, CladMaterial.ZR_NB,
                    CoolantKind.H2O, ModeratorKind.NONE, 0.00455, 0.003785, 0.00069, 0.01275, 312, 0, 0, 0, 6000,
                    0.27, 0.11, 0.62, 0),
            // 37 стержней ⌀13,1 мм в напорной трубе, прир. UO₂, шаг 286 мм.
            T3_CLUSTER_D2O, new LatticeDesign(T3_CLUSTER_D2O, FuelGeometry.CLUSTER_IN_TUBE, FuelMaterial.UO2,
                    CladMaterial.ZR_NB, CoolantKind.D2O, ModeratorKind.D2O, 0.00655, 0.00612, 0.0004, 0.286, 37,
                    0.0517, 0.0042, 12, 444, 0.053, 0.045, 0.037, 0.83));

    private static final Map<String, CellFeedbackState> REFERENCE = Map.of(
            T0_SLUG, new CellFeedbackState(300, 1.2, 300, 0, 0, 0),
            T1_CLUSTER, new CellFeedbackState(900, 450, 800, 0, 0, 0),
            T2_HEX, new CellFeedbackState(900, 715, 578, 0, 0, 0),
            T3_CLUSTER_D2O, new CellFeedbackState(900, 830, 340, 0, 0, 0));

    /** Обогащение (массовая доля U-235) референсного свежего топлива. */
    private static final Map<String, Double> ENRICHMENT = Map.of(
            T0_SLUG, 0.00711, T1_CLUSTER, 0.020, T2_HEX, 0.040, T3_CLUSTER_D2O, 0.00711);

    private LatticeDesigns() {}

    public static LatticeDesign get(String id) {
        LatticeDesign d = DESIGNS.get(id);
        if (d == null) throw new IllegalArgumentException("unknown lattice " + id);
        return d;
    }

    public static Set<String> ids() {
        return DESIGNS.keySet();
    }

    /** Опорное рабочее состояние (горячее, без Xe/Sm): T_f, ρ_c, T_m прототипа тира. */
    public static CellFeedbackState referenceState(String id) {
        return REFERENCE.get(id);
    }

    public static double referenceEnrichment(String id) {
        return ENRICHMENT.get(id);
    }

    /** Топливная ячейка решётки с графитом ядерной чистоты. */
    public static CellComposition referenceCell(String id) {
        return CellComposition.fuel(get(id), NUCLEAR_GRAPHITE_IMPURITY_PPM);
    }
}
