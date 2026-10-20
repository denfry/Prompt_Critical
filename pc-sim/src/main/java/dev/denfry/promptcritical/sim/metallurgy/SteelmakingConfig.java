package dev.denfry.promptcritical.sim.metallurgy;

/**
 * Ключи стали, расплава и труб (08 §8.19): {@code converter.*}, {@code molten.fci_fraction}, {@code pipe.*}.
 *
 * @param decarbEfficiency        {@code converter.decarb_efficiency} — η_C на плато (0,85)
 * @param criticalCarbon          {@code converter.critical_carbon} — ниже этой доли C η_C падает линейно (0,003)
 * @param fciFraction             {@code molten.fci_fraction} [BALANCE] — доля тепла расплава, уходящая в механику
 *                                взаимодействия с водой (0,005)
 * @param weldDefectBasePerM      {@code pipe.weld_defect_base_per_m} — p₀ скрытого дефекта шва на метр (0,002)
 * @param hydrotestFractionOfSmys {@code pipe.hydrotest_fraction_of_smys} — p_исп / p_y (0,9, API 5L)
 * @param parisC                  {@code pipe.paris_c} — C закона Пэриса, м/цикл·(МПа√м)⁻³ (5·10⁻¹²)
 */
public record SteelmakingConfig(double decarbEfficiency, double criticalCarbon, double fciFraction,
        double weldDefectBasePerM, double hydrotestFractionOfSmys, double parisC) {
    public SteelmakingConfig {
        if (!(decarbEfficiency > 0 && decarbEfficiency <= 1)) throw new IllegalArgumentException("decarbEfficiency");
        if (!(criticalCarbon > 0 && criticalCarbon < 0.05)) throw new IllegalArgumentException("criticalCarbon");
        if (!(fciFraction >= 0 && fciFraction < 1)) throw new IllegalArgumentException("fciFraction");
        if (!(weldDefectBasePerM >= 0 && weldDefectBasePerM < 1)) throw new IllegalArgumentException("weldDefectBasePerM");
        if (!(hydrotestFractionOfSmys > 0 && hydrotestFractionOfSmys <= 1.1))
            throw new IllegalArgumentException("hydrotestFractionOfSmys");
        if (!(parisC >= 0)) throw new IllegalArgumentException("parisC");
    }

    public static SteelmakingConfig defaults() {
        return new SteelmakingConfig(0.85, 0.003, 0.005, 0.002, 0.9, 5e-12);
    }
}
