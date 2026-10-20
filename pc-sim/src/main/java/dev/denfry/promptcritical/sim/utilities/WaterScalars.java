package dev.denfry.promptcritical.sim.utilities;

import dev.denfry.promptcritical.sim.hydraulics.FluidNetwork;
import dev.denfry.promptcritical.sim.hydraulics.FluidNode;
import dev.denfry.promptcritical.sim.hydraulics.PassiveScalar;

/**
 * Примеси воды как пассивные скаляры гидросети (01 §1.12.3, 07 §7.9.3): жёсткость (в кг CaCO₃), O₂, Cl⁻, SiO₂,
 * Na⁺ — кг в узле, у граничного узла — массовая доля, как бор. Скаляры уходят только с жидкостью, поэтому в
 * испарительном узле (барабан, котёл) соли упариваются. pH, щёлочность, взвесь скалярами не переносятся — их берут
 * из воды, которой узел питается.
 */
public final class WaterScalars {
    public static final PassiveScalar HARDNESS = new PassiveScalar("hardness_caco3", 0);
    public static final PassiveScalar CHLORIDE = new PassiveScalar("chloride", 0);
    public static final PassiveScalar SILICA = new PassiveScalar("silica", 0);
    public static final PassiveScalar SODIUM = new PassiveScalar("sodium", 0);

    private WaterScalars() {}

    /** Регистрирует скаляры качества воды в сети (кислород — общий {@link PassiveScalar#DISSOLVED_O2}). */
    public static void register(FluidNetwork net) {
        net.registerScalar(HARDNESS);
        net.registerScalar(PassiveScalar.DISSOLVED_O2);
        net.registerScalar(CHLORIDE);
        net.registerScalar(SILICA);
        net.registerScalar(SODIUM);
    }

    /** Масса жидкости узла, кг (у граничного узла — 1: скаляр в нём — доля). */
    private static double liquidKg(FluidNode node, boolean boundary) {
        if (boundary) return 1;
        return node.mass() * (1 - Math.min(Math.max(node.quality(), 0), 1));
    }

    /** Заполняет узел водой качества {@code q} по текущей массе жидкости. */
    public static void charge(FluidNode node, boolean boundary, WaterQuality q) {
        double m = liquidKg(node, boundary);
        node.setScalar(HARDNESS, m * q.hardnessMolEqM3() * WaterQuality.CACO3_KG_PER_EQ / WaterQuality.WATER_DENSITY);
        node.setScalar(PassiveScalar.DISSOLVED_O2, m * q.o2KgKg());
        node.setScalar(CHLORIDE, m * q.chlorideKgKg());
        node.setScalar(SILICA, m * q.silicaKgKg());
        node.setScalar(SODIUM, m * q.sodiumKgKg());
    }

    /**
     * Вода узла: примеси — из скаляров, pH/щёлочность/взвесь — из {@code feed}; проводимость пересчитана по
     * составу. Пустой узел возвращает {@code feed}.
     */
    public static WaterQuality read(FluidNode node, boolean boundary, WaterQuality feed) {
        double m = liquidKg(node, boundary);
        if (!(m > 0)) return feed;
        double hardness = node.scalar(HARDNESS) / m * WaterQuality.WATER_DENSITY / WaterQuality.CACO3_KG_PER_EQ;
        double na = node.scalar(SODIUM) / m;
        return new WaterQuality(hardness, node.scalar(PassiveScalar.DISSOLVED_O2) / m, node.scalar(CHLORIDE) / m,
                node.scalar(SILICA) / m, na, WaterQuality.estimateConductivity(hardness, na, 0), feed.ph(),
                feed.suspendedKgM3(), feed.alkalinityMolEqM3());
    }
}
