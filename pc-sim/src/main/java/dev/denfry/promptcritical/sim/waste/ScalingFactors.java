package dev.denfry.promptcritical.sim.waste;

import dev.denfry.promptcritical.sim.radiation.ActivityInventory;
import dev.denfry.promptcritical.sim.radiation.Emitter;
import java.util.EnumMap;
import java.util.Map;

/**
 * Метод масштабирующих коэффициентов (ISO 21238:2007; IAEA Nuclear Energy Series NW-T-1.18, 2009): трудноизмеряемые
 * нуклиды упаковки считают не спектрометром, а по отношению к ключевому γ-излучателю — Cs-137 для продуктов деления и
 * актинидов, Co-60 для продуктов активации. Коэффициенты — отношения активностей на момент характеризации.
 *
 * <p>Остеклованные ВАО: продукты деления и актиниды остаются в рафинате PUREX.
 * <ul>
 *   <li>Tc-99 / Cs-137: кумулятивные выходы почти равны (6,1 и 6,2 %), значит, отношение активностей — отношение
 *   периодов: 30,08 / 2,111·10⁵ = 1,4·10⁻⁴;</li>
 *   <li>Cs-135 / Cs-137: атомов Cs-135 в облучённом LWR-топливе ≈ 0,35 от Cs-137 (Xe-135 выгорает) → 0,35 · 30,08 /
 *   2,3·10⁶ = 4,6·10⁻⁶;</li>
 *   <li>I-129: выход ≈ 0,8 % → 2,5·10⁻⁷ от Cs-137, но {@link dev.denfry.promptcritical.sim.reprocessing.PurexPlant}
 *   отгоняет 95 % иода в газоочистку → 1,25·10⁻⁸;</li>
 *   <li>Am-241: ≈ 0,04 от Cs-137 через несколько лет после выгрузки (накопление из Pu-241); Pu-239 и Pu-240 —
 *   потери PUREX ≈ 0,1 % от ~5,6 и ~2,3 кг/т ТМ → 4·10⁻⁶ и 5·10⁻⁶; Np-237 без корректировки валентности почти весь
 *   в рафинате: 0,6 кг/т × 2,6·10⁷ Бк/г → 4,4·10⁻⁶.</li>
 * </ul>
 * Эксплуатационные отходы АЭС (смолы, фильтры, кубовый остаток, дезактивационные растворы) — типичные порядки
 * NW-T-1.18: Ni-63 / Co-60 ≈ 1, C-14 / Co-60 ≈ 0,03, Tc-99 / Cs-137 ≈ 10⁻⁵, I-129 / Cs-137 ≈ 3·10⁻⁷, α-нуклиды
 * от повреждённых твэлов — 10⁻⁵ от Cs-137. Разброс на конкретных АЭС — до порядка; поэтому реальные станции свои
 * коэффициенты периодически подтверждают радиохимическим анализом проб.
 */
public final class ScalingFactors {
    /** Происхождение отходов — от него зависят коэффициенты. */
    public enum Stream { REPROCESSING_HLW, REACTOR_OPERATIONAL, DECONTAMINATION }

    private static final Map<LongLivedNuclide, Double> HLW_PER_CS137 = Map.of(
            LongLivedNuclide.TC99, 1.4e-4, LongLivedNuclide.CS135, 4.6e-6, LongLivedNuclide.I129, 1.25e-8,
            LongLivedNuclide.AM241, 0.04, LongLivedNuclide.PU239, 4e-6, LongLivedNuclide.PU240, 5e-6, LongLivedNuclide.NP237, 4.4e-6);
    private static final Map<LongLivedNuclide, Double> NPP_PER_CS137 = Map.of(
            LongLivedNuclide.TC99, 1e-5, LongLivedNuclide.I129, 3e-7, LongLivedNuclide.CS135, 4.6e-6,
            LongLivedNuclide.PU239, 5e-6, LongLivedNuclide.PU240, 5e-6, LongLivedNuclide.AM241, 1e-5);
    private static final Map<LongLivedNuclide, Double> NPP_PER_CO60 = Map.of(LongLivedNuclide.NI63, 1.0, LongLivedNuclide.C14, 0.03);

    private ScalingFactors() {
    }

    /** Активности трудноизмеряемых нуклидов по ключевым в {@code key}, Бк. */
    public static Map<LongLivedNuclide, Double> difficultToMeasure(Stream stream, ActivityInventory key) {
        EnumMap<LongLivedNuclide, Double> out = new EnumMap<>(LongLivedNuclide.class);
        double cs = key.bq(Emitter.CS137), co = key.bq(Emitter.CO60);
        if (stream == Stream.REPROCESSING_HLW) {
            HLW_PER_CS137.forEach((n, f) -> out.put(n, f * cs));
        } else {
            NPP_PER_CS137.forEach((n, f) -> out.put(n, f * cs));
            NPP_PER_CO60.forEach((n, f) -> out.merge(n, f * co, Double::sum));
        }
        return out;
    }

    /** Полный состав упаковки: γ-излучатели плюс пересчитанные долгоживущие. */
    public static WasteInventory characterize(Stream stream, ActivityInventory gamma, double volumeM3) {
        return new WasteInventory(gamma, difficultToMeasure(stream, gamma), volumeM3);
    }
}
