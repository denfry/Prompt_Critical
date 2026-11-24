package dev.denfry.promptcritical.sim.npp;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.fuel.FuelDesign;
import java.util.ArrayList;
import java.util.List;

/**
 * Пара перегрузочных машин NPD, по одной у каждого торца: машина выше по течению вталкивает свежие пучки из своего
 * магазина, машина ниже по течению принимает выгоревшие (CANTEACH, «CANDU Origins and Evolution»). Машины работают
 * синхронно, поэтому в симуляции это один автомат с общим циклом:
 *
 * <ol>
 *   <li>{@link Phase#TRAVEL} — мост по высоте и тележка поперёк едут к каналу;</li>
 *   <li>{@link Phase#LOCK} — хоботы садятся на концевую арматуру с обоих торцов;</li>
 *   <li>{@link Phase#PRESSURISE} — головки заливаются D₂O и поднимаются до давления теплоносителя: без подачи D₂O
 *   на головки цикл стоит;</li>
 *   <li>{@link Phase#UNPLUG} — снимают уплотнительные пробки обоих концов;</li>
 *   <li>{@link Phase#EXCHANGE} — таран вталкивает пучки по одному, столько же выходит в приёмный магазин;</li>
 *   <li>{@link Phase#REPLUG}, {@link Phase#DEPRESSURISE}, {@link Phase#UNLOCK}, {@link Phase#RETURN} — в обратном
 *   порядке и домой, к лотку передачи.</li>
 * </ol>
 *
 * <p>Пока хоботы на канале под давлением, уплотнение хобота подтекает ({@link #SEAL_LEAK_KG_S}): D₂O теплоносителя с
 * тритием уходит в помещение. Выгоревшие пучки в магазине выделяют остаточное тепло; головку охлаждает та же подача
 * D₂O, и без неё за десятки минут оболочки перегреваются и лопаются ({@link Fault#SPENT_OVERHEAT}). Без питания
 * машина замирает там, где была.
 *
 * <p>Времена операций — [оценка] по масштабу машин CANDU (цикл у канала — порядка часа на CANDU-6) и сжаты примерно
 * вдвое [упрощение для §0.13]; скорости моста и тележки — 5 см/с [оценка].
 */
public final class NpdFuellingMachine {
    public enum Phase { IDLE, TRAVEL, LOCK, PRESSURISE, UNPLUG, EXCHANGE, REPLUG, DEPRESSURISE, UNLOCK, RETURN }

    public enum Fault { NONE, SPENT_OVERHEAT }

    public static final int MAGAZINE = 8;
    public static final double BRIDGE_M_S = 0.05, CARRIAGE_M_S = 0.05;
    public static final double LOCK_S = 45, PRESSURISE_S = 60, UNPLUG_S = 90, PUSH_S = 45, REPLUG_S = 90, DEPRESSURISE_S = 45,
            UNLOCK_S = 30;
    /** Стоянка у лотка передачи: смещение от оси каландра, м (внизу у западной колонны). */
    public static final double HOME_X = -2.6, HOME_Y = -2.4;
    /** Течь уплотнения хобота под давлением, кг/с [оценка]. */
    public static final double SEAL_LEAK_KG_S = 0.01;
    /**
     * Магазин с выгоревшими: остаточное тепло пучка сразу после выгрузки, Вт [оценка: ~5 % мощности пучка 78 кВт];
     * теплоёмкость пучка (15 кг UO₂ и оболочки), Дж/К; охлаждённая головка, К; разрушение оболочки, К.
     */
    public static final double SPENT_DECAY_W = 4000, BUNDLE_C_J_K = 5000, COOLED_K = 333.15, CLAD_FAIL_K = 1073.15,
            COOL_TAU_S = 60;
    /** Отвод тепла от магазина потоком D₂O головки, Вт/К [оценка]. */
    public static final double HEAD_UA_W_K = 2000;

    private Phase phase = Phase.IDLE;
    private Fault fault = Fault.NONE;
    private int channel = -1, toPush, pushed;
    private double t, x = HOME_X, y = HOME_Y, magazineK = COOLED_K, leakedKg, lastLeakKg;
    private boolean holdNoPower, holdNoD2o;
    private int fresh;
    private double freshKg, freshU235, freshU234;
    private final List<LightWaterCore.AssemblyFuel> spent = new ArrayList<>();

    /** Зарядить свежие пучки в магазин на стоянке; состав магазина усредняется. Возвращает принятое число. */
    public int loadFresh(int n, double bundleKg, double u235, double u234) {
        if (phase != Phase.IDLE || n <= 0) return 0;
        int take = Math.min(n, MAGAZINE - fresh);
        if (take <= 0) return 0;
        double w0 = fresh, w1 = take, sum = w0 + w1;
        freshKg = (freshKg * w0 + bundleKg * w1) / sum;
        freshU235 = (freshU235 * w0 + u235 * w1) / sum;
        freshU234 = (freshU234 * w0 + u234 * w1) / sum;
        fresh += take;
        return take;
    }

    /** Взять {@code n} свежих из магазина на стоянке (первая загрузка канала на заглушенном реакторе). */
    public boolean takeFresh(int n) {
        if (phase != Phase.IDLE || n > fresh) return false;
        fresh -= n;
        return true;
    }

    public double freshKg() {
        return freshKg;
    }

    public double freshU235() {
        return freshU235;
    }

    public double freshU234() {
        return freshU234;
    }

    /** Выгрузить выгоревшие из приёмного магазина в лоток (только на стоянке). */
    public List<LightWaterCore.AssemblyFuel> takeSpent() {
        if (phase != Phase.IDLE) return List.of();
        List<LightWaterCore.AssemblyFuel> out = new ArrayList<>(spent);
        spent.clear();
        magazineK = COOLED_K;
        return out;
    }

    /**
     * Начать перегрузку канала {@code ch}: {@code bundles} свежих из магазина. Нужны стоянка, загруженный канал, столько
     * свежих и место в приёмном магазине.
     */
    public boolean start(NpdReactor r, int ch, int bundles) {
        if (phase != Phase.IDLE || fault != Fault.NONE || ch < 0 || ch >= NpdReactor.CHANNELS) return false;
        if (bundles < 1 || bundles > fresh || spent.size() + bundles > MAGAZINE || !r.core().loaded(ch)) return false;
        channel = ch;
        toPush = bundles;
        pushed = 0;
        phase = Phase.TRAVEL;
        t = 0;
        return true;
    }

    /** Прервать: до снятия пробок — сразу назад; после — дотолкнуть текущий пучок и закрыть канал. */
    public void abort() {
        switch (phase) {
            case TRAVEL -> next(Phase.RETURN);
            case LOCK, PRESSURISE -> next(Phase.DEPRESSURISE);
            case UNPLUG, EXCHANGE -> toPush = pushed;
            default -> {
            }
        }
    }

    /** Сбросить неисправность после замены повреждённых пучков (магазин выгружен). */
    public boolean clearFault() {
        if (fault == Fault.NONE) return true;
        if (!spent.isEmpty() || phase != Phase.IDLE) return false;
        fault = Fault.NONE;
        return true;
    }

    /**
     * Шаг {@code dt}, с: {@code powered} — питание приводов, {@code d2oSupply} — подача D₂O на головки (давление и
     * охлаждение магазина).
     */
    public void step(double dt, NpdReactor r, boolean powered, boolean d2oSupply) {
        lastLeakKg = 0;
        holdNoPower = !powered && phase != Phase.IDLE;
        holdNoD2o = false;
        magazine(dt, d2oSupply);
        if (pressurised() && d2oSupply) {
            lastLeakKg = r.coolant().remove(SEAL_LEAK_KG_S * dt);
            leakedKg += lastLeakKg;
        }
        if (phase == Phase.IDLE || !powered) return;
        t += dt;
        switch (phase) {
            case TRAVEL -> {
                if (move(dt, NpdChannels.x(channel), NpdChannels.y(channel))) next(Phase.LOCK);
            }
            case LOCK -> after(LOCK_S, Phase.PRESSURISE);
            case PRESSURISE -> {
                if (!d2oSupply) {
                    holdNoD2o = true;
                    t -= dt;
                } else after(PRESSURISE_S, Phase.UNPLUG);
            }
            case UNPLUG -> after(UNPLUG_S, toPush > pushed ? Phase.EXCHANGE : Phase.REPLUG);
            case EXCHANGE -> {
                if (pushed >= toPush) {
                    next(Phase.REPLUG);
                } else if (t >= PUSH_S) {
                    LightWaterCore.AssemblyFuel out = r.refuel(channel, 1, freshKg, freshU235, freshU234);
                    fresh--;
                    pushed++;
                    if (out != null) spent.add(out);
                    t = 0;
                    if (pushed >= toPush) next(Phase.REPLUG);
                }
            }
            case REPLUG -> after(REPLUG_S, Phase.DEPRESSURISE);
            case DEPRESSURISE -> after(DEPRESSURISE_S, Phase.UNLOCK);
            case UNLOCK -> after(UNLOCK_S, Phase.RETURN);
            case RETURN -> {
                if (move(dt, HOME_X, HOME_Y)) {
                    phase = Phase.IDLE;
                    channel = -1;
                    t = 0;
                }
            }
            default -> {
            }
        }
    }

    private void after(double s, Phase then) {
        if (t >= s) next(then);
    }

    private void next(Phase p) {
        phase = p;
        t = 0;
    }

    private boolean move(double dt, double tx, double ty) {
        x = approach(x, tx, CARRIAGE_M_S * dt);
        y = approach(y, ty, BRIDGE_M_S * dt);
        return Math.abs(x - tx) < 1e-9 && Math.abs(y - ty) < 1e-9;
    }

    private static double approach(double v, double target, double step) {
        return Math.abs(target - v) <= step ? target : v + Math.signum(target - v) * step;
    }

    private void magazine(double dt, boolean cooled) {
        if (spent.isEmpty()) {
            magazineK += (COOLED_K - magazineK) * Math.min(1, dt / COOL_TAU_S);
            return;
        }
        double heat = spent.size() * SPENT_DECAY_W, c = spent.size() * BUNDLE_C_J_K;
        double ua = cooled ? HEAD_UA_W_K : 0;
        magazineK += (heat - ua * (magazineK - COOLED_K)) / c * dt;
        if (magazineK > CLAD_FAIL_K && fault == Fault.NONE) {
            fault = Fault.SPENT_OVERHEAT;
            for (int i = 0; i < spent.size(); i++) spent.set(i, failed(spent.get(i)));
        }
    }

    private static LightWaterCore.AssemblyFuel failed(LightWaterCore.AssemblyFuel a) {
        return new LightWaterCore.AssemblyFuel(a.design(), a.hm0Kg(), a.u235Kg(), a.u238Kg(), a.u236Kg(), a.pu239Kg(),
                a.pu240Kg(), a.pu241Kg(), a.pu242Kg(), a.fpKg(), a.u234(), a.burnupMwdKg(), true);
    }

    // ------------------------------------------------------------------ показания

    /** Хоботы на канале и головки под давлением теплоносителя. */
    public boolean pressurised() {
        return phase == Phase.UNPLUG || phase == Phase.EXCHANGE || phase == Phase.REPLUG
                || phase == Phase.PRESSURISE && t > 0;
    }

    public Phase phase() {
        return phase;
    }

    public Fault fault() {
        return fault;
    }

    public int channel() {
        return channel;
    }

    /** Секунды в текущей фазе. */
    public double phaseTime() {
        return t;
    }

    public int fresh() {
        return fresh;
    }

    public int spentCount() {
        return spent.size();
    }

    public int pushed() {
        return pushed;
    }

    public int toPush() {
        return toPush;
    }

    /** Положение тележки и моста относительно оси каландра, м. */
    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    /** Хобот на арматуре: 0 — отведён, 1 — сидит (для рендера). */
    public double snout() {
        return switch (phase) {
            case LOCK -> Math.min(1, t / LOCK_S);
            case PRESSURISE, UNPLUG, EXCHANGE, REPLUG, DEPRESSURISE -> 1;
            case UNLOCK -> 1 - Math.min(1, t / UNLOCK_S);
            default -> 0;
        };
    }

    /** Пробка канала вынута: 0 — на месте, 1 — в головке (для рендера). */
    public double plugOut() {
        return switch (phase) {
            case UNPLUG -> Math.min(1, t / UNPLUG_S);
            case EXCHANGE -> 1;
            case REPLUG -> 1 - Math.min(1, t / REPLUG_S);
            default -> 0;
        };
    }

    public double magazineK() {
        return magazineK;
    }

    public boolean holdNoPower() {
        return holdNoPower;
    }

    public boolean holdNoD2o() {
        return holdNoD2o;
    }

    /** Утечка D₂O теплоносителя через уплотнение за последний шаг и всего, кг. */
    public double lastLeakKg() {
        return lastLeakKg;
    }

    public double leakedKg() {
        return leakedKg;
    }

    public void save(StateWriter out) {
        out.putString("fm_phase", phase.name());
        out.putString("fm_fault", fault.name());
        out.putInt("fm_ch", channel);
        out.putInt("fm_to", toPush);
        out.putInt("fm_pushed", pushed);
        out.putDouble("fm_t", t);
        out.putDouble("fm_x", x);
        out.putDouble("fm_y", y);
        out.putDouble("fm_mk", magazineK);
        out.putDouble("fm_leaked", leakedKg);
        out.putInt("fm_fresh", fresh);
        out.putDouble("fm_fkg", freshKg);
        out.putDouble("fm_f235", freshU235);
        out.putDouble("fm_f234", freshU234);
        out.putInt("fm_spent", spent.size());
        for (int i = 0; i < spent.size(); i++) {
            LightWaterCore.AssemblyFuel a = spent.get(i);
            String k = "fm_s" + i + "_";
            out.putString(k + "d", a.design().name());
            double[] v = {a.hm0Kg(), a.u235Kg(), a.u238Kg(), a.u236Kg(), a.pu239Kg(), a.pu240Kg(), a.pu241Kg(), a.pu242Kg(),
                    a.fpKg(), a.u234(), a.burnupMwdKg()};
            for (int j = 0; j < v.length; j++) out.putDouble(k + j, v[j]);
            out.putBoolean(k + "f", a.failed());
        }
    }

    public void load(StateReader in) {
        phase = parse(Phase.class, in.optString("fm_phase", "IDLE"), Phase.IDLE);
        fault = parse(Fault.class, in.optString("fm_fault", "NONE"), Fault.NONE);
        channel = in.optInt("fm_ch", -1);
        toPush = in.optInt("fm_to", 0);
        pushed = in.optInt("fm_pushed", 0);
        t = in.optDouble("fm_t", 0);
        x = in.optDouble("fm_x", HOME_X);
        y = in.optDouble("fm_y", HOME_Y);
        magazineK = in.optDouble("fm_mk", COOLED_K);
        leakedKg = in.optDouble("fm_leaked", 0);
        fresh = in.optInt("fm_fresh", 0);
        freshKg = in.optDouble("fm_fkg", 0);
        freshU235 = in.optDouble("fm_f235", 0);
        freshU234 = in.optDouble("fm_f234", 0);
        spent.clear();
        int n = in.optInt("fm_spent", 0);
        for (int i = 0; i < n; i++) {
            String k = "fm_s" + i + "_";
            double[] v = new double[11];
            for (int j = 0; j < v.length; j++) v[j] = in.optDouble(k + j, 0);
            FuelDesign d = parse(FuelDesign.class, in.optString(k + "d", "NPD_19"), FuelDesign.NPD_19);
            spent.add(new LightWaterCore.AssemblyFuel(d, v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8], v[9], v[10],
                    in.optInt(k + "f", 0) == 1));
        }
        if (phase != Phase.IDLE && (channel < 0 || channel >= NpdReactor.CHANNELS)) phase = Phase.RETURN;
    }

    private static <E extends Enum<E>> E parse(Class<E> c, String s, E fallback) {
        try {
            return Enum.valueOf(c, s);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
