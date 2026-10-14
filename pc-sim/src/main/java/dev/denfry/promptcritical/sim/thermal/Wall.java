package dev.denfry.promptcritical.sim.thermal;

import java.util.Objects;

/**
 * Стенка с постоянными свойствами материала (01 §1.2.3): трубы (m = 1) и пластины (m = 0).
 * Узлы — в точках сетки по толщине, крайние лежат на поверхностях и владеют половиной ячейки.
 * Сторона 0 — внутренняя поверхность трубы, сторона 1 — наружная.
 */
public final class Wall extends ConductionStructure {
    private final WallMaterial material;
    private final double[] volume;

    private Wall(long id, Geometry geometry, int nodes, WallMaterial material, ThermalConfig config) {
        super(id, geometry, nodes, config);
        this.material = Objects.requireNonNull(material, "material");
        volume = new double[nodes];
    }

    /**
     * {@code count} одинаковых труб длиной {@code length}; поверхности — внутренняя и наружная
     * (null — адиабата). Площади сторон — суммарные по всем трубам.
     */
    public static Wall tube(long id, double innerRadius, double outerRadius, double length, int count, int nodes,
            WallMaterial material, ConvectiveSurface inner, ConvectiveSurface outer, ThermalConfig config) {
        if (!(innerRadius > 0 && outerRadius > innerRadius && length > 0 && count > 0)) {
            throw new IllegalArgumentException("geometry");
        }
        Wall w = new Wall(id, Geometry.TUBE, nodes, material, config);
        double[] r = new double[nodes];
        for (int i = 0; i < nodes; i++) r[i] = innerRadius + (outerRadius - innerRadius) * i / (nodes - 1);
        double lc = length * count, k = material.conductivity();
        for (int i = 0; i < nodes; i++) {
            double a = i == 0 ? innerRadius : 0.5 * (r[i - 1] + r[i]);
            double b = i == nodes - 1 ? outerRadius : 0.5 * (r[i] + r[i + 1]);
            w.volume[i] = Math.PI * (b * b - a * a) * lc;
        }
        for (int i = 0; i < nodes - 1; i++) w.cond[i] = 2 * Math.PI * k * lc / Math.log(r[i + 1] / r[i]);
        w.area[0] = 2 * Math.PI * innerRadius * lc;
        w.area[1] = 2 * Math.PI * outerRadius * lc;
        w.surfaces[0] = inner;
        w.surfaces[1] = outer;
        return w;
    }

    /** Пластина толщиной {@code thickness} и площадью {@code area}; стороны 0 и 1 (null — адиабата). */
    public static Wall slab(long id, double thickness, double area, int nodes, WallMaterial material,
            ConvectiveSurface side0, ConvectiveSurface side1, ThermalConfig config) {
        if (!(thickness > 0 && area > 0)) throw new IllegalArgumentException("geometry");
        Wall w = new Wall(id, Geometry.SLAB, nodes, material, config);
        double dx = thickness / (nodes - 1);
        for (int i = 0; i < nodes; i++) w.volume[i] = area * dx * (i == 0 || i == nodes - 1 ? 0.5 : 1);
        for (int i = 0; i < nodes - 1; i++) w.cond[i] = material.conductivity() * area / dx;
        w.area[0] = w.area[1] = area;
        w.surfaces[0] = side0;
        w.surfaces[1] = side1;
        return w;
    }

    @Override
    void updateProperties() {
        double rc = material.density() * material.heatCapacity();
        for (int i = 0; i < n; i++) cap[i] = rc * volume[i];
    }

    /** Объёмный нагрев металла, Вт/м³ (γ-нагрев корпуса, электронагреватели). */
    @Override
    public void setVolumetricPower(double wattsPerM3) {
        for (int i = 0; i < n; i++) source[i] = wattsPerM3 * volume[i];
    }

    /** Площадь стороны, м². */
    public double area(int side) {
        return area[side];
    }
}
