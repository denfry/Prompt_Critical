package dev.denfry.promptcritical.sim.acoustics;

import java.util.List;

/**
 * Спектр виброскорости вращающейся машины по диагностическим полосам (04a §4a.6): дискретные составляющие плюс
 * широкополосный фон, огибающая ускорения ВЧ и температура корпуса подшипника.
 *
 * <p>Общий уровень — как его меряет прибор по ISO 10816-1 §3: СКЗ виброскорости в полосе 10–1000 Гц, а у машин
 * медленнее 600 об/мин — 2–1000 Гц. Составляющие вне полосы (сепараторная у медленного вала, зубцовая у быстрого) в
 * общий уровень не входят, но в спектре видны.
 *
 * @param shaftHz      оборотная частота f_r, Гц
 * @param lines        составляющие по полосам
 * @param vBackground  широкополосный фон, м/с СКЗ
 * @param aHf          огибающая виброускорения 5–20 кГц, м/с² СКЗ
 * @param bearingTempK температура корпуса подшипника, K
 * @param machineClass класс машины для зон
 */
public record VibrationSpectrum(double shaftHz, List<Line> lines, double vBackground, double aHf, double bearingTempK,
        MachineClass machineClass) {
    /** Верх полосы измерения общего уровня, Гц. */
    public static final double BAND_HIGH_HZ = 1000;
    /** Скорость вала, ниже которой полоса начинается с 2 Гц, а не с 10 (ISO 10816-1 §3). */
    public static final double SLOW_MACHINE_RPM = 600;

    public enum Band {
        /** Оборотная: дисбаланс. */
        X1,
        /** Двойная оборотная: расцентровка, ослабление креплений. */
        X2,
        /** Лопаточная z·f_r: гидро- и аэродинамические пульсации. */
        BPF,
        /** Дефект наружного кольца. */
        BPFO,
        /** Внутреннего кольца. */
        BPFI,
        /** Тела качения. */
        BSF,
        /** Сепаратора. */
        FTF,
        /** Зубцовая z·f_r зубчатой передачи. */
        GMF
    }

    /** Составляющая: полоса, частота (Гц) и СКЗ виброскорости (м/с). */
    public record Line(Band band, double hz, double vRms) {
    }

    public static final VibrationSpectrum STILL = new VibrationSpectrum(0, List.of(), 0, 0, 0, MachineClass.I);

    public VibrationSpectrum {
        lines = List.copyOf(lines);
        if (machineClass == null) throw new IllegalArgumentException("machineClass");
    }

    /**
     * Тот же спектр с добавочным широкополосным фоном {@code vRms} (м/с, складывается квадратично) и огибающей ВЧ
     * {@code aHf} (м/с²): кавитация насоса (04a §4a.3) — удары схлопывающихся пузырей без своей частоты.
     */
    public VibrationSpectrum withBroadband(double vRms, double aHfAdd) {
        return new VibrationSpectrum(shaftHz, lines, Math.hypot(vBackground, vRms), aHf + aHfAdd, bearingTempK, machineClass);
    }

    public double shaftRpm() {
        return shaftHz * 60;
    }

    /** Нижняя граница полосы общего уровня, Гц. */
    public double bandLowHz() {
        return shaftRpm() < SLOW_MACHINE_RPM ? 2 : 10;
    }

    /** Общий уровень, м/с СКЗ: корень из суммы квадратов составляющих в полосе и фона. */
    public double overallRms() {
        double lo = bandLowHz(), s = vBackground * vBackground;
        for (Line l : lines) if (l.hz >= lo && l.hz <= BAND_HIGH_HZ) s += l.vRms * l.vRms;
        return Math.sqrt(s);
    }

    public IsoZone zone() {
        return machineClass.zoneOf(overallRms());
    }

    /** Составляющая полосы; null — у машины её нет. */
    public Line line(Band band) {
        for (Line l : lines) if (l.band == band) return l;
        return null;
    }

    /** СКЗ составляющей, м/с; 0 — нет. */
    public double v(Band band) {
        Line l = line(band);
        return l == null ? 0 : l.vRms;
    }

    /** Наибольшая составляющая; null — вал стоит. */
    public Line peak() {
        Line best = null;
        for (Line l : lines) if (best == null || l.vRms > best.vRms) best = l;
        return best;
    }
}
