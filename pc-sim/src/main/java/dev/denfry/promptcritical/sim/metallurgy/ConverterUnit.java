package dev.denfry.promptcritical.sim.metallurgy;

import dev.denfry.promptcritical.sim.core.FastContext;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Двойник кислородного конвертера {@code bof_vessel} (08 §8.6.1): {@link Converter} и что задаёт мир — наклон
 * корпуса (продувка только в вертикали), высота фурмы и кислород. Кислород мир отпускает каждый тик из буфера фурмы
 * по {@link #oxygenDemandNm3S}; шаг SLOW дует средним за период. Конвертерный газ копится и забирается миром в бак
 * газоотвода ({@link #takeOffgasNm3}).
 */
public final class ConverterUnit implements IronmakingUnit {
    public static final double DEFAULT_O2_NM3S = 0.5, MAX_O2_NM3S = 1.5;
    /** Фурма: рабочая высота над ванной по умолчанию; выше {@link #LANCE_PARKED_M} — поднята из горловины, м. */
    public static final double DEFAULT_LANCE_M = 1.2, LANCE_PARKED_M = 3.0, MAX_LANCE_M = 4.0;
    /** Отклонение корпуса от вертикали, при котором ещё можно дуть, градусы. */
    public static final double UPRIGHT_DEG = 5;

    private final Converter converter = new Converter(SteelmakingConfig.defaults());
    private final SteelmakingLog log = new SteelmakingLog();
    private double o2SetNm3S = DEFAULT_O2_NM3S, lanceM = MAX_LANCE_M, tiltDeg;
    private double o2PendingNm3, offgasNm3, lastO2Nm3S;

    public ConverterUnit() {
        converter.setEventSink(log);
    }

    @Override
    public String deviceType() {
        return IronmakingSubsystem.CONVERTER;
    }

    @Override
    public void step(IronmakingSubsystem works, FastContext ctx, double slowDt) {
        if (slowDt <= 0) return;
        lastO2Nm3S = o2PendingNm3 / slowDt;
        o2PendingNm3 = 0;
        converter.stepSlow(slowDt, lastO2Nm3S, lanceM);
        offgasNm3 += converter.offgasNm3S() * slowDt;
    }

    /** Дуть можно: в корпусе металл, корпус вертикален, фурма опущена в горловину. */
    public boolean blowReady() {
        return converter.phase() != ConverterPhase.EMPTY && Math.abs(tiltDeg) <= UPRIGHT_DEG && lanceM < LANCE_PARKED_M;
    }

    /** Сколько кислорода нужно сейчас, нм³/с физ. */
    public double oxygenDemandNm3S() {
        return blowReady() ? o2SetNm3S : 0;
    }

    /** Кислород, отпущенный миром за тик, нм³. */
    public void supplyOxygen(double nm3) {
        if (nm3 > 0) o2PendingNm3 += nm3;
    }

    /** Забрать накопленный конвертерный газ, нм³ (не больше {@code maxNm3}). */
    public double takeOffgasNm3(double maxNm3) {
        double t = Math.max(0, Math.min(offgasNm3, maxNm3));
        offgasNm3 -= t;
        return t;
    }

    public void setTilt(double deg) {
        tiltDeg = deg;
    }

    public double tiltDeg() {
        return tiltDeg;
    }

    public void setOxygenSetpoint(double nm3S) {
        o2SetNm3S = Math.max(0, Math.min(MAX_O2_NM3S, nm3S));
    }

    public double oxygenSetpointNm3S() {
        return o2SetNm3S;
    }

    /** Дутьё последнего шага SLOW, нм³/с физ. */
    public double lastOxygenNm3S() {
        return lastO2Nm3S;
    }

    public void setLance(double m) {
        lanceM = Math.max(0, Math.min(MAX_LANCE_M, m));
    }

    public double lanceM() {
        return lanceM;
    }

    public Converter converter() {
        return converter;
    }

    public SteelmakingLog log() {
        return log;
    }

    @Override
    public void save(StateWriter out) {
        out.putDoubles("ctl", new double[] {o2SetNm3S, lanceM, tiltDeg, o2PendingNm3, offgasNm3}, 5);
        converter.save(out);
    }

    @Override
    public void load(StateReader in) {
        double[] c = in.getDoubles("ctl");
        o2SetNm3S = c[0];
        lanceM = c[1];
        tiltDeg = c[2];
        o2PendingNm3 = c[3];
        offgasNm3 = c[4];
        converter.load(in);
    }
}
