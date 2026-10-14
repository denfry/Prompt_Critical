package dev.denfry.promptcritical.sim.thermal;

/** Материал стенки с постоянными свойствами (трубки ПГ, трубопроводы, корпус). Единицы — СИ. */
public record WallMaterial(double conductivity, double density, double heatCapacity) {
    /** Сталь 08Х18Н10Т при ~300 °C (01 §1.4.7). */
    public static final WallMaterial STAINLESS_STEEL = new WallMaterial(18.0, 7900, 500);

    public WallMaterial {
        if (!(conductivity > 0 && density > 0 && heatCapacity > 0)) throw new IllegalArgumentException("properties");
    }
}
