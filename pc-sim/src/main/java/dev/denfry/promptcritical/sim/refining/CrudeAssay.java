package dev.denfry.promptcritical.sim.refining;

/**
 * Кривая истинных температур кипения (ИТК) нефти: накопленный выход по массе против температуры (ГОСТ 11011; ASTM
 * D2892 до 400 °C, D5236 выше). Между точками — линейная интерполяция, выход монотонно растёт. Нефть делят на узкие
 * псевдокомпоненты {@link #SLICE_K} по температуре кипения — так с ней работают модели ректификации.
 *
 * @param name         название
 * @param temperatureK температуры ИТК, К, по возрастанию
 * @param massFraction накопленный выход по массе на этих температурах, от 0 до 1
 * @param densityKgM3  плотность нефти при 20 °C
 */
public record CrudeAssay(String name, double[] temperatureK, double[] massFraction, double densityKgM3) {
    public static final double SLICE_K = 5;

    /**
     * Модельная лёгкая западносибирская нефть, ρ = 850 кг/м³: бензиновых фракций до 180 °C 25 %, светлых до 350 °C
     * 53,5 %, мазута 46,5 % — в пределах, приведённых для лёгких сернистых нефтей Западной Сибири (Ахметов С.А.,
     * «Технология глубокой переработки нефти и газа», 2002, гл. 2; Магарил Р.З., «Теоретические основы химических
     * процессов переработки нефти»). Точки — модельные, не паспорт конкретного месторождения.
     */
    public static final CrudeAssay WEST_SIBERIAN = of("west_siberian_light", 850,
            35, 0.0, 62, 0.045, 85, 0.085, 105, 0.12, 140, 0.18, 180, 0.25, 210, 0.305, 240, 0.36, 270, 0.41, 300, 0.46,
            350, 0.535, 400, 0.61, 450, 0.675, 500, 0.735, 550, 0.79, 600, 0.835, 700, 0.92, 850, 1.0);

    public CrudeAssay {
        if (temperatureK.length != massFraction.length || temperatureK.length < 2)
            throw new IllegalArgumentException("assay points");
        for (int i = 1; i < temperatureK.length; i++)
            if (!(temperatureK[i] > temperatureK[i - 1]) || massFraction[i] < massFraction[i - 1])
                throw new IllegalArgumentException("assay must be monotonic");
        if (massFraction[0] != 0 || massFraction[massFraction.length - 1] != 1) throw new IllegalArgumentException("assay spans 0…1");
    }

    /** По парам (°C, доля). */
    public static CrudeAssay of(String name, double densityKgM3, double... celsiusFraction) {
        int n = celsiusFraction.length / 2;
        double[] t = new double[n], x = new double[n];
        for (int i = 0; i < n; i++) {
            t[i] = celsiusFraction[2 * i] + 273.15;
            x[i] = celsiusFraction[2 * i + 1];
        }
        return new CrudeAssay(name, t, x, densityKgM3);
    }

    /** Накопленный выход до температуры, доля по массе. */
    public double yieldBelow(double tK) {
        double[] t = temperatureK, x = massFraction;
        if (tK <= t[0]) return 0;
        if (tK >= t[t.length - 1]) return 1;
        int i = 1;
        while (t[i] < tK) i++;
        return x[i - 1] + (x[i] - x[i - 1]) * (tK - t[i - 1]) / (t[i] - t[i - 1]);
    }

    /** Температура, до которой выкипает доля {@code fraction}, К. */
    public double temperatureAt(double fraction) {
        double[] t = temperatureK, x = massFraction;
        if (fraction <= 0) return t[0];
        if (fraction >= 1) return t[t.length - 1];
        int i = 1;
        while (x[i] < fraction) i++;
        if (x[i] == x[i - 1]) return t[i];
        return t[i - 1] + (t[i] - t[i - 1]) * (fraction - x[i - 1]) / (x[i] - x[i - 1]);
    }

    /** Выход фракции между температурами, доля по массе. */
    public double cut(double fromK, double toK) {
        return yieldBelow(toK) - yieldBelow(fromK);
    }

    /** Псевдокомпоненты: средние температуры кипения узких фракций. */
    public double[] sliceTemperaturesK() {
        double t0 = temperatureK[0], t1 = temperatureK[temperatureK.length - 1];
        int n = (int) Math.ceil((t1 - t0) / SLICE_K);
        double[] out = new double[n];
        for (int i = 0; i < n; i++) out[i] = t0 + (i + 0.5) * (t1 - t0) / n;
        return out;
    }

    /** Массовые доли псевдокомпонентов. */
    public double[] sliceFractions() {
        double t0 = temperatureK[0], t1 = temperatureK[temperatureK.length - 1];
        int n = (int) Math.ceil((t1 - t0) / SLICE_K);
        double[] out = new double[n];
        for (int i = 0; i < n; i++) out[i] = cut(t0 + i * (t1 - t0) / n, t0 + (i + 1) * (t1 - t0) / n);
        return out;
    }
}
