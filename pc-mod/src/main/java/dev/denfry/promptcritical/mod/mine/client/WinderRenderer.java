package dev.denfry.promptcritical.mod.mine.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.denfry.promptcritical.mod.client.whole.WholeModelRenderer;
import dev.denfry.promptcritical.mod.mine.HoistGeometry;
import dev.denfry.promptcritical.mod.mine.MineHoistBlockEntity;
import dev.denfry.promptcritical.sim.mining.HoistUnit;
import net.minecraft.core.Direction;

/**
 * Паровая подъёмная машина реального масштаба (winding_engine): цельная модель станин и машин, поверх — барабан с
 * валом и кривошипами (вторая цельная модель), ползуны, шатуны и курсор указателя глубины. Кинематика — по таблице
 * размеров (docs/research/winding_engine-dimensions.md), px модели: ось вала (8, 40, 8), кривошип 9,75, шатун 40;
 * палец западной машины θ = −α (α — угол барабана, 0 — палец к цилиндрам, +z), восточной — θ + 90°;
 * палец (y, z) = (40 + 9,75 sin θ, 8 + 9,75 cos θ), ползун z = 8 + 9,75 cos θ + √(40² − (9,75 sin θ)²).
 */
final class WinderRenderer extends WholeModelRenderer<MineHoistBlockEntity> {
    /** Ось вала барабана, px модели; радиус навитого каната, блоков (⌀15 футов, R 36,5 px). */
    static final float AXIS_Y = 40;
    static final double DRUM_R = 36.5 / 16.0;
    private static final float CRANK_R = 9.75f, ROD_L = 40;
    /** Оси машин по x, блоков от оси контроллера: запад −22 px, восток 38 px. */
    private static final double WEST_X = -1.875, EAST_X = 1.875;
    /** Курсор указателя глубины: верхнее положение и ход от устья до дна ствола, блоков. */
    private static final double CURSOR_X = 2.869, CURSOR_Y = 2.375, CURSOR_Z = 1.375, CURSOR_TRAVEL = 1.875;

    @Override
    protected void parts(MineHoistBlockEntity be, float pt, PoseStack ms, VertexConsumer vc, int light) {
        Direction f = be.facing();
        // угол барабана реального радиуса: та же длина каната, что у двойника с барабаном R 1,25 м
        float a = (float) (be.drumAngle(pt) * HoistUnit.DRUM_RADIUS_M / DRUM_R);
        wholeAboutX("winding_engine_drum", be, f, 0.5, AXIS_Y / 16, 0.5, a, ms, vc, light);
        engine(be, f, WEST_X, -a, ms, vc, light);
        engine(be, f, EAST_X, -a + (float) (Math.PI / 2), ms, vc, light);

        HoistGeometry.Shaft shaft = be.shaft();
        double fill = shaft == null || shaft.depth() <= 0 ? 0 : Math.max(0, Math.min(1, be.renderDepth(pt) / shaft.depth()));
        part(MineClient.WINDER_CURSOR, be, f, CURSOR_X, CURSOR_Y - fill * CURSOR_TRAVEL, CURSOR_Z).light(light).renderInto(ms, vc);
    }

    /** Ползун и шатун одной машины при угле кривошипа θ. */
    private static void engine(MineHoistBlockEntity be, Direction f, double dx, float theta, PoseStack ms, VertexConsumer vc, int light) {
        float s = (float) Math.sin(theta), c = (float) Math.cos(theta);
        float crossZ = 8 + CRANK_R * c + (float) Math.sqrt(Math.max(0, ROD_L * ROD_L - CRANK_R * CRANK_R * s * s));
        // ползун: палец partial (8, 8, 8) на оси машины на высоте вала
        part(MineClient.WINDER_CROSSHEAD, be, f, dx, AXIS_Y / 16 - 0.5, (crossZ - 8) / 16).light(light).renderInto(ms, vc);
        // шатун: малая головка (8, 8, 28) partial на пальце ползуна, большая — на 40 px к −z, на пальце кривошипа
        float phi = (float) Math.asin(Math.max(-1, Math.min(1, CRANK_R * s / ROD_L)));
        part(MineClient.WINDER_CONROD, be, f, dx, AXIS_Y / 16 - 0.5, (crossZ - 28) / 16)
                .translate(0.5, 0.5, 28 / 16.0).rotateX(phi).translate(-0.5, -0.5, -28 / 16.0).light(light).renderInto(ms, vc);
    }
}
