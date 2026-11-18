package dev.denfry.promptcritical.sim.nuclide;

/**
 * Плутоний свежей МОКС-сборки (канала, пучка), который реактор ведёт по изотопам: Pu-239, Pu-240, Pu-241 и Pu-242, кг.
 * Pu-238 (у реакторного плутония 1–3 %) отдельно не ведётся — его масса идёт в Pu-242: оба на тепловых нейтронах почти
 * не делятся (σ_f Pu-238 17 б, Pu-242 &lt; 0,2 б; Mughabghab, «Atlas of Neutron Resonances», 2006), так масса
 * тяжёлого металла сохраняется, а делящаяся часть остаётся честной. {@link #NONE} — урановое топливо.
 */
public record PlutoniumCharge(double pu239Kg, double pu240Kg, double pu241Kg, double pu242Kg) {
    public static final PlutoniumCharge NONE = new PlutoniumCharge(0, 0, 0, 0);
    /**
     * Ценность делящегося Pu к U-235 в тепловой решётке на начало кампании и предел доли Pu в ТМ теплового МОКС —
     * обоснование у {@code MoxBlend} (OECD/NEA, «Plutonium Fuel: An Assessment», 1989): выше 12 % коэффициент по
     * пустотам положительный.
     */
    public static final double FISS_TO_U235 = 0.75, MAX_THERMAL_FRACTION = 0.12;
    /**
     * Доля потери ценности гомогенного поглотителя (бора), которую теряют стержни. В полной МОКС-зоне PWR ценность
     * бора падает на 35–40 %, стержней — на 15–20 % (OECD/NEA, «Physics of Plutonium Recycling», т. VI, 1995; IAEA
     * TRS-415, 2003): сосредоточенный «чёрный» стержень ловит нейтроны, которые до него дошли, и его ценность зависит
     * от площади миграции M² ≈ τ + L², где τ от топлива не зависит, — поэтому вдвое слабее [оценка по этим данным].
     */
    public static final double ROD_TO_POISON_SENSITIVITY = 0.5;

    public PlutoniumCharge {
        pu239Kg = Math.max(0, pu239Kg);
        pu240Kg = Math.max(0, pu240Kg);
        pu241Kg = Math.max(0, pu241Kg);
        pu242Kg = Math.max(0, pu242Kg);
    }

    /**
     * Плутоний {@code puKg} с массовыми долями Pu-239…Pu-242; недостача до единицы (Pu-238) — в Pu-242. Без изотопии
     * (все доли 0) — оружейный: весь Pu-239.
     */
    public static PlutoniumCharge of(double puKg, double f239, double f240, double f241, double f242) {
        if (puKg <= 0) return NONE;
        double s = Math.max(0, f239) + Math.max(0, f240) + Math.max(0, f241) + Math.max(0, f242);
        if (s <= 0) return new PlutoniumCharge(puKg, 0, 0, 0);
        if (s > 1) {
            f239 /= s;
            f240 /= s;
            f241 /= s;
            f242 /= s;
            s = 1;
        }
        return new PlutoniumCharge(puKg * Math.max(0, f239), puKg * Math.max(0, f240), puKg * Math.max(0, f241),
                puKg * (Math.max(0, f242) + 1 - s));
    }

    /** Плутоний разрешён для теплового реактора: доля Pu в ТМ ({@code uKg} урана плюс этот Pu) не выше предела. */
    public boolean thermalLicensable(double uKg) {
        return totalKg() <= MAX_THERMAL_FRACTION * (uKg + totalKg()) + 1e-9;
    }

    /**
     * Урановый двойник состава: делящийся Pu (Pu-239 + Pu-241) заменён U-235 с весом {@link #FISS_TO_U235}, остальная
     * масса плутония — U-238; масса ТМ та же. Опора для ценности стержней МОКС-зоны: у двойника то же эквивалентное
     * обогащение, но урановый спектр и поглощение.
     */
    public static NuclideVector uraniumEquivalent(NuclideVector fuel) {
        return uraniumEquivalent(fuel, 1);
    }

    /**
     * Урановый двойник, в котором заменена только доля {@code share} плутония каждого изотопа — та, что пришла в зону
     * с МОКС; остальной (наработанный из U-238) остаётся как есть. Ценности стержней установок заданы для реальных
     * урановых зон, где наработанный Pu уже есть, — считать его ещё раз нельзя.
     */
    public static NuclideVector uraniumEquivalent(NuclideVector fuel, double share) {
        double g = Math.max(0, Math.min(1, share));
        double fiss = g * (fuel.atoms(Nuclide.PU239) * Nuclide.PU239.molarMass() + fuel.atoms(Nuclide.PU241) * Nuclide.PU241.molarMass());
        double other = g * (fuel.atoms(Nuclide.PU238) * Nuclide.PU238.molarMass() + fuel.atoms(Nuclide.PU240) * Nuclide.PU240.molarMass()
                + fuel.atoms(Nuclide.PU242) * Nuclide.PU242.molarMass());
        if (fiss + other <= 0) return fuel;
        double keep = 1 - g;
        return fuel.with(Nuclide.PU238, fuel.atoms(Nuclide.PU238) * keep).with(Nuclide.PU239, fuel.atoms(Nuclide.PU239) * keep)
                .with(Nuclide.PU240, fuel.atoms(Nuclide.PU240) * keep).with(Nuclide.PU241, fuel.atoms(Nuclide.PU241) * keep)
                .with(Nuclide.PU242, fuel.atoms(Nuclide.PU242) * keep)
                .with(Nuclide.U235, fuel.atoms(Nuclide.U235) + FISS_TO_U235 * fiss / Nuclide.U235.molarMass())
                .with(Nuclide.U238, fuel.atoms(Nuclide.U238) + (other + (1 - FISS_TO_U235) * fiss) / Nuclide.U238.molarMass());
    }

    /**
     * Доля плутония зоны, пришедшая с МОКС: загруженный свежим Pu к Pu сейчас, не больше 1. В МОКС Pu за кампанию
     * убывает (делится больше, чем нарабатывается), так что вся его масса считается МОКС; в урановой зоне загружено 0 —
     * множитель стержней остаётся 1, как до МОКС.
     *
     * @param loadedPuKg  плутоний, загруженный свежим (с поправкой на выгруженное), кг
     * @param currentPuKg плутоний в зоне сейчас, кг
     */
    public static double moxShare(double loadedPuKg, double currentPuKg) {
        if (loadedPuKg <= 0 || currentPuKg <= 0) return 0;
        return Math.min(1, loadedPuKg / currentPuKg);
    }

    /** Есть ли в составе плутоний. */
    public static boolean hasPlutonium(NuclideView fuel) {
        return fuel.atoms(Nuclide.PU238) + fuel.atoms(Nuclide.PU239) + fuel.atoms(Nuclide.PU240) + fuel.atoms(Nuclide.PU241)
                + fuel.atoms(Nuclide.PU242) > 0;
    }

    /**
     * Множитель ценности стержней МОКС-зоны. Гомогенный поглотитель по теории возмущений (Δρ ≈ −ΔΣ/Σ_a) теряет в
     * отношении Σ_a уранового двойника к Σ_a зоны; стержни — {@link #ROD_TO_POISON_SENSITIVITY} этой потери.
     *
     * @param sigmaAEquivalent Σ_a ячейки с {@link #uraniumEquivalent} составом, см⁻¹
     * @param sigmaA           Σ_a ячейки как есть, см⁻¹
     */
    public static double rodWorthFactor(double sigmaAEquivalent, double sigmaA) {
        if (sigmaA <= 0 || sigmaAEquivalent <= 0) return 1;
        double poison = Math.min(1, sigmaAEquivalent / sigmaA);
        return 1 - ROD_TO_POISON_SENSITIVITY * (1 - poison);
    }

    public double totalKg() {
        return pu239Kg + pu240Kg + pu241Kg + pu242Kg;
    }

    /** Делящийся плутоний (Pu-239 + Pu-241), кг. */
    public double fissileKg() {
        return pu239Kg + pu241Kg;
    }

    public boolean isMox() {
        return totalKg() > 0;
    }

    /** Сумма двух порций по изотопам. */
    public PlutoniumCharge plus(PlutoniumCharge o) {
        return new PlutoniumCharge(pu239Kg + o.pu239Kg, pu240Kg + o.pu240Kg, pu241Kg + o.pu241Kg, pu242Kg + o.pu242Kg);
    }

    /** Та же изотопия в доле {@code f} массы (порция пучков при перегрузке на ходу). */
    public PlutoniumCharge scaled(double f) {
        return new PlutoniumCharge(pu239Kg * f, pu240Kg * f, pu241Kg * f, pu242Kg * f);
    }
}
