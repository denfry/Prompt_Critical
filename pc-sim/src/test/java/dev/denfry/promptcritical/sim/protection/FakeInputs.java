package dev.denfry.promptcritical.sim.protection;

import java.util.EnumMap;
import java.util.Map;

/** Измерения для тестов защиты: значения каналов задаются массивами, всё остальное — полями. */
final class FakeInputs implements ProtectionInputs {
    final Map<TripReason, double[]> values = new EnumMap<>(TripReason.class);
    final Map<TripReason, Integer> groups = new EnumMap<>(TripReason.class);
    final double[][] dnbr = new double[DnbrSignal.values().length][];
    double power = 1.0;
    double period = Double.POSITIVE_INFINITY;
    boolean lua, manual, torqueLost, pumpTripped, rodsAtBottom = true, withdrawal;

    /** Номинал ПР-300 (01 §1.14.1): 2 петли, 2 ПГ, 2 ГЦН; DNBR-вычислителей нет. */
    static FakeInputs pr300Nominal() {
        FakeInputs in = new FakeInputs();
        in.all(TripReason.HIGH_FLUX, 3, 1.0);
        in.all(TripReason.SHORT_PERIOD, 3, Double.POSITIVE_INFINITY);
        in.all(TripReason.HIGH_PRIMARY_PRESSURE, 3, 15.5e6);
        in.all(TripReason.LOW_PRIMARY_PRESSURE, 3, 15.5e6);
        in.loops(TripReason.LOW_COOLANT_FLOW, 1.0);
        in.all(TripReason.HIGH_COOLANT_TEMP, 3, 593.15);
        in.loops(TripReason.LOW_SG_LEVEL, 0.5);
        in.all(TripReason.HIGH_CONTAINMENT_PRESSURE, 3, 1.0e5);
        in.loops(TripReason.LOSS_OF_DRIVE_POWER, 1.0);
        in.all(TripReason.TURBINE_TRIP, 2, 0);
        in.all(TripReason.SEISMIC, 3, 0);
        return in;
    }

    FakeInputs set(TripReason r, double... v) {
        values.put(r, v);
        return this;
    }

    FakeInputs all(TripReason r, int channels, double v) {
        double[] a = new double[channels];
        java.util.Arrays.fill(a, v);
        return set(r, a);
    }

    /** Две группы по три канала (петли, ПГ, насосы). */
    FakeInputs loops(TripReason r, double v) {
        groups.put(r, 2);
        return all(r, 6, v);
    }

    FakeInputs remove(TripReason r) {
        values.remove(r);
        groups.remove(r);
        return this;
    }

    FakeInputs dnbrSignal(DnbrSignal s, double... perCalculator) {
        dnbr[s.ordinal()] = perCalculator;
        return this;
    }

    @Override
    public int channelCount(TripReason r) {
        if (r == TripReason.LOW_DNBR && dnbr[DnbrSignal.PRESSURE.ordinal()] != null)
            return dnbr[DnbrSignal.PRESSURE.ordinal()].length;
        double[] v = values.get(r);
        return v == null ? 0 : v.length;
    }

    @Override
    public double channelValue(TripReason r, int ch) {
        return values.get(r)[ch];
    }

    @Override
    public int groupCount(TripReason r) {
        return groups.getOrDefault(r, 1);
    }

    @Override
    public double dnbrSignal(DnbrSignal s, int calculator) {
        double[] v = dnbr[s.ordinal()];
        return v == null || calculator >= v.length ? Double.NaN : v[calculator];
    }

    @Override
    public double measuredPowerFrac() {
        return power;
    }

    @Override
    public double measuredPeriod() {
        return period;
    }

    @Override
    public boolean luaTripRequested() {
        return lua;
    }

    @Override
    public boolean manualTrip() {
        return manual;
    }

    @Override
    public boolean driveTorqueLost() {
        return torqueLost;
    }

    @Override
    public boolean pumpTripped() {
        return pumpTripped;
    }

    @Override
    public boolean rodsAtBottom() {
        return rodsAtBottom;
    }

    @Override
    public boolean rodWithdrawalStarted() {
        return withdrawal;
    }
}
