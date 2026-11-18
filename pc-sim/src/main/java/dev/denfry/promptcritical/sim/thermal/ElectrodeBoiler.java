package dev.denfry.promptcritical.sim.thermal;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import java.util.Objects;

/**
 * Электродный паровой котёл высокого напряжения: ток идёт через саму котловую воду между электродами, вода — активное
 * сопротивление, вся мощность выделяется в ней. Сопротивление обратно погружённой длине электродов, поэтому
 * мощность P = P_ном·(U/U_ном)²·(h/h_эл) регулируется уровнем воды в электродной камере: насос циркуляции или клапан
 * поднимают воду на электроды — мощность растёт, сливают — падает (принцип котлов Parat и отечественных
 * электродных котлов КЭВ 6–10 кВ). Уровень меняется со скоростью {@link #LEVEL_RATE_PER_S} полного хода в секунду,
 * КПД — {@value #EFFICIENCY} (потери только через изоляцию корпуса). Пар идёт в общий паровой коллектор котельной.
 *
 * <p>Автоматика сброса излишка ({@link Mode#AUTO}): при частоте выше номинала на {@value #START_HZ_ABOVE} Гц котёл
 * набирает нагрузку линейно до полной при {@value #FULL_HZ_ABOVE} Гц сверху — как ограниченное частотное
 * регулирование при повышении частоты LFSM-O (Регламент ЕС 2016/631, ст. 13: порог 50,2 Гц), только наоборот: не
 * генератор снижает выдачу, а нагрузка забирает избыток. Нет сети или нет котла, принимающего пар, — электроды
 * осушаются.
 */
public final class ElectrodeBoiler {
    public enum Mode { OFF, MANUAL, AUTO }

    public static final double START_HZ_ABOVE = 0.2, FULL_HZ_ABOVE = 1.0;
    /** Полный ход уровня за минуту [оценка: насос циркуляции электродной камеры]. */
    public static final double LEVEL_RATE_PER_S = 1.0 / 60;
    public static final double EFFICIENCY = 0.99;

    private final double ratedPowerW;
    private Mode mode = Mode.AUTO;
    private double manualSetpoint = 0.5;
    /** Погружение электродов, доля длины. */
    private double level;
    private double demandFraction;

    public ElectrodeBoiler(double ratedPowerW) {
        if (!(ratedPowerW > 0)) throw new IllegalArgumentException("ratedPowerW");
        this.ratedPowerW = ratedPowerW;
    }

    public double ratedPowerW() {
        return ratedPowerW;
    }

    public Mode mode() {
        return mode;
    }

    public void setMode(Mode m) {
        mode = Objects.requireNonNull(m, "mode");
    }

    public double manualSetpoint() {
        return manualSetpoint;
    }

    public void setManualSetpoint(double fraction) {
        manualSetpoint = Math.max(0, Math.min(1, fraction));
    }

    /** Погружение электродов, доля. */
    public double level() {
        return level;
    }

    /** Задание мощности автоматики, доля номинальной. */
    public double demandFraction() {
        return demandFraction;
    }

    /** Задание по частоте: 0 до порога, 1 — при {@link #FULL_HZ_ABOVE} сверх номинала. */
    public static double frequencyDemand(double f, double f0) {
        double x = (f - f0 - START_HZ_ABOVE) / (FULL_HZ_ABOVE - START_HZ_ABOVE);
        return Math.max(0, Math.min(1, x));
    }

    /**
     * Шаг уровня в электродной камере.
     *
     * @param frequencyHz частота сети (0 — сети нет)
     * @param steamSink   пар есть куда отдать (котёл на коллекторе цел и не на предохранительном клапане)
     */
    public void step(double dtS, double frequencyHz, double nominalHz, boolean steamSink) {
        boolean live = frequencyHz > 0 && steamSink;
        demandFraction = !live ? 0 : switch (mode) {
            case OFF -> 0;
            case MANUAL -> manualSetpoint;
            case AUTO -> frequencyDemand(frequencyHz, nominalHz);
        };
        double step = LEVEL_RATE_PER_S * dtS;
        level += Math.max(-step, Math.min(step, demandFraction - level));
    }

    /** Мощность при номинальном напряжении на нынешнем уровне, Вт: сопротивление воды задаёт нагрузку сети. */
    public double powerAtRatedVoltageW() {
        return ratedPowerW * level;
    }

    /** Тепло в пар при фактической мощности из сети, Вт. */
    public static double heatW(double electricW) {
        return Math.max(0, electricW) * EFFICIENCY;
    }

    public void save(StateWriter out) {
        out.putString("mode", mode.name());
        out.putDouble("manual", manualSetpoint);
        out.putDouble("level", level);
    }

    public void load(StateReader in) {
        try {
            mode = Mode.valueOf(in.optString("mode", Mode.AUTO.name()));
        } catch (IllegalArgumentException e) {
            mode = Mode.AUTO;
        }
        manualSetpoint = in.optDouble("manual", 0.5);
        level = Math.max(0, Math.min(1, in.optDouble("level", 0)));
    }
}
