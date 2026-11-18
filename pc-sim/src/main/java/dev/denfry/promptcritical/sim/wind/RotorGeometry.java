package dev.denfry.promptcritical.sim.wind;

/**
 * Геометрия ветроколеса: число лопастей, радиусы и сечения лопасти (середины кольцевых элементов BEM).
 * Углы крутки — от плоскости вращения, при нулевом угле установки лопасти.
 *
 * @param radiusM    радиус ветроколеса R, м
 * @param hubRadiusM радиус втулки, м
 * @param blades     число лопастей B
 * @param r          радиусы середин элементов, м
 * @param dr         ширина элементов, м
 * @param chord      хорда сечения, м
 * @param twistRad   крутка θ_T сечения, рад
 */
public record RotorGeometry(double radiusM, double hubRadiusM, int blades, double[] r, double[] dr, double[] chord,
        double[] twistRad) {

    public RotorGeometry {
        if (!(radiusM > hubRadiusM && hubRadiusM >= 0)) throw new IllegalArgumentException("radius");
        if (blades < 1) throw new IllegalArgumentException("blades");
        if (r.length == 0 || r.length != dr.length || r.length != chord.length || r.length != twistRad.length)
            throw new IllegalArgumentException("sections");
        r = r.clone();
        dr = dr.clone();
        chord = chord.clone();
        twistRad = twistRad.clone();
    }

    public int sections() {
        return r.length;
    }

    public double sweptAreaM2() {
        return Math.PI * radiusM * radiusM;
    }

    /**
     * Лопасть, оптимальная по Глауэрту для быстроходности λ_d с учётом закрутки следа (Manwell, McGowan,
     * Rogers, «Wind Energy Explained», 2-е изд., 2009, §3.7.3, ур. 3.105–3.106): в сечении с местной
     * быстроходностью λ_r угол притекания φ = (2/3)·arctg(1/λ_r), хорда c = 8πr(1 − cos φ)/(B·C_l,d), крутка
     * θ_T = φ − α_d. Корневые хорды такой лопасти нетехнологичны — хорда ограничена {@code maxChordM}, как у
     * реальных лопастей с линейной корневой частью (Burton et al., «Wind Energy Handbook», 2011, §3.7).
     *
     * @param sections     число кольцевых элементов
     * @param clDesign     расчётный C_l,d
     * @param alphaDesignRad расчётный угол атаки α_d, рад
     */
    public static RotorGeometry glauertOptimum(double radiusM, double hubRadiusM, int blades, double tsrDesign, double clDesign,
            double alphaDesignRad, double maxChordM, int sections) {
        if (sections < 4) throw new IllegalArgumentException("sections");
        double[] r = new double[sections], dr = new double[sections], c = new double[sections], t = new double[sections];
        double w = (radiusM - hubRadiusM) / sections;
        for (int i = 0; i < sections; i++) {
            double ri = hubRadiusM + (i + 0.5) * w;
            double lr = tsrDesign * ri / radiusM;
            double phi = 2.0 / 3 * Math.atan(1 / lr);
            r[i] = ri;
            dr[i] = w;
            c[i] = Math.min(maxChordM, 8 * Math.PI * ri * (1 - Math.cos(phi)) / (blades * clDesign));
            t[i] = phi - alphaDesignRad;
        }
        return new RotorGeometry(radiusM, hubRadiusM, blades, r, dr, c, t);
    }

    /** Средняя хорда (для удлинения R/c̄ в поляре). */
    public double meanChordM() {
        double s = 0, l = 0;
        for (int i = 0; i < r.length; i++) {
            s += chord[i] * dr[i];
            l += dr[i];
        }
        return s / l;
    }
}
