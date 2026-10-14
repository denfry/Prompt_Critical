package dev.denfry.promptcritical.sim.mining;

import java.util.List;
import java.util.Map;

/**
 * Каталог жил M2 (08 §8.1.2). {@code ilmenite_placer} помечен в GDD (B) — в каталог не входит.
 *
 * <p>Содержания из таблицы 08 §8.1.2 — интервалом [g_min, g_max], медиана посередине. Где GDD содержание не даёт
 * (пиролюзит, пирит, сфалерит, галенит, флюорит, огнеупорная глина, халькопирит полиметаллов), — типичные
 * промышленные значения [допущение, сверить]. Попутные: Co = 0,035·g_Ni·e^{0,3N'} (§8.1.3); ЭПГ в медно-никелевом
 * халькопирите, Mo в порфировом, Ag в галените — [допущение] по порядку величин Норильска, Алмалыка, Алтая.
 */
public final class VeinCatalog {
    /** Сера сульфида на единицу содержания металла: CuFeS₂, (Fe,Ni)₉S₈ при Ni:Fe = 1, PbS, ZnS, MoS₂. */
    static final double S_CHALCOPYRITE = 1.009, S_PENTLANDITE = 0.971, S_GALENA = 0.155, S_SPHALERITE = 0.490,
            S_MOLYBDENITE = 0.668;

    private static final OreGrade MAGNETITE = OreGrade.of("magnetite_ore", "Fe", 0.35, 0.30, 0.40, 0.5, 0);
    private static final OreGrade HEMATITE = OreGrade.of("hematite_ore", "Fe", 0.575, 0.50, 0.65, 0.5, 0);
    private static final OreGrade QUARTZ = OreGrade.of("vein_quartz", "SiO2", 0.995, 0.99, 0.999, 0.003, 0);
    private static final OreGrade PYROLUSITE = OreGrade.of("pyrolusite_ore", "Mn", 0.40, 0.30, 0.50, 0.5, 0);
    /** Пирит: содержание — сама сера (FeS₂ — 53,5 % S). */
    private static final OreGrade PYRITE = OreGrade.of("pyrite_ore", "S", 0.35, 0.20, 0.50, 0.5, 1);
    private static final OreGrade FIRECLAY = OreGrade.of("fireclay", "Al2O3", 0.32, 0.25, 0.40, 0.5, 0);
    private static final OreGrade SPHALERITE = OreGrade.of("sphalerite_ore", "Zn", 0.08, 0.03, 0.15, 0.9, S_SPHALERITE);
    private static final OreGrade PENTLANDITE = OreGrade.of("pentlandite_ore", "Ni", 0.017, 0.01, 0.03, 0.9,
            S_PENTLANDITE).withByproduct("Co", 0.035, 0.3);
    private static final OreGrade CHALCOPYRITE_NORILSK = OreGrade.of("chalcopyrite_ore", "Cu", 0.03, 0.02, 0.04, 0.9,
            S_CHALCOPYRITE).withByproduct("PGM", 2.5e-4, 0.9);
    /** ЭПГ 5–50 г/т, медиана 12 г/т (§8.1.3). */
    private static final OreGrade SPERRYLITE = OreGrade.of("sperrylite_ore", "PGM", 12e-6, 5e-6, 50e-6, 0.9, 0);
    private static final OreGrade CHALCOPYRITE_PORPHYRY = OreGrade.of("chalcopyrite_ore", "Cu", 0.0075, 0.005, 0.01,
            0.9, S_CHALCOPYRITE).withByproduct("Mo", 0.03, 0.5);
    private static final OreGrade MOLYBDENITE = OreGrade.of("molybdenite_ore", "Mo", 6e-4, 2e-4, 1e-3, 0.9, S_MOLYBDENITE);
    private static final OreGrade GALENA = OreGrade.of("galena_ore", "Pb", 0.05, 0.02, 0.12, 0.9, S_GALENA)
            .withByproduct("Ag", 2e-3, 0.5);
    private static final OreGrade CHALCOPYRITE_POLY = OreGrade.of("chalcopyrite_ore", "Cu", 0.01, 0.005, 0.03, 0.9,
            S_CHALCOPYRITE);
    private static final OreGrade BAUXITE = OreGrade.of("bauxite_ore", "Al2O3", 0.50, 0.40, 0.60, 0.5, 0);
    private static final OreGrade CASSITERITE = OreGrade.of("cassiterite_ore", "Sn", 0.0125, 0.005, 0.02, 0.9, 0);
    private static final OreGrade FLUORITE = OreGrade.of("fluorite_ore", "CaF2", 0.30, 0.15, 0.60, 0.5, 0);
    private static final OreGrade CHROMITE = OreGrade.of("chromite_ore", "Cr2O3", 0.425, 0.35, 0.50, 0.5, 0);

    /** Каталог M2 в порядке таблицы 08 §8.1.2. */
    public static final List<VeinType> M2 = List.of(
            new VeinType("banded_iron", false, roles(MAGNETITE, HEMATITE, QUARTZ, PYROLUSITE), -32, 64, 16, 0.55, 30,
                    Map.of(BiomeClass.MOUNTAINS, 1.5, BiomeClass.PLAINS, 1.0, BiomeClass.TAIGA, 1.0,
                            BiomeClass.SWAMP, 1.0, BiomeClass.TUNDRA, 1.0, BiomeClass.SAVANNA, 1.0,
                            BiomeClass.JUNGLE, 1.0, BiomeClass.OTHER, 1.0)),
            new VeinType("coal_seam", true, roles(null, PYRITE, FIRECLAY, SPHALERITE), -40, 72, 24, 0.85, 25,
                    Map.of(BiomeClass.PLAINS, 1.0, BiomeClass.TAIGA, 1.0, BiomeClass.SWAMP, 1.5)),
            new VeinType("cu_ni_sulfide", false, roles(PENTLANDITE, CHALCOPYRITE_NORILSK, PYRITE, SPERRYLITE), -64, 16,
                    -32, 0.45, 8, Map.of(BiomeClass.MOUNTAINS, 1.0, BiomeClass.TUNDRA, 1.5)),
            new VeinType("porphyry_copper", false, roles(CHALCOPYRITE_PORPHYRY, MOLYBDENITE, PYRITE, QUARTZ), 0, 80, 40,
                    0.40, 10, Map.of(BiomeClass.MOUNTAINS, 1.0, BiomeClass.SAVANNA, 1.5)),
            new VeinType("polymetallic", false, roles(GALENA, SPHALERITE, PYRITE, CHALCOPYRITE_POLY), -16, 48, 16, 0.50,
                    12, Map.of()),
            new VeinType("bauxite", false, roles(BAUXITE, HEMATITE, FIRECLAY, null), 56, 80, 66, 0.70, 8,
                    Map.of(BiomeClass.JUNGLE, 3.0, BiomeClass.SAVANNA, 1.0)),
            new VeinType("quartz_greisen", false, roles(QUARTZ, CASSITERITE, FLUORITE, MOLYBDENITE), -32, 64, 24, 0.50,
                    7, Map.of(BiomeClass.MOUNTAINS, 2.0)),
            new VeinType("chromite_ultramafic", false, roles(CHROMITE, MAGNETITE, PYRITE, SPERRYLITE), -64, 0, -40, 0.45,
                    5, Map.of(BiomeClass.MOUNTAINS, 1.5)));

    private VeinCatalog() {}

    /** Тип по {@code vein_id} или {@code null}. */
    public static VeinType byId(String id) {
        for (VeinType t : M2) if (t.id().equals(id)) return t;
        return null;
    }

    private static OreGrade[] roles(OreGrade primary, OreGrade secondary, OreGrade intermediate, OreGrade sporadic) {
        return new OreGrade[] {primary, secondary, intermediate, sporadic};
    }
}
