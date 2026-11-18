package dev.denfry.promptcritical.sim.petrochem;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import java.util.Objects;

/**
 * Трубчатая печь пиролиза с закалочно-испарительным аппаратом (ЗИА). Сырьё с паром разбавления нагревается в
 * конвекционной секции до входа в радиантный змеевик ({@link PyrolysisFeed#inletK}) и в змеевике — до температуры на
 * выходе COT (800–880 °C) за время пребывания 0,2–0,5 с; на выходе газ за десятки миллисекунд закаливается в ЗИА до
 * 400–450 °C, отдавая тепло на пар высокого давления 11,5 МПа (Zimmermann, Walzl, «Ethylene», Ullmann's, 2009, §5;
 * Albright, Crynes, Corcoran, «Pyrolysis: Theory and Industrial Practice», 1983, гл. 1, 17).
 *
 * <p>Выходы — {@link EthaneCracking} для этана, {@link PyrolysisFeed#yields} для остального. Пар разбавления снижает
 * парциальное давление углеводородов и сдвигает выходы к олефинам: на каждые 0,1 кг/кг сверх обычного соотношения —
 * +0,6 % этилена (относительных) за счёт смолы и пиробензина [оценка по трендам Мухиной, гл. 4].
 *
 * <p>Коксование: кокс на стенке змеевика растёт со скоростью ∝ exp(−E_к/RT_п), где T_п — температура поверхности
 * кокса со стороны газа (COT плюс плёнка), E_к = 150 кДж/моль; тем быстрее, чем
 * тяжелее сырьё и меньше пара; слой кокса толщиной δ (теплопроводность ≈ 5 Вт/(м·К)) при тепловом потоке ≈ 80 кВт/м²
 * поднимает температуру стенки (TMT) на 16 К на миллиметр. Пробег до выжига — 40–60 суток при расчётном режиме, предел
 * TMT — 1100 °C для труб из сплава HP (Ullmann, §5.2). Защита: при TMT выше предела сырьё отсекается. Выжиг —
 * паровоздушной смесью, 0,4 мм кокса в час (сутки на полный выжиг). Коксование идёт по игровому времени, как сезоны;
 * материальный и тепловой балансы — в реальных секундах.
 */
public final class PyrolysisFurnace {
    public enum Mode { STOP, RUN, DECOKE }

    public static final double TMT_LIMIT_K = 1100 + 273.15, FILM_K = 100, K_PER_MM = 16;
    public static final double COKE_E = 150e3, COKE_MM_PER_DAY = 0.19, DESIGN_WALL_K = 850 + 273.15 + FILM_K;
    public static final double DECOKE_MM_PER_H = 0.4;
    public static final double CP_FEED = 3.0e3, CP_STEAM = 2.2e3, CP_GAS = 2.6e3, WATER_TO_STEAM_J_KG = 2.26e6 + 4.2e3 * 50;
    public static final double FURNACE_EFFICIENCY = 0.92, HP_STEAM_J_KG = 2.27e6;
    public static final double PRESSURE_PA = 0.22e6, MIN_COT_K = 780 + 273.15, MAX_COT_K = 900 + 273.15;

    private PyrolysisFeed feed = PyrolysisFeed.PROPANE;
    private Mode mode = Mode.STOP;
    private double cotK = 850 + 273.15, steamRatio = 0.4, residenceS = 0.35;
    private double cokeMm;
    private boolean tmtTrip;

    // итоги последнего шага
    private double[] productKgS = new double[PyroComponent.N];
    private double conversion, dutyW, fuelW, hpSteamKgS, tmtK, feedKgS;

    public PyrolysisFeed feed() {
        return feed;
    }

    public void setFeed(PyrolysisFeed f) {
        feed = Objects.requireNonNull(f, "feed");
    }

    public Mode mode() {
        return mode;
    }

    public void setMode(Mode m) {
        mode = Objects.requireNonNull(m, "mode");
        if (m != Mode.RUN) tmtTrip = false;
    }

    public double cotK() {
        return cotK;
    }

    public void setCotK(double k) {
        cotK = Math.max(MIN_COT_K, Math.min(MAX_COT_K, k));
    }

    public double steamRatio() {
        return steamRatio;
    }

    public void setSteamRatio(double r) {
        steamRatio = Math.max(0.2, Math.min(1.0, r));
    }

    public double residenceS() {
        return residenceS;
    }

    public void setResidenceS(double s) {
        residenceS = Math.max(0.1, Math.min(0.8, s));
    }

    public double cokeMm() {
        return cokeMm;
    }

    public void setCokeMm(double mm) {
        cokeMm = Math.max(0, mm);
    }

    public boolean tmtTrip() {
        return tmtTrip;
    }

    /** Продукты последнего шага, кг/с по компонентам. */
    public double[] productKgS() {
        return productKgS.clone();
    }

    public double conversion() {
        return conversion;
    }

    /** Тепло, поглощённое в змеевике, Вт. */
    public double dutyW() {
        return dutyW;
    }

    /** Тепло сжигаемого топлива, Вт. */
    public double fuelW() {
        return fuelW;
    }

    public double hpSteamKgS() {
        return hpSteamKgS;
    }

    public double tmtK() {
        return tmtK;
    }

    public double feedKgS() {
        return feedKgS;
    }

    /** Соотношение пар/сырьё, обычное для сырья: газы 0,3, бензины 0,5 кг/кг. */
    public double referenceSteamRatio() {
        return feed.feedComponent() != null ? 0.3 : 0.5;
    }

    /** Степень превращения при нынешних COT и времени пребывания (брутто-кинетика по линейному профилю). */
    public double lumpedConversion() {
        int n = 100;
        double sum = 0;
        for (int i = 0; i < n; i++) {
            double t = feed.inletK() + (cotK - feed.inletK()) * (i + 0.5) / n;
            sum += feed.rate(t) * residenceS / n;
        }
        return 1 - Math.exp(-sum);
    }

    /** Выходы на сырьё, массовые доли, при нынешнем режиме. */
    public double[] yieldsNow() {
        double[] y;
        if (feed == PyrolysisFeed.ETHANE) {
            double ethane = 1000 / PyroComponent.C2H6.molarMassG;
            double steam = steamRatio * 1000 / 18.015;
            double[] mol = EthaneCracking.crack(ethane, steam, feed.inletK(), cotK, PRESSURE_PA, residenceS);
            y = new double[PyroComponent.N];
            for (PyroComponent c : PyroComponent.values()) y[c.ordinal()] = mol[c.ordinal()] * c.molarMassG / 1000;
            double sum = 0;
            for (double v : y) sum += v;
            // схема без C5+: недостающая доля (доли процента) — в пиробензин
            y[PyroComponent.PYGAS.ordinal()] += Math.max(0, 1 - sum);
            conversion = 1 - y[PyroComponent.C2H6.ordinal()];
        } else {
            conversion = lumpedConversion();
            y = feed.yields(conversion);
        }
        // пар разбавления: этилен за счёт смолы и пиробензина
        double shift = 0.06 * (steamRatio - referenceSteamRatio()) * y[PyroComponent.C2H4.ordinal()];
        double heavy = y[PyroComponent.FUEL_OIL.ordinal()] + y[PyroComponent.PYGAS.ordinal()];
        shift = Math.max(-0.5 * y[PyroComponent.C2H4.ordinal()], Math.min(shift, 0.5 * heavy));
        if (heavy > 0) {
            y[PyroComponent.C2H4.ordinal()] += shift;
            double f = shift / heavy;
            y[PyroComponent.FUEL_OIL.ordinal()] -= f * y[PyroComponent.FUEL_OIL.ordinal()];
            y[PyroComponent.PYGAS.ordinal()] -= f * y[PyroComponent.PYGAS.ordinal()];
        }
        // сернистые и углекислота: диметилдисульфид в паре против коксования и CO₂ паровой газификации кокса
        y[PyroComponent.H2S.ordinal()] += 0.0002;
        y[PyroComponent.CO2.ordinal()] += 0.0003;
        double sum = 0;
        for (double v : y) sum += v;
        for (int i = 0; i < y.length; i++) y[i] /= sum;
        return y;
    }

    /**
     * Шаг печи.
     *
     * @param dtS         шаг, с (баланс)
     * @param gameDtS     шаг игрового времени, с (коксование и выжиг)
     * @param feedKgS     сколько сырья подано, кг/с
     */
    public void step(double dtS, double gameDtS, double feedKgS) {
        productKgS = new double[PyroComponent.N];
        dutyW = 0;
        fuelW = 0;
        hpSteamKgS = 0;
        conversion = 0;
        this.feedKgS = 0;
        double wall = cotK + FILM_K;
        tmtK = mode == Mode.STOP ? 0 : wall + K_PER_MM * cokeMm;
        if (mode == Mode.DECOKE) {
            cokeMm = Math.max(0, cokeMm - DECOKE_MM_PER_H * gameDtS / 3600);
            tmtK = 0;
            return;
        }
        if (mode != Mode.RUN || tmtTrip || feedKgS <= 0) return;
        if (tmtK > TMT_LIMIT_K) {
            tmtTrip = true;
            return;
        }
        this.feedKgS = feedKgS;
        double[] y = yieldsNow();
        for (int i = 0; i < y.length; i++) productKgS[i] = y[i] * feedKgS;
        double steamKgS = steamRatio * feedKgS;
        dutyW = feedKgS * (CP_FEED * (cotK - 300) + conversion * feed.heatOfCrackingJKg())
                + steamKgS * (WATER_TO_STEAM_J_KG + CP_STEAM * (cotK - 373));
        fuelW = dutyW / FURNACE_EFFICIENCY;
        double quenchOut = feed.feedComponent() != null ? 400 + 273.15 : 450 + 273.15;
        hpSteamKgS = (feedKgS + steamKgS) * CP_GAS * (cotK - quenchOut) / HP_STEAM_J_KG;
        // кокс растёт на поверхности слоя, обращённой к газу: её температура — газ плюс плёнка, за пробег почти не
        // меняется (растёт температура металла под коксом, а не поверхности кокса)
        double rel = COKE_E / EthaneCracking.R * (1 / DESIGN_WALL_K - 1 / wall);
        double steamFactor = Math.sqrt(referenceSteamRatio() / steamRatio);
        cokeMm += COKE_MM_PER_DAY * feed.cokeFactor() * Math.exp(rel) * steamFactor * gameDtS / 86400;
    }

    public void save(StateWriter out) {
        out.putString("feed", feed.id());
        out.putString("mode", mode.name());
        out.putDouble("cot", cotK);
        out.putDouble("steam", steamRatio);
        out.putDouble("tau", residenceS);
        out.putDouble("coke", cokeMm);
        out.putBoolean("tmt_trip", tmtTrip);
    }

    public void load(StateReader in) {
        String id = in.optString("feed", PyrolysisFeed.PROPANE.id());
        for (PyrolysisFeed f : PyrolysisFeed.ALL) if (f.id().equals(id)) feed = f;
        try {
            mode = Mode.valueOf(in.optString("mode", Mode.STOP.name()));
        } catch (IllegalArgumentException e) {
            mode = Mode.STOP;
        }
        setCotK(in.optDouble("cot", cotK));
        setSteamRatio(in.optDouble("steam", steamRatio));
        setResidenceS(in.optDouble("tau", residenceS));
        cokeMm = Math.max(0, in.optDouble("coke", 0));
        tmtTrip = in.has("tmt_trip") && in.getBoolean("tmt_trip");
    }
}
