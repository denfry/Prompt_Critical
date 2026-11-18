package dev.denfry.promptcritical.sim.petrochem;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Трубчатый реактор полиэтилена высокого давления (ПЭВД): этилен, сжатый гиперкомпрессором до 150–300 МПа,
 * полимеризуется по радикальному механизму; инициатор — кислород (20–100 ppm), подаваемый в несколько точек по длине.
 * Каждая зона инициирования разогревается от температуры подачи ≈ 170 °C (кислород начинает работать) до пика
 * 280–320 °C, после чего рубашка снимает тепло до следующей точки ввода. Тепло полимеризации 3,6 МДж/кг (95 кДж/моль)
 * при теплоёмкости сверхкритического этилена ≈ 2,6 кДж/(кг·К) даёт ≈ 14 К на процент превращения, поэтому за проход
 * превращается 20–35 % (Kiparissides et al., «Mathematical modeling of tubular low-density polyethylene reactors»,
 * Ind. Eng. Chem. Res. 32, 1993; Ullmann's, «Polyethylene», §4.1; ПЭВД в СССР — «Полимир»,
 * Новополоцк, 1968). Непрореагировавший этилен возвращается в цикл.
 *
 * <p>Пик зоны растёт с подачей кислорода как √[I] (скорость радикальной полимеризации R_p ∝ k_p[M]√(f·k_d[I]/k_t)) и с
 * давлением (плотность мономера); плотность полимера падает с пиковой температурой — больше коротких ветвлений
 * [зависимости — оценка по трендам Kiparissides]. Пиковая температура выше предела разложения — этилен разлагается
 * (C₂H₄ → C + CH₄, 127 кДж/моль, 4,5 МДж/кг): давление и температура мгновенно растут, разрывные мембраны вскрываются,
 * выброс горячего газа воспламеняется. Предел ≈ 340 °C при 250 МПа, ниже при большем давлении [оценка: аварии ПЭВД
 * происходят при пиках выше 320–350 °C].
 */
public final class LdpeReactor {
    public static final double HEAT_J_KG = 3.6e6, CP = 2.6e3, START_K = 170 + 273.15, ZONES = 3;
    public static final double REF_PPM = 40, REF_RISE_K = 120, REF_PRESSURE_MPA = 250;
    public static final double DECOMPOSITION_J_KG = 127e3 / 0.028054;
    /** Этилен в трубе реактора, кг: 250 м × Ø25 мм при плотности ≈ 500 кг/м³ [опытно-промышленный реактор, BALANCE]. */
    public static final double INVENTORY_KG = 0.25 * Math.PI * 0.025 * 0.025 * 250 * 500;
    /** Работа гиперкомпрессора на кг этилена, Дж/кг: два каскада до 250 МПа [порядок по Ullmann]. */
    public static final double COMPRESSION_J_KG = 0.8e6;

    private double pressureMpa = 250, oxygenPpm = 40;
    private boolean running;
    private boolean decomposed;
    private double peakK, conversion, polymerKgS, density, compressorW, totalPolymerKg;

    public double pressureMpa() {
        return pressureMpa;
    }

    public void setPressureMpa(double p) {
        pressureMpa = Math.max(150, Math.min(300, p));
    }

    public double oxygenPpm() {
        return oxygenPpm;
    }

    public void setOxygenPpm(double ppm) {
        oxygenPpm = Math.max(0, Math.min(150, ppm));
    }

    public boolean running() {
        return running;
    }

    public void setRunning(boolean on) {
        running = on && !decomposed;
    }

    public boolean decomposed() {
        return decomposed;
    }

    /** Ремонт после разложения: новые мембраны и очистка трубы от сажи. */
    public void repair() {
        decomposed = false;
    }

    public double peakK() {
        return peakK;
    }

    public double conversion() {
        return conversion;
    }

    public double polymerKgS() {
        return polymerKgS;
    }

    /** Плотность полиэтилена, г/см³. */
    public double density() {
        return density;
    }

    public double compressorW() {
        return compressorW;
    }

    public double totalPolymerKg() {
        return totalPolymerKg;
    }

    /** Предел разложения при давлении, К. */
    public static double decompositionK(double pressureMpa) {
        return 340 + 273.15 - 0.1 * (pressureMpa - REF_PRESSURE_MPA);
    }

    /** Пик температуры зоны, К. */
    public static double peakK(double oxygenPpm, double pressureMpa) {
        return START_K + REF_RISE_K * Math.sqrt(oxygenPpm / REF_PPM) * (pressureMpa / REF_PRESSURE_MPA);
    }

    /**
     * Шаг реактора.
     *
     * @param ethyleneKgS этилен в циркуляционном контуре, кг/с (подпитка — сколько превратится)
     * @return энергия разложения, Дж, если оно случилось на этом шаге; иначе 0
     */
    public double step(double dtS, double ethyleneKgS) {
        polymerKgS = 0;
        compressorW = 0;
        conversion = 0;
        if (!running || decomposed || ethyleneKgS <= 0 || oxygenPpm <= 0) {
            peakK = running ? START_K : 0;
            return 0;
        }
        peakK = peakK(oxygenPpm, pressureMpa);
        if (peakK > decompositionK(pressureMpa)) {
            decomposed = true;
            running = false;
            return INVENTORY_KG * DECOMPOSITION_J_KG;
        }
        conversion = Math.min(0.4, ZONES * CP * (peakK - START_K) / HEAT_J_KG);
        polymerKgS = conversion * ethyleneKgS;
        compressorW = ethyleneKgS * COMPRESSION_J_KG;
        density = Math.max(0.910, Math.min(0.935, 0.930 - 0.0002 * (peakK - (270 + 273.15))));
        totalPolymerKg += polymerKgS * dtS;
        return 0;
    }

    public void save(StateWriter out) {
        out.putDouble("p", pressureMpa);
        out.putDouble("o2", oxygenPpm);
        out.putBoolean("running", running);
        out.putBoolean("decomposed", decomposed);
        out.putDouble("total", totalPolymerKg);
    }

    public void load(StateReader in) {
        setPressureMpa(in.optDouble("p", 250));
        setOxygenPpm(in.optDouble("o2", 40));
        decomposed = in.has("decomposed") && in.getBoolean("decomposed");
        running = in.has("running") && in.getBoolean("running") && !decomposed;
        totalPolymerKg = in.optDouble("total", 0);
    }
}
