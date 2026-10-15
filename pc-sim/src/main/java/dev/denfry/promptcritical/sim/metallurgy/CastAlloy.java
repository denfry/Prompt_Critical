package dev.denfry.promptcritical.sim.metallurgy;

/**
 * Литейный сплав (08 §8.9): теплофизика расплава для плавки и затвердевания. Плотности расплавов — 08 §8.0.2
 * (чугун 6980, сталь 7000, Al 2375, Cu 8020); ликвидус, теплоёмкости и теплота плавления — справочные порядки
 * [сверить]. Бронза — оловянная БрО10, плотность 8600.
 */
public enum CastAlloy {
    /** Серый чугун (C ≈ 3,5 %): графит не даёт горячих трещин; быстрое охлаждение — отбел. */
    GRAY_IRON("gray_iron", 1473, 6980, 680, 830, 230e3, false),
    CARBON_STEEL("carbon_steel", 1793, 7000, 620, 820, 270e3, true),
    BRONZE("bronze", 1300, 8600, 380, 470, 190e3, false),
    COPPER("copper", 1358, 8020, 385, 495, 205e3, false),
    ALUMINIUM("aluminium", 933, 2375, 900, 1180, 397e3, true);

    public final String id;
    /** Ликвидус (у чистых металлов — точка плавления), К. */
    public final double liquidusK;
    /** Плотность расплава, кг/м³. */
    public final double densityLiquid;
    /** c_p твёрдого и жидкого, Дж/(кг·К). */
    public final double cSolid, cLiquid;
    /** Теплота плавления, Дж/кг. */
    public final double latentHeat;
    /** Склонен к горячим трещинам при разнотолщинности. */
    public final boolean hotShort;

    CastAlloy(String id, double liquidusK, double densityLiquid, double cSolid, double cLiquid, double latentHeat,
            boolean hotShort) {
        this.id = id;
        this.liquidusK = liquidusK;
        this.densityLiquid = densityLiquid;
        this.cSolid = cSolid;
        this.cLiquid = cLiquid;
        this.latentHeat = latentHeat;
        this.hotShort = hotShort;
    }

    /** Сплав по {@link #id}; {@code null}, если такого нет. */
    public static CastAlloy byId(String id) {
        for (CastAlloy a : values()) if (a.id.equals(id)) return a;
        return null;
    }

    /** Энтальпия от T₀ = 293 К до T, Дж/кг (плавление при ликвидусе). */
    public double enthalpy(double tK) {
        double t0 = 293;
        if (tK <= liquidusK) return cSolid * (tK - t0);
        return cSolid * (liquidusK - t0) + latentHeat + cLiquid * (tK - liquidusK);
    }

    /** Температура по энтальпии, К (обратная к {@link #enthalpy}); на площадке плавления — ликвидус. */
    public double temperature(double hJkg) {
        double t0 = 293, hs = cSolid * (liquidusK - t0);
        if (hJkg <= hs) return t0 + hJkg / cSolid;
        if (hJkg <= hs + latentHeat) return liquidusK;
        return liquidusK + (hJkg - hs - latentHeat) / cLiquid;
    }

    /** Доля жидкого при энтальпии h. */
    public double liquidFraction(double hJkg) {
        double hs = cSolid * (liquidusK - 293);
        return Math.max(0, Math.min(1, (hJkg - hs) / latentHeat));
    }

    /**
     * Множитель постоянной Чворинова относительно стали в той же форме при одинаковом перегреве:
     * B ∝ [ρ(L + c_lΔT)/(T_пл − T₀)]² (вывод Чворинова для полубесконечной формы).
     */
    public double chvorinovFactor(double superheatK) {
        double s = CARBON_STEEL.chvorinovGroup(superheatK);
        double r = chvorinovGroup(superheatK) / s;
        return r * r;
    }

    private double chvorinovGroup(double superheatK) {
        return densityLiquid * (latentHeat + cLiquid * Math.max(0, superheatK)) / (liquidusK - 293);
    }
}
