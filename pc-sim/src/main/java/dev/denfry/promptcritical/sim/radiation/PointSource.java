package dev.denfry.promptcritical.sim.radiation;

/**
 * Точечный изотропный источник: линии γ (энергия, МэВ, и выход фотонов, 1/с) и быстрые нейтроны деления (1/с). Для
 * мгновенного выброса (вспышка СЦР) те же поля означают полное число частиц, а «мощность дозы» — дозу.
 */
public record PointSource(double[] energyMeV, double[] photons, double neutrons) {
    public static final PointSource NONE = new PointSource(new double[0], new double[0], 0);

    public PointSource {
        if (energyMeV.length != photons.length) throw new IllegalArgumentException("lines and intensities differ");
    }

    /** Источник с одной эффективной линией. */
    public static PointSource line(double energyMeV, double photons, double neutrons) {
        return new PointSource(new double[] {energyMeV}, new double[] {photons}, neutrons);
    }

    /** Сумма двух источников в одной точке. */
    public PointSource plus(PointSource o) {
        double[] e = new double[energyMeV.length + o.energyMeV.length], p = new double[e.length];
        System.arraycopy(energyMeV, 0, e, 0, energyMeV.length);
        System.arraycopy(o.energyMeV, 0, e, energyMeV.length, o.energyMeV.length);
        System.arraycopy(photons, 0, p, 0, photons.length);
        System.arraycopy(o.photons, 0, p, photons.length, o.photons.length);
        return new PointSource(e, p, neutrons + o.neutrons);
    }

    public PointSource scaled(double k) {
        double[] p = photons.clone();
        for (int i = 0; i < p.length; i++) p[i] *= k;
        return new PointSource(energyMeV, p, neutrons * k);
    }

    public boolean empty() {
        double s = neutrons;
        for (double v : photons) s += v;
        return s <= 0;
    }
}
