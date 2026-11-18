package dev.denfry.promptcritical.sim.radiation;

/**
 * Перенос снимаемого загрязнения касанием: при контакте площадью S с поверхности A на B уходит доля k снимаемой
 * активности A с этой площади, и встречно с B на A. Фиксированное не переносится. Для сухого контакта k — единицы-десятки
 * процентов: ISO 7503-2 принимает, что мазок по сухой поверхности снимает 10 % рыхлого (фактор снятия F = 0,1);
 * шаг подошвой мягче мазка — здесь {@value #STEP_K}, хват рукой — {@value #GRIP_K}.
 *
 * <p>Площади тела — порядки ICRP 89 (2002): кожа кистей обеих рук ≈ {@value #HANDS_CM2} см² (ладонная и тыльная),
 * подошва одной ноги ≈ {@value #SOLE_CM2} см². Длина шага взрослого ≈ {@value #STEP_M} м.
 */
public final class ContaminationTransfer {
    public static final double STEP_K = 0.02, GRIP_K = 0.05;
    public static final double HANDS_CM2 = 800, SOLE_CM2 = 250, STEP_M = 0.7;
    /** Площадь грани блока (1 м²), см². */
    public static final double BLOCK_FACE_CM2 = 1e4;
    /** Условная поверхность одного предмета (инструмент, деталь, мешок), см². */
    public static final double ITEM_CM2 = 100;

    /** Две поверхности после контакта. */
    public record Pair(SurfaceContamination a, SurfaceContamination b) {
    }

    private ContaminationTransfer() {
    }

    /**
     * Один контакт: поверхности A (площадь {@code areaA}) и B ({@code areaB}) соприкоснулись площадью {@code contactCm2}
     * с коэффициентом переноса {@code k}. Площадь контакта не больше меньшей из поверхностей. Активность сохраняется.
     */
    public static Pair contact(SurfaceContamination a, double areaA, SurfaceContamination b, double areaB, double contactCm2, double k) {
        double s = Math.min(contactCm2, Math.min(areaA, areaB));
        if (s <= 0 || k <= 0) return new Pair(a, b);
        // активность (Бк), снятая с контакта каждой стороны
        double aAl = a.alphaLoose() * s * k, aBl = a.betaLoose() * s * k;
        double bAl = b.alphaLoose() * s * k, bBl = b.betaLoose() * s * k;
        SurfaceContamination na = new SurfaceContamination(a.alphaLoose() + (bAl - aAl) / areaA, a.alphaFixed(),
                a.betaLoose() + (bBl - aBl) / areaA, a.betaFixed());
        SurfaceContamination nb = new SurfaceContamination(b.alphaLoose() + (aAl - bAl) / areaB, b.alphaFixed(),
                b.betaLoose() + (aBl - bBl) / areaB, b.betaFixed());
        return new Pair(na, nb);
    }

    /**
     * Прогулка {@code metres} по полу: шаги чередуют ноги, каждая подошва касается пола раз в два шага. Обувь (обе
     * подошвы, {@code shoes}) и пол ({@code floor}, площадь {@code floorCm2}) обмениваются рыхлым загрязнением.
     */
    public static Pair walk(SurfaceContamination shoes, SurfaceContamination floor, double floorCm2, double metres) {
        // больше сотни шагов за раз — телепорт или рывок, а не ходьба по одному блоку
        int steps = (int) Math.min(100, Math.round(metres / STEP_M));
        Pair p = new Pair(shoes, floor);
        for (int i = 0; i < steps; i++) p = contact(p.a(), 2 * SOLE_CM2, p.b(), floorCm2, SOLE_CM2, STEP_K);
        return p;
    }
}
