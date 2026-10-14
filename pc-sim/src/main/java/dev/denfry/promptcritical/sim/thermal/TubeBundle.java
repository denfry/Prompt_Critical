package dev.denfry.promptcritical.sim.thermal;

import java.util.List;

/**
 * Трубный пучок ПГ или конденсатора (01 §1.4.7): {@code thermal.sg_axial_segments} сегментов по
 * ходу теплоносителя внутри труб, у каждого — свой узел внутри и своя поверхность снаружи
 * (обычно к общему узлу второго контура). Сторона 0 — внутри труб, 1 — снаружи.
 */
public final class TubeBundle implements HeatStructure {
    private final long id;
    private final Wall[] segments;

    private TubeBundle(long id, Wall[] segments) {
        this.id = id;
        this.segments = segments;
    }

    /**
     * @param inner поверхности внутри труб по сегментам (по ходу первичного теплоносителя)
     * @param outer поверхности снаружи по сегментам; размеры списков равны числу сегментов
     */
    public static TubeBundle of(long id, double innerRadius, double outerRadius, double totalLength, int tubes,
            int radialNodes, WallMaterial material, List<ConvectiveSurface> inner, List<ConvectiveSurface> outer,
            ThermalConfig config) {
        if (inner.isEmpty() || inner.size() != outer.size()) throw new IllegalArgumentException("segments");
        int m = inner.size();
        Wall[] s = new Wall[m];
        for (int k = 0; k < m; k++) {
            s[k] = Wall.tube(id, innerRadius, outerRadius, totalLength / m, tubes, radialNodes, material,
                    inner.get(k), outer.get(k), config);
        }
        return new TubeBundle(id, s);
    }

    public int segmentCount() {
        return segments.length;
    }

    public Wall segment(int k) {
        return segments[k];
    }

    public void setUniformTemperature(double temperature) {
        for (Wall w : segments) w.setUniformTemperature(temperature);
    }

    @Override
    public long id() {
        return id;
    }

    @Override
    public Geometry geometry() {
        return Geometry.TUBE_BUNDLE;
    }

    @Override
    public void step(double dt) {
        for (Wall w : segments) w.step(dt);
    }

    @Override
    public void setVolumetricPower(double wattsPerM3) {
        for (Wall w : segments) w.setVolumetricPower(wattsPerM3);
    }

    /** Средние по сегментам температуры узлов стенки. */
    @Override
    public double[] radialTemperatures() {
        double[] avg = segments[0].radialTemperatures();
        for (int k = 1; k < segments.length; k++) {
            double[] tk = segments[k].radialTemperatures();
            for (int i = 0; i < avg.length; i++) avg[i] += tk[i];
        }
        for (int i = 0; i < avg.length; i++) avg[i] /= segments.length;
        return avg;
    }

    @Override
    public double centerlineTemperature() {
        return Double.NaN;
    }

    @Override
    public double surfaceTemperature(int side) {
        double s = 0;
        for (Wall w : segments) s += w.surfaceTemperature(side);
        return s / segments.length;
    }

    @Override
    public double heatFlux(int side) {
        double a = 0;
        for (Wall w : segments) a += w.area(side);
        return heatRate(side) / a;
    }

    @Override
    public double heatRate(int side) {
        double q = 0;
        for (Wall w : segments) q += w.heatRate(side);
        return q;
    }

    /** Режим входного сегмента. */
    @Override
    public HeatTransferMode mode(int side) {
        return segments[0].mode(side);
    }

    @Override
    public double dnbr() {
        return Double.NaN;
    }

    @Override
    public double gapConductance() {
        return Double.NaN;
    }

    @Override
    public double meltFraction() {
        return 0;
    }

    @Override
    public double rodInternalPressure() {
        return Double.NaN;
    }

    @Override
    public boolean dnbOccurred() {
        for (Wall w : segments) if (w.dnbOccurred()) return true;
        return false;
    }

    @Override
    public double storedEnergy() {
        double e = 0;
        for (Wall w : segments) e += w.storedEnergy();
        return e;
    }
}
