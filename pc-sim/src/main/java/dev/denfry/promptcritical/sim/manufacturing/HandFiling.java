package dev.denfry.promptcritical.sim.manufacturing;

/**
 * Опиловка напильником (GDD 18 §18.7, строка «резание», §18.15 п. 4): кованый вал до станков доводится вручную до
 * IT12–IT14. Такой вал работает, но посадка подшипника грубая, и машина изнашивается быстрее (§18.9).
 */
public final class HandFiling {
    /**
     * Квалитет опиленной поверхности: середина экономической точности опиловки IT12–IT14 (справочник технолога-
     * машиностроителя, т. 1, табл. экономической точности обработки).
     */
    public static final int FILED_IT = 13;
    /** Шероховатость после чистовой опиловки, Ra, мкм (там же: Ra 3,2–12,5). */
    public static final double FILED_RA_UM = 6.3;
    /**
     * Твёрже этого напильник скользит: рабочая часть напильника из У13 закалена до HRC 54–60 (ГОСТ 1465), сталь
     * заметно мягче него уже не режется [BALANCE: порог 45].
     */
    public static final int MAX_WORK_HRC = 45;

    private HandFiling() {}

    /** Можно ли опилить: деталь не обработана точнее опиловки и не закалена. */
    public static boolean canFile(PartSpec p) {
        return (p.itGrade() == 0 || p.itGrade() > FILED_IT) && p.hardnessHrc() <= MAX_WORK_HRC;
    }

    /** Опиленная деталь: квалитет IT13, Ra не грубее 6,3 мкм; прежние замеры больше не годятся. */
    public static PartSpec file(PartSpec p) {
        if (!canFile(p)) throw new IllegalArgumentException("cannot file " + p);
        return new PartSpec(p.kind(), p.material(), p.route(), p.heatTreat(), p.hardnessHrc(), FILED_IT,
                Math.min(p.raUm(), FILED_RA_UM), p.defects(), false);
    }
}
