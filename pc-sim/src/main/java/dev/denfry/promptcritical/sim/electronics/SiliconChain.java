package dev.denfry.promptcritical.sim.electronics;

/**
 * Кремний от кварца до поликремния (11 §11.5.2, 15 §15.6): материальные и энергетические балансы стадий.
 *
 * <p><b>Рудно-термическая печь.</b> SiO₂ + 2C → Si + 2CO, ΔH°₂₉₈ = +689,6 кДж/моль (NIST-JANAF: SiO₂ −910,7, CO −110,5)
 * → 6,82 кВт·ч на кг Si только на реакцию. Реальная печь тратит 11–13 кВт·ч/кг (Schei, Tuset, Tveit, «Production of
 * High Silicon Alloys», 1998): извлечение кремния 80–90 % (часть уходит с газом как SiO и пылью микрокремнезёма) и
 * тепловой КПД ванны около 0,65 (нагрев шихты и газа до 1900–2100 °C, потери через футеровку). Углерод — с запасом
 * 10 % на угар электродов и окисление кокса на колошнике.
 *
 * <p><b>Трихлорсилан.</b> Si + 3HCl → SiHCl₃ + H₂ в кипящем слое при ≈ 300 °C; селективность по SiHCl₃ 85–90 %, остальное
 * — SiCl₄ (Si + 4HCl → SiCl₄ + 2H₂). Газы — в нм³ (22,414 л/моль).
 *
 * <p><b>Сименс-процесс.</b> SiHCl₃ + H₂ → Si + 3HCl на стержнях при ≈ 1100 °C. За проход в кремний переходит 10–20 %
 * трихлорсилана (равновесие сдвинуто к SiCl₄ и HCl), остальное рециркулирует. Электричество — на нагрев стержней,
 * излучающих на холодный колокол: 45–200 кВт·ч/кг в зависимости от поколения установки (Fraunhofer ISE 2024; колокол
 * 1980-х — около 100–150, современные — 45–60). Принято 120 кВт·ч/кг: эпоха, до которой идёт мод.
 */
public final class SiliconChain {
    public static final double M_SI = 28.0855, M_SIO2 = 60.0843, M_C = 12.011, M_HCL = 36.461, M_TCS = 135.45, NM3_PER_MOL = 0.022414;
    /** ΔH°₂₉₈ восстановления SiO₂ углеродом, Дж/моль. */
    public static final double DH_REDUCTION_J_MOL = 689.6e3;
    /** Извлечение кремния и тепловой КПД рудно-термической печи. */
    public static final double SAF_RECOVERY = 0.85, SAF_THERMAL_EFFICIENCY = 0.65, CARBON_EXCESS = 1.10;
    /** Чистота технического кремния. */
    public static final double MG_PURITY = 0.985;
    /** Селективность по трихлорсилану и доля перехода в кремний за проход реактора Сименса. */
    public static final double TCS_SELECTIVITY = 0.88, SIEMENS_CONVERSION = 0.15;
    /** Удельная энергия реактора Сименса, кВт·ч/кг. */
    public static final double SIEMENS_KWH_PER_KG = 120;
    /** Плотность жидкого трихлорсилана, кг/л (кипит при 31,8 °C). */
    public static final double TCS_DENSITY = 1.342;
    public static final double J_PER_KWH = 3.6e6;

    private SiliconChain() {
    }

    /** Кварц на кг технического кремния, кг. */
    public static double quartzPerKgSi() {
        return M_SIO2 / M_SI / SAF_RECOVERY;
    }

    /** Углерод на кг кремния, кг. */
    public static double carbonPerKgSi() {
        return 2 * M_C / M_SI / SAF_RECOVERY * CARBON_EXCESS;
    }

    /** Теоретическая энергия реакции на кг Si, кВт·ч. */
    public static double reactionKwhPerKg() {
        return DH_REDUCTION_J_MOL / (M_SI / 1000) / J_PER_KWH;
    }

    /** Электроэнергия печи на кг технического кремния, кВт·ч. */
    public static double safKwhPerKg() {
        return reactionKwhPerKg() / (SAF_RECOVERY * SAF_THERMAL_EFFICIENCY);
    }

    /** HCl на кг кремния в реакторе трихлорсилана, нм³ (с учётом SiCl₄, который берёт четвёртую молекулу). */
    public static double hclNm3PerKgSi() {
        double mol = 1000 / M_SI;
        return mol * (3 * TCS_SELECTIVITY + 4 * (1 - TCS_SELECTIVITY)) * NM3_PER_MOL;
    }

    /** Водород, выделяющийся на кг кремния, нм³. */
    public static double hydrogenNm3PerKgSi() {
        double mol = 1000 / M_SI;
        return mol * (TCS_SELECTIVITY + 2 * (1 - TCS_SELECTIVITY)) * NM3_PER_MOL;
    }

    /** Трихлорсилан на кг технического кремния, л. */
    public static double tcsLitresPerKgSi() {
        return 1000 / M_SI * TCS_SELECTIVITY * M_TCS / 1000 / TCS_DENSITY;
    }

    /** Трихлорсилан, проходящий реактор Сименса на кг поликремния (с рециклом), л. */
    public static double tcsLitresPerKgPoly() {
        return 1000 / M_SI * M_TCS / 1000 / TCS_DENSITY / SIEMENS_CONVERSION;
    }

    /** Чистый расход трихлорсилана на кг поликремния (рецикл возвращает непрореагировавший), л. */
    public static double tcsConsumedLitresPerKgPoly() {
        return 1000 / M_SI * M_TCS / 1000 / TCS_DENSITY;
    }

    /** Водород на кг поликремния, нм³. */
    public static double hydrogenNm3PerKgPoly() {
        return 1000 / M_SI * NM3_PER_MOL;
    }

    /** HCl, выходящий из реактора Сименса на кг поликремния, нм³. */
    public static double hclOutNm3PerKgPoly() {
        return 3 * 1000 / M_SI * NM3_PER_MOL;
    }
}
