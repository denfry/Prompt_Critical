package dev.denfry.promptcritical.sim.grid;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Силовое питание машин от сети 0,4 кВ: розетка, силовой щит или ИБП. Машины группы заявляют мощность, щит отдаёт
 * каждой долю {@link #supplyFactor()} — во столько раз медленнее она работает.
 *
 * <ul>
 *   <li>Розетка и щит отдают не больше номинала: при перегрузе доля меньше единицы. Скорость двигателей идёт за
 *       частотой сети ({@code f / 50}).</li>
 *   <li>Нулевая защита: если напряжение ниже {@value #UV_TRIP_PU} номинала дольше {@value #UV_DELAY_S} с, контактор
 *       отпадает и сам не включается (самозапуска нет), пока его не включат кнопкой «Пуск». Защита взводится, когда
 *       шина хоть раз была в норме, поэтому щит, поставленный до пуска станции, не отпадает.</li>
 *   <li>ИБП двойного преобразования держит выход на 50 Гц от сети, а без сети — от свинцовой батареи на шине
 *       {@value #UPS_BUS_V} В, пока она не разрядится. Без сети он не отпадает. Разряд по Пейкерту (показатель
 *       {@value #PEUKERT}) от десятичасового режима; заряд током десятичасового режима с КПД
 *       {@value #CHARGE_EFFICIENCY}; разряженная батарея включается снова после {@value #RECOVER_SOC} заряда.</li>
 * </ul>
 */
public final class PowerBoard {
    /** Ниже этого напряжения на вводе, о.е., отпадают контакторы двигателей. */
    public static final double UV_TRIP_PU = 0.85;
    /** Выдержка нулевой защиты, с. */
    public static final double UV_DELAY_S = 0.5;
    /** Частота, при которой двигатели вращаются с номинальной скоростью, Гц. */
    public static final double NOMINAL_HZ = 50;
    /** Окно частоты на вводе ИБП, Гц: вне него ИБП уходит на батарею. */
    public static final double UPS_F_MIN_HZ = 47.5, UPS_F_MAX_HZ = 52.5;
    /** КПД выпрямителя и инвертора ИБП вместе. */
    public static final double UPS_EFFICIENCY = 0.92;
    /** Шина постоянного тока ИБП: 16 аккумуляторов по 24 В, В. */
    public static final double UPS_BUS_V = 384;
    /** Ёмкость батареи ИБП, А·ч: около 20 минут на полной нагрузке. */
    public static final double UPS_BATTERY_AH = 18;
    public static final double PEUKERT = 1.25, CHARGE_EFFICIENCY = 0.9, RECOVER_SOC = 0.2;
    /** Запас батареи ИБП, Дж. */
    public static final double UPS_BATTERY_J = UPS_BUS_V * UPS_BATTERY_AH * 3600;

    public enum Kind {
        /** Бытовая розетка 16 А, 230 В. */
        SOCKET(3.7e3),
        /** Трёхфазная розетка 32 А, 400 В. */
        SOCKET_3P(22e3),
        /** Силовой щит, номинал вводного автомата переключается. */
        BOARD(100e3),
        /** ИБП 10 кВА. */
        UPS(9e3);

        public final double defaultRatedW;

        Kind(double defaultRatedW) {
            this.defaultRatedW = defaultRatedW;
        }
    }

    /** Номиналы вводного автомата силового щита, Вт (63, 160, 250 и 400 А при cos φ 0,9). */
    public static final double[] BOARD_RATINGS_W = {40e3, 100e3, 160e3, 250e3};

    private final Kind kind;
    private double ratedW;
    private double demandW;
    private boolean on = true;
    private boolean armed;
    private boolean tripped;
    private double underS;
    private boolean onBattery;
    private double factor;
    private double gridW;
    /** Запас батареи, Дж (только ИБП). */
    private double batteryJ;
    private boolean batteryCut;

    public PowerBoard(Kind kind) {
        this.kind = kind;
        this.ratedW = kind.defaultRatedW;
        this.batteryJ = kind == Kind.UPS ? UPS_BATTERY_J : 0;
    }

    public Kind kind() {
        return kind;
    }

    public double ratedW() {
        return ratedW;
    }

    /** Номинал щита; у розеток и ИБП он задан конструкцией. */
    public void setRatedW(double w) {
        if (kind == Kind.BOARD && w > 0 && Double.isFinite(w)) ratedW = w;
    }

    /** Следующий номинал вводного автомата щита. */
    public void cycleRating() {
        if (kind != Kind.BOARD) return;
        for (double r : BOARD_RATINGS_W) {
            if (r > ratedW + 1) {
                ratedW = r;
                return;
            }
        }
        ratedW = BOARD_RATINGS_W[0];
    }

    public double demandW() {
        return demandW;
    }

    /** Сумма заявок машин группы, Вт. */
    public void setDemandW(double w) {
        demandW = Double.isFinite(w) ? Math.max(0, w) : 0;
    }

    public boolean on() {
        return on;
    }

    /** Контактор отпал по нулевой защите и ждёт «Пуска». */
    public boolean tripped() {
        return tripped;
    }

    public boolean onBattery() {
        return onBattery;
    }

    /** Кнопка «Пуск»: включить контактор (снимает отпадание). */
    public void start() {
        on = true;
        tripped = false;
        underS = 0;
    }

    /** Кнопка «Стоп». */
    public void stop() {
        on = false;
        tripped = false;
    }

    /** Доля заявленной мощности, которую получает каждая машина группы, 0…1. */
    public double supplyFactor() {
        return factor;
    }

    /** Мощность из сети на прошлом шаге, Вт. */
    public double gridW() {
        return gridW;
    }

    /** Степень заряда батареи ИБП, 0…1 (у щитов и розеток — 0). */
    public double batterySoc() {
        return kind == Kind.UPS ? batteryJ / UPS_BATTERY_J : 0;
    }

    /**
     * Шаг: напряжение {@code vPu} и частота {@code fHz} на вводе.
     *
     * @return мощность из сети, Вт
     */
    public double step(double dt, double vPu, double fHz) {
        if (!(dt > 0)) throw new IllegalArgumentException("dt must be positive");
        boolean healthy = vPu >= UV_TRIP_PU;
        if (kind == Kind.UPS) return gridW = stepUps(dt, vPu, fHz);
        if (healthy) {
            armed = true;
            underS = 0;
        } else if (on && armed) {
            underS += dt;
            if (underS >= UV_DELAY_S) {
                on = false;
                tripped = true;
                armed = false;
            }
        }
        if (!on || !healthy) {
            factor = 0;
            return gridW = 0;
        }
        double delivered = Math.min(demandW, ratedW);
        double share = demandW > 0 ? delivered / demandW : 1;
        factor = share * Math.max(0, Math.min(1, fHz / NOMINAL_HZ));
        return gridW = delivered;
    }

    private double stepUps(double dt, double vPu, double fHz) {
        boolean inputOk = vPu >= UV_TRIP_PU && fHz >= UPS_F_MIN_HZ && fHz <= UPS_F_MAX_HZ;
        double out = on ? Math.min(demandW, ratedW) : 0;
        double share = demandW > 0 ? out / demandW : 1;
        if (inputOk) {
            onBattery = false;
            double chargeW = batteryJ < UPS_BATTERY_J ? Math.min(UPS_BATTERY_J / 36_000, (UPS_BATTERY_J - batteryJ) / (CHARGE_EFFICIENCY * dt)) : 0;
            batteryJ = Math.min(UPS_BATTERY_J, batteryJ + chargeW * CHARGE_EFFICIENCY * dt);
            if (batteryJ >= RECOVER_SOC * UPS_BATTERY_J) batteryCut = false;
            factor = on ? share : 0;
            return out / UPS_EFFICIENCY + chargeW;
        }
        onBattery = true;
        if (!on || batteryCut) {
            factor = 0;
            return 0;
        }
        double p = out / UPS_EFFICIENCY, p10 = UPS_BATTERY_J / 36_000;
        double drawn = p * dt * (p > p10 ? Math.pow(p / p10, PEUKERT - 1) : 1);
        batteryJ = Math.max(0, batteryJ - drawn);
        if (batteryJ <= 0) batteryCut = true;
        factor = batteryCut ? 0 : share;
        return 0;
    }

    public void save(StateWriter out) {
        out.putDouble("rated", ratedW);
        out.putDouble("demand", demandW);
        out.putBoolean("on", on);
        out.putBoolean("armed", armed);
        out.putBoolean("tripped", tripped);
        out.putDouble("under_s", underS);
        if (kind == Kind.UPS) {
            out.putDouble("battery_j", batteryJ);
            out.putBoolean("cut", batteryCut);
        }
    }

    public void load(StateReader in) {
        double r = in.optDouble("rated", kind.defaultRatedW);
        ratedW = kind == Kind.BOARD && r > 0 && Double.isFinite(r) ? r : kind.defaultRatedW;
        setDemandW(in.optDouble("demand", 0));
        on = !in.has("on") || in.getBoolean("on");
        armed = in.has("armed") && in.getBoolean("armed");
        tripped = in.has("tripped") && in.getBoolean("tripped");
        double u = in.optDouble("under_s", 0);
        underS = Double.isFinite(u) ? Math.max(0, u) : 0;
        if (kind == Kind.UPS) {
            double j = in.optDouble("battery_j", UPS_BATTERY_J);
            batteryJ = Double.isFinite(j) ? Math.max(0, Math.min(UPS_BATTERY_J, j)) : UPS_BATTERY_J;
            batteryCut = in.has("cut") && in.getBoolean("cut");
        }
    }
}
