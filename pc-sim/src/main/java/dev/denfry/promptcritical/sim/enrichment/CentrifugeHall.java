package dev.denfry.promptcritical.sim.enrichment;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import java.util.Random;

/**
 * Цех газовых центрифуг: машины одной конструкции ({@link Centrifuge}), собранные в идеальный каскад
 * ({@link IdealCascade}) под заданные обогащения отбора и отвала. Отбор конденсируется в ёмкости-десублиматоре, откуда
 * его сливают в баллон; отвал уходит в баллон отвала сразу. Защита по ядерной безопасности (блокировка СЦР)
 * останавливает отбор, когда U-235 в десублиматоре дошёл до безопасной массы ({@link Criticality}); без блокировки
 * десублиматор можно перегрузить до критической массы — вспышка СЦР.
 *
 * <p>Конструкции машин: алюминиевый подкритический ротор 0,6 м (ранние машины), длинный ротор из мартенситностареющей
 * стали 1,5 м — надкритический, с сильфонами (схема Циппе), и подкритический углепластиковый ротор 1 м. Скорость
 * задаётся долей от скорости разрыва; отказы — {@link Centrifuge#failuresPerYear} по игровому времени.
 */
public final class CentrifugeHall {
    public enum Design {
        ALUMINIUM(Centrifuge.Rotor.ALUMINIUM, 0.6), MARAGING(Centrifuge.Rotor.MARAGING_STEEL, 1.5), CARBON(Centrifuge.Rotor.CARBON_FIBRE, 1.0);

        public final Centrifuge.Rotor rotor;
        public final double lengthM;

        Design(Centrifuge.Rotor rotor, double lengthM) {
            this.rotor = rotor;
            this.lengthM = lengthM;
        }
    }

    /** Отвал копится в цехе до слива, но не больше, кг урана. */
    public static final double TAILS_HOLD_KG = 50;

    private Design design = Design.ALUMINIUM;
    private double speedFraction = 0.85, circulation = 1, productX = 0.045, tailsX = 0.0025;
    private boolean running, interlock = true;
    // десублиматор отбора и отвал до слива: уран, кг; доли U-235, U-234
    private double trapKg, trapX, trapX234, tailsKg, tailsXHeld, tailsX234;
    private Result last = Result.IDLE;

    /** Итог шага; потоки — кг урана/с. */
    public record Result(double productKgS, double tailsKgS, double feedKgS, double swuPerS, int stages, int crashes,
            double excursionFissions, boolean interlocked, boolean tooFewMachines, double alpha) {
        static final Result IDLE = new Result(0, 0, 0, 0, 0, 0, 0, false, false, 1);
    }

    public Design design() {
        return design;
    }

    public void setDesign(Design d) {
        if (!running) design = d;
    }

    public double speedFraction() {
        return speedFraction;
    }

    public void setSpeedFraction(double f) {
        speedFraction = Math.max(0.5, Math.min(1.05, f));
    }

    public double speed() {
        return speedFraction * design.rotor.burstSpeed();
    }

    public double circulation() {
        return circulation;
    }

    public void setCirculation(double c) {
        circulation = Math.max(0.2, Math.min(2.5, c));
    }

    public double productX() {
        return productX;
    }

    public void setProductX(double x) {
        productX = Math.max(0.008, Math.min(0.95, x));
    }

    public double tailsX() {
        return tailsX;
    }

    public void setTailsX(double x) {
        tailsX = Math.max(0.001, Math.min(0.0065, x));
    }

    public boolean running() {
        return running;
    }

    public void setRunning(boolean r) {
        running = r;
    }

    public boolean interlock() {
        return interlock;
    }

    public void setInterlock(boolean on) {
        interlock = on;
    }

    public double trapKg() {
        return trapKg;
    }

    public double trapX() {
        return trapX;
    }

    public double trapX234() {
        return trapX234;
    }

    public double trapU235Kg() {
        return trapKg * trapX;
    }

    public double tailsKg() {
        return tailsKg;
    }

    public double tailsHeldX() {
        return tailsXHeld;
    }

    public double tailsHeldX234() {
        return tailsX234;
    }

    public Result last() {
        return last;
    }

    /** Разделительная мощность одной машины при текущем режиме, кг ЕРР/с. */
    public double machineSwuPerS() {
        return Centrifuge.swuPerS(speed(), design.lengthM, circulation);
    }

    /** Слив из десублиматора не больше {@code kg} урана (весь состав одинаков); возвращает слитое. */
    public double drainProduct(double kg) {
        double d = Math.max(0, Math.min(kg, trapKg));
        trapKg -= d;
        if (trapKg <= 1e-12) trapKg = 0;
        return d;
    }

    public double drainTails(double kg) {
        double d = Math.max(0, Math.min(kg, tailsKg));
        tailsKg -= d;
        if (tailsKg <= 1e-12) tailsKg = 0;
        return d;
    }

    private static double mix(double m1, double x1, double m2, double x2) {
        return m1 + m2 > 0 ? (m1 * x1 + m2 * x2) / (m1 + m2) : x2;
    }

    /**
     * Шаг на {@code dtS} игровых секунд: {@code machines} машин, питания доступно {@code feedKg} урана составом
     * {@code xf}, {@code x234f}. Возвращает итог; питание, взятое за шаг, — feedKgS·dtS.
     */
    public Result step(double dtS, int machines, double feedKg, double xf, double x234f, Random rng) {
        if (!running || dtS <= 0 || machines <= 0 || feedKg <= 0 || !(xf > tailsX && xf < productX)) {
            last = new Result(0, 0, 0, 0, 0, 0, 0, false, false, 1);
            return last;
        }
        double v = speed();
        double swu = machineSwuPerS(), mFeed = Centrifuge.feedPerS(design.lengthM);
        double alpha = Centrifuge.alpha(swu, mFeed);
        // отказы роторов: пуассоновский поток по игровому времени
        int crashes = crashes(rng, machines, Centrifuge.failuresPerYear(design.rotor, v, design.lengthM) * dtS / Centrifuge.YEAR_S);
        IdealCascade c = new IdealCascade(alpha, productX, xf, tailsX);
        int working = machines - crashes;
        double p = c.productFor(working, mFeed);
        boolean few = p <= 0;
        double fPerP = Separation.feedPerProduct(productX, xf, tailsX), wPerP = fPerP - 1;
        p = Math.min(p, feedKg / dtS / fPerP);
        p = Math.min(p, Math.max(0, TAILS_HOLD_KG - tailsKg) / dtS / wPerP);
        // блокировка СЦР: десублиматор не наполняют выше безопасной массы U-235
        boolean locked = false;
        if (interlock) {
            double room = Criticality.safeU235Kg(productX) - trapU235Kg();
            double maxP = room > 0 ? room / productX / dtS : 0;
            if (p > maxP) {
                p = Math.max(0, maxP);
                locked = true;
            }
        }
        double pKg = p * dtS, wKg = p * wPerP * dtS;
        double x234p = c.productU234(x234f), x234w = c.tailsU234(x234f);
        trapX = mix(trapKg, trapX, pKg, productX);
        trapX234 = mix(trapKg, trapX234, pKg, x234p);
        trapKg += pKg;
        tailsXHeld = mix(tailsKg, tailsXHeld, wKg, tailsX);
        tailsX234 = mix(tailsKg, tailsX234, wKg, x234w);
        tailsKg += wKg;
        double fissions = Criticality.excursionFissions(trapU235Kg(), trapX);
        if (fissions > 0) {
            // вспышка: раствор вскипает и разбрызгивается, реакция гаснет; цех останавливают
            trapKg = 0;
            running = false;
        }
        last = new Result(p, p * wPerP, p * fPerP, swu * working, c.stages(), crashes, fissions, locked, few, alpha);
        return last;
    }

    private static int crashes(Random rng, int machines, double expected) {
        if (Double.isInfinite(expected)) return machines;
        if (expected <= 0) return 0;
        // Кнут: число событий пуассоновского потока
        double l = Math.exp(-Math.min(expected, 50)), prod = rng.nextDouble();
        int k = 0;
        while (prod > l && k < machines) {
            k++;
            prod *= rng.nextDouble();
        }
        return k;
    }

    public void save(StateWriter out) {
        out.putString("design", design.name());
        out.putDouble("speed", speedFraction);
        out.putDouble("circ", circulation);
        out.putDouble("xp", productX);
        out.putDouble("xw", tailsX);
        out.putBoolean("run", running);
        out.putBoolean("interlock", interlock);
        out.putDouble("trap", trapKg);
        out.putDouble("trap_x", trapX);
        out.putDouble("trap_x234", trapX234);
        out.putDouble("tails", tailsKg);
        out.putDouble("tails_x", tailsXHeld);
        out.putDouble("tails_x234", tailsX234);
    }

    public void load(StateReader in) {
        try {
            design = Design.valueOf(in.optString("design", Design.ALUMINIUM.name()));
        } catch (IllegalArgumentException e) {
            design = Design.ALUMINIUM;
        }
        setSpeedFraction(in.optDouble("speed", 0.85));
        setCirculation(in.optDouble("circ", 1));
        setProductX(in.optDouble("xp", 0.045));
        setTailsX(in.optDouble("xw", 0.0025));
        running = in.has("run") && in.getBoolean("run");
        interlock = !in.has("interlock") || in.getBoolean("interlock");
        trapKg = Math.max(0, in.optDouble("trap", 0));
        trapX = in.optDouble("trap_x", 0);
        trapX234 = in.optDouble("trap_x234", 0);
        tailsKg = Math.max(0, in.optDouble("tails", 0));
        tailsXHeld = in.optDouble("tails_x", 0);
        tailsX234 = in.optDouble("tails_x234", 0);
    }
}
