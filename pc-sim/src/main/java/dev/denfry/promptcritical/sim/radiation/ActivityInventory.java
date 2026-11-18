package dev.denfry.promptcritical.sim.radiation;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Набор радионуклидов с активностями в одном предмете: распад с накоплением дочерних (двухзвенные цепочки по
 * Бейтману), источник γ с самопоглощением в массе предмета и остаточное тепловыделение.
 *
 * <p>Самопоглощение: доля γ, выходящих наружу, f = (1 − e^{−μt})/(μt) — однородный объёмный источник в слое толщиной
 * t, без накопления; μ — как у свинца (тяжёлые металлы U, Pu, Pb близки по μ/ρ на 0,5–1,5 МэВ). Толщина —
 * 0,4·∛V: топливо вытянуто (ТВС 0,46 т при 3 г/см³ — ширина ≈ 21 см при длине 4 м). Для ТВС f ≈ 0,15 на 0,66 МэВ,
 * для лёгкого предмета — почти 1.
 */
public final class ActivityInventory {
    private final EnumMap<Emitter, Double> bq = new EnumMap<>(Emitter.class);
    private final double massKg, densityGcc;

    public ActivityInventory(Map<Emitter, Double> bq, double massKg, double densityGcc) {
        for (Map.Entry<Emitter, Double> e : bq.entrySet()) if (e.getValue() > 0) this.bq.put(e.getKey(), e.getValue());
        this.massKg = Math.max(0, massKg);
        this.densityGcc = densityGcc > 0 ? densityGcc : 1;
    }

    public Map<Emitter, Double> activities() {
        return new EnumMap<>(bq);
    }

    public double bq(Emitter e) {
        return bq.getOrDefault(e, 0.0);
    }

    public double totalBq() {
        double s = 0;
        for (double v : bq.values()) s += v;
        return s;
    }

    public double massKg() {
        return massKg;
    }

    public double densityGcc() {
        return densityGcc;
    }

    /**
     * Состав через {@code tS} секунд: родитель — экспонента, дочерний — накопление из родителя плюс собственный
     * распад (A₂(t) = A₂(0)e^{−λ₂t} + A₁(0)·λ₂/(λ₂ − λ₁)·(e^{−λ₁t} − e^{−λ₂t})).
     */
    public ActivityInventory decayed(double tS) {
        if (tS <= 0) return this;
        EnumMap<Emitter, Double> out = new EnumMap<>(Emitter.class);
        for (Map.Entry<Emitter, Double> e : bq.entrySet()) out.merge(e.getKey(), e.getKey().activity(e.getValue(), tS), Double::sum);
        for (Map.Entry<Emitter, Double> e : bq.entrySet()) {
            Emitter p = e.getKey(), d = p.daughter;
            if (d == null) continue;
            double l1 = p.lambda(), l2 = d.lambda();
            double grown = Math.abs(l2 - l1) < 1e-30 ? e.getValue() * l2 * tS * Math.exp(-l1 * tS)
                    : e.getValue() * l2 / (l2 - l1) * (Math.exp(-l1 * tS) - Math.exp(-l2 * tS));
            out.merge(d, grown, Double::sum);
        }
        return new ActivityInventory(out, massKg, densityGcc);
    }

    /** Доля γ энергии {@code e}, выходящих из массы предмета. */
    public double escape(double energyMeV) {
        if (massKg <= 0) return 1;
        double t = 0.4 * Math.cbrt(massKg * 1000 / densityGcc);
        double mt = ShieldMaterial.LEAD.massAttenuation(energyMeV) * densityGcc * t;
        return mt < 1e-6 ? 1 : (1 - Math.exp(-mt)) / mt;
    }

    /** Точечный источник с учётом самопоглощения. */
    public PointSource source() {
        PointSource s = PointSource.NONE;
        for (Map.Entry<Emitter, Double> e : bq.entrySet()) {
            PointSource line = e.getKey().source(e.getValue());
            double[] p = line.photons().clone();
            for (int i = 0; i < p.length; i++) p[i] *= escape(line.energyMeV()[i]);
            s = s.plus(new PointSource(line.energyMeV(), p, 0));
        }
        return s;
    }

    /** Остаточное тепловыделение, Вт. */
    public double decayHeatW() {
        double s = 0;
        for (Map.Entry<Emitter, Double> e : bq.entrySet()) s += e.getValue() * e.getKey().heatMeV * DoseRate.MEV_J;
        return s;
    }

    /** Мощность дозы на расстоянии без защиты, Зв/ч. */
    public double doseRateSvH(double rM) {
        return DoseRate.totalSvH(source(), rM, List.of());
    }
}
