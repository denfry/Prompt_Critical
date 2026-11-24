package dev.denfry.promptcritical.sim.radiation;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Йодная профилактика (04b §4b.8.3): таблетка KI 130 мг насыщает щитовидную железу стабильным йодом, и она меньше
 * забирает радиоактивного I-131. Доля блокирования f_KI зависит от момента приёма относительно поступления
 * (Δt = t_KI − t_поступл, физ. ч; отрицательное — таблетка до поступления):
 * <pre>
 *   −24…0 → 0,95;  0…2 → 0,80;  2…8 → 0,80 → 0,40;  8…24 → 0,40 → 0,07;  иначе 0
 * </pre>
 * Таблетка действует {@value #ACTION_H} физ. ч, при долгом выбросе её пьют снова. Блокирование уменьшает дозу от
 * вдохнутого I-131 на долю щитовидной железы в эффективной дозе: e' = e·(1 − {@value #THYROID_SHARE}·f_KI)
 * (ориентир — рекомендации WHO/IAEA по йодной профилактике).
 *
 * <p>Учёт поступлений: ожидаемая доза от I-131 копится почасовыми корзинами за последние {@value #ACTION_H} ч. Доза
 * в корзину пишется уже с блокированием от принятой раньше таблетки; таблетка, принятая позже, возвращает часть дозы
 * этих корзин по таблице — но не больше, чем дало бы лучшее из двух блокирований.
 */
public final class IodineProphylaxis {
    public static final double ACTION_H = 24, THYROID_SHARE = 0.95;

    private record Intake(long hour, double rawSv, double blocked) {
    }

    private final Deque<Intake> intakes = new ArrayDeque<>();
    private double tabletHour = Double.NaN;

    /** Доля блокирования для таблетки, принятой через {@code dtHours} после поступления. */
    public static double blocking(double dtHours) {
        if (dtHours < -ACTION_H) return 0;
        if (dtHours <= 0) return 0.95;
        if (dtHours <= 2) return 0.80;
        if (dtHours <= 8) return 0.80 - 0.40 * (dtHours - 2) / 6;
        if (dtHours <= 24) return 0.40 - 0.33 * (dtHours - 8) / 16;
        return 0;
    }

    /** Множитель дозы от вдохнутого йода при блокировании {@code f}. */
    public static double doseFactor(double f) {
        return 1 - THYROID_SHARE * f;
    }

    /** Действует ли таблетка в час {@code hour}. */
    public boolean active(double hour) {
        return !Double.isNaN(tabletHour) && hour - tabletHour >= 0 && hour - tabletHour <= ACTION_H;
    }

    /** Час приёма последней таблетки, {@code NaN} — не принимали. */
    public double tabletHour() {
        return tabletHour;
    }

    /**
     * Поступление I-131 в час {@code hour}: ожидаемая доза без защиты {@code rawSv}.
     *
     * @return доза, которая на самом деле идёт в учёт, Зв
     */
    public double intake(double hour, double rawSv) {
        if (!(rawSv > 0)) return 0;
        double f = Double.isNaN(tabletHour) ? 0 : blocking(tabletHour - hour);
        long h = (long) Math.floor(hour);
        Intake last = intakes.peekLast();
        if (last != null && last.hour == h && last.blocked == f) {
            intakes.pollLast();
            intakes.addLast(new Intake(h, last.rawSv + rawSv, f));
        } else intakes.addLast(new Intake(h, rawSv, f));
        while (!intakes.isEmpty() && hour - intakes.peekFirst().hour > ACTION_H + 1) intakes.pollFirst();
        return rawSv * doseFactor(f);
    }

    /**
     * Принять таблетку в час {@code hour}.
     *
     * @return доза, которую таблетка возвращает из уже записанных поступлений, Зв
     */
    public double takeTablet(double hour) {
        tabletHour = hour;
        double refund = 0;
        Deque<Intake> next = new ArrayDeque<>();
        for (Intake in : intakes) {
            double f = Math.max(in.blocked, blocking(hour - (in.hour + 0.5)));
            refund += in.rawSv * THYROID_SHARE * (f - in.blocked);
            next.addLast(new Intake(in.hour, in.rawSv, f));
        }
        intakes.clear();
        intakes.addAll(next);
        return refund;
    }

    /** Восстановление из сохранения: только час таблетки (почасовые корзины не сохраняются — потеря меньше суток). */
    public static IodineProphylaxis restored(double tabletHour) {
        IodineProphylaxis p = new IodineProphylaxis();
        p.tabletHour = tabletHour;
        return p;
    }
}
