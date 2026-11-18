package dev.denfry.promptcritical.sim.isotopes;

import dev.denfry.promptcritical.sim.radiation.DoseRate;
import dev.denfry.promptcritical.sim.radiation.PointSource;
import dev.denfry.promptcritical.sim.radiation.ShieldMaterial;
import java.util.List;

/**
 * Гамма-дефектоскопия (12 §12.2.3): время экспозиции плёнки за стенкой стальной трубы
 *
 * <pre>t_exp = E_плёнки / K̇(r, s),</pre>
 *
 * где K̇ — керма в воздухе за слоем стали толщиной s на расстоянии r от источника с накоплением ({@link DoseRate}),
 * E_плёнки — требуемая керма за плёнкой ({@code isotopes.film_exposure_gy}, 2·10⁻⁴ Гр). Недодержка (меньше половины
 * нужной экспозиции) — плёнка прозрачная, передержка (больше вдвое) — чёрная: в обоих случаях дефектов не видно.
 */
public final class Radiography {
    /** Плёнка годна при экспозиции от {@value #UNDER} до {@value #OVER} от нужной. */
    public static final double UNDER = 0.5, OVER = 2.0;

    public enum Film { UNDEREXPOSED, GOOD, OVEREXPOSED }

    private Radiography() {
    }

    /** Керма за стенкой, Гр/с. */
    public static double kermaRateGyS(PointSource src, double rM, double steelCm) {
        List<DoseRate.Layer> layers = steelCm > 0 ? List.of(new DoseRate.Layer(ShieldMaterial.IRON, steelCm)) : List.of();
        return DoseRate.gammaGyH(src, rM, layers) / 3600;
    }

    /** Нужная экспозиция, с; бесконечность — источник пуст. */
    public static double exposureS(PointSource src, double rM, double steelCm, double filmGy) {
        double k = kermaRateGyS(src, rM, steelCm);
        return k > 0 ? filmGy / k : Double.POSITIVE_INFINITY;
    }

    /** Качество плёнки по набранной керме. */
    public static Film film(double gotGy, double filmGy) {
        if (gotGy < UNDER * filmGy) return Film.UNDEREXPOSED;
        if (gotGy > OVER * filmGy) return Film.OVEREXPOSED;
        return Film.GOOD;
    }
}
