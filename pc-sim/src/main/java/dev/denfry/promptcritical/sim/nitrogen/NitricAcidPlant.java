package dev.denfry.promptcritical.sim.nitrogen;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.gasprocessing.GasCompressor;

/**
 * Неконцентрированная азотная кислота по Оствальду: контактный аппарат с пакетом платинородиевых сеток, котёл-утилизатор,
 * абсорбционная колонна с ситчатыми тарелками и (если заряжен катализатор) каталитическая очистка хвостовых газов
 * аммиаком. Воздух сжимает компрессор на валу, газотурбинный рекуператор на хвостовых газах возвращает 65 % работы.
 * Давление — как у советских схем: атмосферное (0,1 МПа), АК-72 (0,42 МПа на окислении) и УКЛ-7 (0,73 МПа)
 * (Атрощенко, Каргин, «Технология азотной кислоты», 1970; «Справочник азотчика», т. 2, 1987, гл. 1–3; Thiemann et
 * al., «Nitric Acid», Ullmann's, 2000).
 *
 * <p>Окисление 4NH₃ + 5O₂ → 4NO + 6H₂O на сетках; побочно 4NH₃ + 3O₂ → 2N₂ + 6H₂O. Выход NO наибольший около 810 °C при
 * атмосферном давлении (97–98 %) и около 880 °C при 0,73 МПа (94–95 %); вдали от оптимума падает квадратично [аппроксимация
 * данных Атрощенко]. Температура сеток — из баланса энтальпии (≈ 68 К на 1 % NH₃). Нижний предел взрываемости
 * аммиачно-воздушной смеси — 15 % при 20 °C и 0,1 МПа, ниже с подогревом и давлением [оценка по «Справочнику азотчика»]:
 * выше него смесь вспыхивает в аппарате. Потери платины — 0,05 г/т HNO₃ при 0,1 МПа, 0,3 г/т при 0,73 МПа, вдвое на
 * каждые 50 К выше 900 °C.
 *
 * <p>Окисление NO кислородом — уравнение Боденштейна d p_NO₂/dt = 2k·p_NO²·p_O₂, lg k = 652,1/T − 0,7356 (атм⁻²·с⁻¹):
 * реакция третьего порядка с отрицательным температурным коэффициентом, поэтому давление ускоряет её как P². На каждой
 * тарелке газ окисляется в межтарелочном объёме, затем NO₂ поглощается 3NO₂ + H₂O → 2HNO₃ + NO с эффективностью,
 * которая падает к нулю, когда крепость кислоты на тарелке приближается к предельной для давления: 50 % при 0,1 МПа,
 * 58 % при 0,42, 62 % при 0,73 МПа (азеотроп 68,4 %).
 */
public final class NitricAcidPlant {
    public static final double[] PRESSURES_MPA = {0.101325, 0.42, 0.73};
    /** Номинальный расход аммиака, моль/с (≈ 250 кг/ч) [BALANCE]. */
    public static final double RATED_NH3_MOL_S = 4.0;
    public static final int TRAYS = 30;
    /** Межтарелочный объём газа, м³; эффективность поглощения тарелки. */
    public static final double TRAY_VOLUME_M3 = 0.7, TRAY_EFFICIENCY = 0.45;
    public static final double EXPANDER_RECOVERY = 0.65, K_AIR = 1.4, AIR_IN_K = 293.15;
    /** Пакет сеток: платина, г. */
    public static final double GAUZE_PT_G = 400;
    public static final double NO_LHV_J_MOL = 226.5e3;
    private static final int NH3 = Species.NH3.ordinal(), O2 = Species.O2.ordinal(), NO = Species.NO.ordinal(),
            NO2 = Species.NO2.ordinal(), H2O = Species.H2O.ordinal(), N2 = Species.N2.ordinal();
    private static final double HNO3_KG_MOL = 0.063013, WATER_KG_MOL = 0.018015;

    private int pressureIndex = 2;
    private double ammoniaPercent = 10.5, strengthSet = 0.58, gauzePtG, denox;
    private boolean running;
    private Result last = Result.IDLE;

    /** Итог шага. Расходы — моль/с; крепость — массовая доля; потери платины — г/с. */
    public record Result(double nh3MolS, double gauzeK, double selectivity, double hno3MolS, double strength, double waterMolS,
            double tailNoxPpm, double absorption, double shaftW, double ptLossGS, double lowerLimitPercent, boolean ignited) {
        static final Result IDLE = new Result(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false);
    }

    public double pressureMpa() {
        return PRESSURES_MPA[pressureIndex];
    }

    public int pressureIndex() {
        return pressureIndex;
    }

    public void setPressureIndex(int i) {
        pressureIndex = Math.floorMod(i, PRESSURES_MPA.length);
    }

    public double ammoniaPercent() {
        return ammoniaPercent;
    }

    public void setAmmoniaPercent(double p) {
        ammoniaPercent = Math.max(6, Math.min(16, p));
    }

    public double strengthSet() {
        return strengthSet;
    }

    public void setStrengthSet(double s) {
        strengthSet = Math.max(0.45, Math.min(0.68, s));
    }

    public double gauzePtG() {
        return gauzePtG;
    }

    /** Новый пакет сеток. */
    public void loadGauze() {
        gauzePtG = GAUZE_PT_G;
    }

    public double denox() {
        return denox;
    }

    /** Заряд катализатора очистки хвостовых газов (на основе V₂O₅/TiO₂). */
    public void loadDenox() {
        denox = 1;
    }

    public boolean running() {
        return running;
    }

    public void setRunning(boolean r) {
        running = r;
    }

    public Result last() {
        return last;
    }

    // --- физика ---

    /** Наибольший выход NO на сетках при давлении {@code pMpa}. */
    public static double maxSelectivity(double pMpa) {
        return 0.98 - 0.0175 * Math.log(pMpa / 0.101325);
    }

    /** Оптимальная температура сеток, К. */
    public static double optimumK(double pMpa) {
        return 1083.15 + 35 * Math.log(pMpa / 0.101325);
    }

    /** Выход NO на сетках при температуре {@code t}, давлении и состоянии сеток (доля оставшейся платины). */
    public static double selectivity(double t, double pMpa, double gauze) {
        double dt = t - optimumK(pMpa);
        double s = maxSelectivity(pMpa) - (dt > 0 ? 2e-6 : 6e-6) * dt * dt;
        if (gauze < 0.3) s *= 0.7 + gauze;
        return Math.max(0, Math.min(1, s));
    }

    /** Нижний предел взрываемости NH₃ в воздухе, % об., при подогреве смеси {@code mixK} и давлении. */
    public static double lowerLimitPercent(double mixK, double pMpa) {
        return 15.0 - 0.010 * (mixK - 293.15) - 0.8 * Math.log(pMpa / 0.101325);
    }

    /** Предельная крепость кислоты, достижимая в колонне при давлении {@code pMpa}. */
    public static double maxStrength(double pMpa) {
        return Math.min(0.684, 0.50 + 0.061 * Math.log(pMpa / 0.101325));
    }

    /** Константа Боденштейна окисления NO, атм⁻²·с⁻¹. */
    public static double bodenstein(double t) {
        return Math.pow(10, 652.1 / t - 0.7356);
    }

    /** Подогрев смеси перед сетками: воздух после компрессора без охлаждения, К. */
    public static double mixK(double pMpa) {
        return pMpa < 0.2 ? AIR_IN_K : Math.min(523.15, GasCompressor.dischargeK(AIR_IN_K, pMpa / 0.101325) - 30);
    }

    /** Потери платины, г на моль HNO₃. */
    public static double ptLossPerMol(double pMpa, double gauzeK) {
        double perTonne = 0.05 + 0.25 * (pMpa - 0.101325) / (0.73 - 0.101325);
        double hot = Math.pow(2, Math.max(0, gauzeK - 1173.15) / 50);
        return perTonne * hot * HNO3_KG_MOL / 1000;
    }

    /** Сетки: состав после окисления и температура. */
    static double[] burn(double nh3, double air, double mixK, double pMpa, double gauze, double[] tOut) {
        double[] in = Species.air(air);
        in[NH3] = nh3;
        double h = Species.enthalpy(in, mixK);
        double t = mixK + 700, s = 0;
        double[] out = in;
        for (int i = 0; i < 8; i++) {
            s = selectivity(t, pMpa, gauze);
            out = in.clone();
            double toNo = nh3 * s, toN2 = nh3 - toNo;
            out[NH3] = 0;
            out[NO] += toNo;
            out[N2] += toN2 / 2;
            out[H2O] += 1.5 * nh3;
            out[O2] -= 1.25 * toNo + 0.75 * toN2;
            if (out[O2] < 0) out[O2] = 0;
            t = Species.temperatureFor(out, h, t);
        }
        tOut[0] = t;
        tOut[1] = s;
        return out;
    }

    /**
     * Шаг на {@code dtS}: доступно аммиака {@code nh3Mol} и воды {@code waterMol} (моль), вал даёт {@code shaftW}.
     */
    public Result step(double dtS, double nh3Mol, double waterMol, double shaftW) {
        if (!running || dtS <= 0 || gauzePtG <= 0) {
            last = Result.IDLE;
            return last;
        }
        double p = pressureMpa();
        double nh3 = Math.min(RATED_NH3_MOL_S, nh3Mol / dtS);
        double x = ammoniaPercent / 100;
        double air = nh3 * (1 - x) / x;
        double mix = mixK(p);
        double lel = lowerLimitPercent(mix, p);
        // вал: компрессор воздуха минус рекуператор
        double wAir = GasCompressor.idealWorkJPerMol(AIR_IN_K, 0.101325, p, K_AIR) * (1 - EXPANDER_RECOVERY);
        double secondaryAir = 0.1 * air;
        double need = (air + secondaryAir) * wAir;
        if (need > shaftW && need > 0) {
            double s = Math.max(0, shaftW) / need;
            nh3 *= s;
            air *= s;
            secondaryAir *= s;
            need = shaftW;
        }
        if (nh3 <= 0) {
            last = Result.IDLE;
            return last;
        }
        if (ammoniaPercent >= lel) {
            last = new Result(nh3, 0, 0, 0, 0, 0, 0, 0, need, 0, lel, true);
            return last;
        }
        double gauze = gauzePtG / GAUZE_PT_G;
        double[] ts = new double[2];
        double[] g = burn(nh3, air, mix, p, gauze, ts);
        Species.add(g, Species.air(secondaryAir));
        double water = g[H2O];
        g[H2O] = 0; // вода конденсируется в холодильнике-конденсаторе и идёт в колонну
        // крепость — по уставке, если воды хватает: в колонну идёт конденсат окисления (85 % воды реакции, остальное
        // уносят хвостовые газы) и добавочная вода; поглощение расходует ½ моль воды на моль HNO₃
        double condensate = 0.85 * water;
        Absorption a = absorb(g, p, strengthSet);
        double hno3 = a.hno3;
        double waterNeeded = hno3 * HNO3_KG_MOL * (1 - strengthSet) / strengthSet / WATER_KG_MOL + 0.5 * hno3;
        double processWater = Math.min(Math.max(0, waterNeeded - condensate), Math.max(0, waterMol / dtS));
        double strength = strengthSet;
        double inProduct = condensate + processWater - 0.5 * hno3;
        if (Math.abs(inProduct - (waterNeeded - 0.5 * hno3)) > 1e-9 * (1 + hno3)) {
            // воды мало — кислота крепче уставки; конденсата много — слабее
            strength = inProduct > 0 ? Math.min(0.684, hno3 * HNO3_KG_MOL / (hno3 * HNO3_KG_MOL + inProduct * WATER_KG_MOL)) : 0.684;
            a = absorb(g, p, strength);
            hno3 = a.hno3;
        }
        double nox = a.noxMolS;
        // очистка хвостовых газов аммиаком: 4NO + 4NH₃ + O₂ → 4N₂ + 6H₂O
        double reduced = nox * 0.9 * denox;
        nox -= reduced;
        double tail = a.tailMolS;
        double ppm = tail > 0 ? nox / tail * 1e6 : 0;
        double pt = ptLossPerMol(p, ts[0]) * hno3;
        gauzePtG = Math.max(0, gauzePtG - pt * dtS);
        double absorption = nh3 * ts[1] > 0 ? hno3 / (nh3 * ts[1]) : 0;
        last = new Result(nh3 + reduced, ts[0], ts[1], hno3, strength, processWater, ppm, absorption, need, pt, lel, false);
        return last;
    }

    private record Absorption(double hno3, double noxMolS, double tailMolS) {
    }

    /** Абсорбционная колонна: тарелки снизу вверх, крепость на тарелке падает от продукционной к верху по параболе. */
    private static Absorption absorb(double[] gas, double pMpa, double strength) {
        double no = gas[NO], no2 = gas[NO2], o2 = gas[O2];
        double inert = Species.total(gas) - no - no2 - o2;
        double pAtm = pMpa / 0.101325, t = 308.15, k = bodenstein(t), cMax = maxStrength(pMpa), hno3 = 0;
        for (int i = 0; i < TRAYS; i++) {
            double tot = inert + no + no2 + o2;
            double tau = TRAY_VOLUME_M3 / (tot * Species.R * t / (pMpa * 1e6));
            int sub = 10;
            double h = tau / sub;
            for (int j = 0; j < sub; j++) {
                double pNo = no / tot * pAtm, pO2 = o2 / tot * pAtm;
                double dp = 2 * k * pNo * pNo * pO2 * h; // атм NO₂
                double dn = Math.min(no, dp / pAtm * tot);
                no -= dn;
                no2 += dn;
                o2 = Math.max(0, o2 - dn / 2);
            }
            // крепость высока на нижних тарелках и быстро падает лишь к верху колонны
            double c = strength * (1 - (double) i * i / ((double) TRAYS * TRAYS));
            double eff = TRAY_EFFICIENCY * Math.max(0, 1 - Math.pow(Math.min(1, c / cMax), 4));
            double absorbed = no2 * eff;
            no2 -= absorbed;
            no += absorbed / 3;
            hno3 += absorbed * 2 / 3;
        }
        return new Absorption(hno3, no + no2, inert + no + no2 + o2);
    }

    public void save(StateWriter out) {
        out.putInt("p", pressureIndex);
        out.putDouble("nh3", ammoniaPercent);
        out.putDouble("strength", strengthSet);
        out.putDouble("pt", gauzePtG);
        out.putDouble("denox", denox);
        out.putBoolean("run", running);
    }

    public void load(StateReader in) {
        setPressureIndex(in.has("p") ? in.getInt("p") : 2);
        setAmmoniaPercent(in.optDouble("nh3", 10.5));
        setStrengthSet(in.optDouble("strength", 0.58));
        gauzePtG = Math.max(0, Math.min(GAUZE_PT_G, in.optDouble("pt", 0)));
        denox = Math.max(0, Math.min(1, in.optDouble("denox", 0)));
        running = in.has("run") && in.getBoolean("run");
    }
}
