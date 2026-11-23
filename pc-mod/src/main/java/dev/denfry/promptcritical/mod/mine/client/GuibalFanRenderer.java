package dev.denfry.promptcritical.mod.mine.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.client.whole.WholeModelRenderer;
import dev.denfry.promptcritical.mod.mine.MainMineFanBlockEntity;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.core.Direction;

/**
 * Вентилятор Guibal реального масштаба: цельная модель и паровая машина на кривошипе оси (docs/research/guibal_fan-dimensions.md).
 * Ось вдоль z через (8, 40) px модели, кривошип в плоскости z = 50, палец на радиусе 9,75 к −x при угле 0. Машина
 * лежит вдоль x к западу: ползун и шатун — partial подъёмной машины, повёрнутые на −90° вокруг y (их +z к −x модели).
 * Ползун x = 8 − r cos α − √(L² − (r sin α)²), шатун качается вокруг z на asin(r sin α / L). Ротор скрыт в кожухе.
 */
final class GuibalFanRenderer extends WholeModelRenderer<MainMineFanBlockEntity> {
    static final PartialModel CRANK = PartialModel.of(PromptCritical.id("block/guibal_fan/partial/crank")),
            CROSSHEAD = PartialModel.of(PromptCritical.id("block/winding_engine/partial/crosshead")),
            CONROD = PartialModel.of(PromptCritical.id("block/winding_engine/partial/conrod"));
    /** Ось, плоскость кривошипа, радиус кривошипа, длина шатуна и палец малой головки в partial шатуна, px. */
    private static final float AXIS_X = 8, AXIS_Y = 40, PLANE_Z = 50, CRANK_R = 9.75f, ROD_L = 40, ROD_PIN_Z = 28;

    /** Загрузить класс до запекания моделей: partial-модели регистрируются в статике. */
    static void init() {
    }

    @Override
    protected void parts(MainMineFanBlockEntity be, float pt, PoseStack ms, VertexConsumer vc, int light) {
        Direction f = be.wholeModelFacing();
        float a = be.renderCrank(pt);
        // кривошип: поворот вокруг z через ось partial (8, 8, 8) = ось модели (8, 40, 50)
        part(CRANK, be, f, 0, 2, 2.625).translate(0.5, 0.5, 0.5).rotateZDegrees((float) Math.toDegrees(a)).translate(-0.5, -0.5, -0.5)
                .light(light).renderInto(ms, vc);
        float s = CRANK_R * (float) Math.sin(a);
        float xc = AXIS_X - CRANK_R * (float) Math.cos(a) - (float) Math.sqrt(Math.max(0, ROD_L * ROD_L - s * s));
        // ползун: его ось (8, 8, 8) — на пальце ползуна
        part(CROSSHEAD, be, f, xc / 16, AXIS_Y / 16, PLANE_Z / 16).rotateYDegrees(-90).translate(-0.5, -0.5, -0.5)
                .light(light).renderInto(ms, vc);
        // шатун: малая головка (8, 8, 28) на пальце ползуна, большая уходит к пальцу кривошипа ниже или выше оси
        float phi = (float) Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, s / ROD_L))));
        part(CONROD, be, f, xc / 16, AXIS_Y / 16, PLANE_Z / 16).rotateZDegrees(-phi).rotateYDegrees(-90)
                .translate(-0.5, -0.5, -ROD_PIN_Z / 16).light(light).renderInto(ms, vc);
    }
}
