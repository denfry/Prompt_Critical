package dev.denfry.promptcritical.mod.boiler.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.boiler.SteamEngineBlockEntity;
import dev.denfry.promptcritical.mod.client.whole.WholeModelRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.core.Direction;

/**
 * Паровая машина реального масштаба (mill_engine): цельная модель и механизм поверх неё. Кинематика — по таблице
 * размеров (docs/research/mill_engine-dimensions.md): угол кривошипа θ от внутренней мёртвой точки (палец к цилиндру),
 * палец (y, z) = (24 + 4,8 sin θ, 56 − 4,8 cos θ) px, ползун z = z_п − √(24² − (y_п − 24)²), шатун 24 px.
 */
final class MillEngineRenderer extends WholeModelRenderer<SteamEngineBlockEntity> {
    static final PartialModel FLYWHEEL = partial("flywheel"), CRANK = partial("crank"), CROSSHEAD = partial("crosshead"),
            ROD = partial("rod"), GOVERNOR = partial("governor");
    /** Ось коленвала, радиус кривошипа, шатун, покой пальца ползуна, px модели. */
    private static final float AXIS_Y = 24, AXIS_Z = 56, CRANK_R = 4.8f, ROD_L = 24, CROSSHEAD_REST_Z = 27.2f;
    /** Передача ремнём на регулятор от коленвала. */
    private static final float GOV_RATIO = 1.6f;

    private static PartialModel partial(String name) {
        return PartialModel.of(PromptCritical.id("block/mill_engine/partial/" + name));
    }

    /** Загрузить класс до запекания моделей: partial-модели регистрируются в статике. */
    static void init() {
    }

    @Override
    protected void parts(SteamEngineBlockEntity be, float pt, PoseStack ms, VertexConsumer vc, int light) {
        Direction f = be.facing();
        float theta = be.renderCrank(pt);
        float deg = (float) Math.toDegrees(theta);
        // маховик и кривошип: поворот вокруг x через ось partial (·, 8, 8) = вал модели (·, 24, 56)
        part(FLYWHEEL, be, f, 1, 1, 3).translate(0, 0.5, 0.5).rotateXDegrees(deg).translate(0, -0.5, -0.5).light(light).renderInto(ms, vc);
        part(CRANK, be, f, 0, 1, 3).translate(0, 0.5, 0.5).rotateXDegrees(deg).translate(0, -0.5, -0.5).light(light).renderInto(ms, vc);
        float pinY = AXIS_Y + CRANK_R * (float) Math.sin(theta), pinZ = AXIS_Z - CRANK_R * (float) Math.cos(theta);
        float dy = pinY - AXIS_Y;
        float crossZ = pinZ - (float) Math.sqrt(Math.max(0, ROD_L * ROD_L - dy * dy));
        float slide = (crossZ - CROSSHEAD_REST_Z) / 16;
        part(CROSSHEAD, be, f, 0, 1, 20 / 16.0 + slide).light(light).renderInto(ms, vc);
        // шатун: малая головка на пальце ползуна (partial (8, 8, 3.2)), большая — на пальце кривошипа выше/ниже оси
        float rodDeg = (float) -Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, dy / ROD_L))));
        part(ROD, be, f, 0, 1, 1.5 + slide).translate(0, 0.5, 0.2).rotateXDegrees(rodDeg).translate(0, -0.5, -0.2)
                .light(light).renderInto(ms, vc);
        part(GOVERNOR, be, f, 11 / 16.0, 2, 22 / 16.0).translate(0.5, 0, 0.5).rotateYDegrees(deg * GOV_RATIO).translate(-0.5, 0, -0.5)
                .light(light).renderInto(ms, vc);
    }
}
