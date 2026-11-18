package dev.denfry.promptcritical.sim.drilling;

/**
 * Дифференциальный прихват (09 §9.5.10): колонна стоит без вращения напротив проницаемого пласта при репрессии, и
 * фильтрационная корка прижимает УБТ к стенке. Сила прихвата F = μ_корки·Δp·A_контакта(t), площадь контакта растёт
 * со временем стоянки как √t (Outmans, «Mechanics of Differential Pressure Sticking of Drill Collars», Trans. AIME
 * 213, 1958): A = L·w, w = {@link #CONTACT_WIDTH_1H_M}·√(t/1 ч), но не шире половины окружности УБТ.
 *
 * <p>Освобождение — расхаживание: натяжка сверх веса свободной колонны не больше 80 % предела слабейшего звена
 * (§9.5.10). Слабейшее звено — тело бурильной трубы 5″ 19,5 фунт/фут группы E: 395 600 фунт-сил = 1,76 МН
 * (API RP 7G, табл. 2); канат 1⅛″ при запасе прочности 2 на расхаживании держит 0,58·10·0,81/2 = 2,35 МН
 * (§9.5.2), вышка — 6 МН (§9.5.1). Пока сила прихвата меньше допустимой натяжки, колонну можно сорвать; больше —
 * колонна прихвачена: ротор её не провернёт, бурить нельзя, а освободит её только снижение репрессии (ванна —
 * бэклог §9.5.10). Вращение свободной колонны сбрасывает время стоянки.
 */
public final class DifferentialSticking {
    /** Коэффициент трения о фильтрационную корку [BALANCE: в пределах 0,07–0,25 по Outmans и Helmick–Longley]. */
    public static final double MU_CAKE = 0.15;
    /** Ширина полосы контакта через час стоянки, м [BALANCE]. */
    public static final double CONTACT_WIDTH_1H_M = 0.05;
    /** Половина окружности УБТ 6½″, м: шире контакт не бывает. */
    public static final double MAX_CONTACT_WIDTH_M = Math.PI * 6.5 * 0.0254 / 2;
    /** Предел текучести тела трубы 5″ 19,5 фунт/фут E-75 (API RP 7G), Н. */
    public static final double PIPE_TENSILE_N = 1.76e6;
    /** Канат 1⅛″ EIPS, 10 струн, η = 0,810, запас прочности 2 при расхаживании (§9.5.2), Н. */
    public static final double ROPE_LIMIT_N = 0.58e6 * 10 * 0.810 / 2;
    /** Доля предела слабейшего звена, допустимая при расхаживании (§9.5.10). */
    public static final double WORK_FRACTION = 0.8;

    private double contactS;
    private boolean stuck;
    private double forceN;

    /** Предел натяжки на крюке при расхаживании, Н. */
    public static double hookLimitN() {
        return WORK_FRACTION * Math.min(PIPE_TENSILE_N, ROPE_LIMIT_N);
    }

    /** Ширина контакта после {@code seconds} стоянки, м. */
    public static double contactWidthM(double seconds) {
        return Math.min(MAX_CONTACT_WIDTH_M, CONTACT_WIDTH_1H_M * Math.sqrt(Math.max(0, seconds) / 3600));
    }

    /** Сила прихвата, Н, при репрессии {@code overbalancePa}, длине контакта и времени стоянки. */
    public static double forceN(double overbalancePa, double contactLengthM, double seconds) {
        if (overbalancePa <= 0 || contactLengthM <= 0) return 0;
        return MU_CAKE * overbalancePa * contactLengthM * contactWidthM(seconds);
    }

    /**
     * Шаг: {@code rotating} — ротор вращает колонну; {@code overbalancePa} — давление в стволе против пласта минус
     * пластовое; {@code contactLengthM} — длина УБТ против вскрытого коллектора; {@code freeHookLoadN} — вес свободной
     * колонны на крюке.
     */
    public void step(double dt, boolean rotating, double overbalancePa, double contactLengthM, double freeHookLoadN) {
        if (dt <= 0) return;
        if (rotating && !stuck) {
            contactS = 0;
        } else if (overbalancePa > 0 && contactLengthM > 0) {
            contactS += dt;
        }
        forceN = forceN(overbalancePa, contactLengthM, contactS);
        if (!stuck && forceN > overpullN(freeHookLoadN)) stuck = true;
    }

    /** Допустимая натяжка сверх веса свободной колонны, Н. */
    public static double overpullN(double freeHookLoadN) {
        return Math.max(0, hookLimitN() - freeHookLoadN);
    }

    /**
     * Расходить колонну: натяжка до предела. Если сила прихвата меньше допустимой натяжки — колонна свободна, время
     * стоянки обнуляется. Возвращает {@code true}, если колонна свободна.
     */
    public boolean workPipe(double freeHookLoadN) {
        if (forceN <= overpullN(freeHookLoadN)) {
            stuck = false;
            contactS = 0;
            forceN = 0;
            return true;
        }
        return false;
    }

    public boolean stuck() {
        return stuck;
    }

    public double stickingForceN() {
        return forceN;
    }

    public double contactSeconds() {
        return contactS;
    }

    /** Восстановить из сохранения. */
    public void restore(double contactSeconds, boolean isStuck) {
        contactS = Math.max(0, contactSeconds);
        stuck = isStuck;
    }
}
