package dev.denfry.promptcritical.mod.boiler.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.boiler.FireTubeBoilerBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;

/**
 * Живые приборы на фронте котла (модель {@code art/blockbench/boilers/fire_tube_boiler.author.js}): столбик воды в двух
 * водоуказательных стёклах ({@code glass_0}, {@code glass_1}) по {@link FireTubeBoilerBlockEntity#glassFraction()} с
 * красной риской нижнего допустимого уровня и стрелка манометра по {@link FireTubeBoilerBlockEntity#gaugePressurePa()}.
 */
final class FireTubeBoilerRenderer implements BlockEntityRenderer<FireTubeBoilerBlockEntity> {
    /** Трубки стёкол модели (левые края по x), y 11…18 px, перед трубкой z = −14.9. */
    private static final float[] TUBES_X = {0.0f, 15.0f};
    private static final float TUBE_W = 1.0f, Y0 = 11.0f, Y1 = 18.0f, Z = -14.93f;
    /** Риска нижнего допустимого уровня (потолок жаровой трубы) на шкале стекла. */
    private static final float CROWN_MARK = (float) ((0.45 - FireTubeBoilerBlockEntity.GLASS_BOTTOM_M) / FireTubeBoilerBlockEntity.GLASS_SPAN_M);
    /** Манометр: центр циферблата, шкала 0…{@link FireTubeBoilerBlockEntity#GAUGE_SCALE_PA} на дуге 270° от 225°. */
    private static final float DIAL_X = 8.0f, DIAL_Y = 21.5f, DIAL_Z = -15.6f, NEEDLE = 2.1f, TAIL = 0.5f, HALF_W = 0.12f;

    @Override
    public void render(FireTubeBoilerBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
        VertexConsumer vc = buffers.getBuffer(RenderType.debugQuads());
        ms.pushPose();
        BoilerClient.toModel(ms, be.facing());
        float top = Y0 + (Y1 - Y0) * be.glassFraction();
        float mark = Y0 + (Y1 - Y0) * CROWN_MARK;
        for (float x0 : TUBES_X) {
            float x1 = x0 + TUBE_W;
            BoilerClient.rect(vc, ms, x0, Y0, x1, Y1, Z + 0.01f, 0xC0303844);
            if (be.glassFraction() > 0.005f) BoilerClient.rect(vc, ms, x0, Y0, x1, top, Z, 0xE03C78D8);
            BoilerClient.rect(vc, ms, x0 - 0.5f, mark - 0.15f, x1 + 0.5f, mark + 0.15f, Z - 0.01f, 0xFFD02020);
        }
        if (!be.getBlockState().getValue(PcProperties.EXPLODED)) needle(vc, ms, be.gaugePressurePa());
        ms.popPose();
    }

    /** Стрелка манометра. Фронт смотрит на север, поэтому для наблюдателя ось x модели идёт справа налево. */
    private static void needle(VertexConsumer vc, PoseStack ms, double gaugePa) {
        double frac = Math.max(0, Math.min(1.03, gaugePa / FireTubeBoilerBlockEntity.GAUGE_SCALE_PA));
        double a = Math.toRadians(225 - frac * 270);
        float dx = (float) -Math.cos(a), dy = (float) Math.sin(a), nx = -dy * HALF_W, ny = dx * HALF_W;
        float[][] pts = {
                {DIAL_X - dx * TAIL + nx, DIAL_Y - dy * TAIL + ny},
                {DIAL_X + dx * NEEDLE, DIAL_Y + dy * NEEDLE},
                {DIAL_X + dx * NEEDLE, DIAL_Y + dy * NEEDLE},
                {DIAL_X - dx * TAIL - nx, DIAL_Y - dy * TAIL - ny}};
        BoilerClient.quad(vc, ms, pts, DIAL_Z, 0xFF1A1A1A);
    }

    @Override
    public boolean shouldRenderOffScreen(FireTubeBoilerBlockEntity be) {
        return true;
    }
}
