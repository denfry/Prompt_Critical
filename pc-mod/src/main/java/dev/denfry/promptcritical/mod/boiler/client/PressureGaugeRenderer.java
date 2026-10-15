package dev.denfry.promptcritical.mod.boiler.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.denfry.promptcritical.mod.boiler.PressureGaugeBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Стрелка манометра на циферблате модели {@code instrument_pressure} (центр x 8, y 8,5 px, плоскость z 10,9):
 * ноль — внизу слева, шкала 270° по часовой для смотрящего; красная риска — рабочее давление котла 1,2 МПа.
 */
final class PressureGaugeRenderer implements BlockEntityRenderer<PressureGaugeBlockEntity> {
    private static final float CX = 8, CY = 8.5f, Z = 10.85f, LEN = 4.3f, HALF_WIDTH = 0.3f;
    private static final float WORKING_MARK = (float) ((1.2e6 - 1.01325e5) / PressureGaugeBlockEntity.FULL_SCALE_PA);

    @Override
    public void render(PressureGaugeBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
        VertexConsumer vc = buffers.getBuffer(RenderType.debugQuads());
        ms.pushPose();
        BoilerClient.toModel(ms, be.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING));
        needle(vc, ms, WORKING_MARK, LEN + 0.6f, LEN - 0.4f, 0.25f, Z + 0.02f, 0xFFC02020);
        needle(vc, ms, be.needleFraction(partialTicks), LEN, -0.8f, HALF_WIDTH, Z, 0xFF101010);
        ms.popPose();
    }

    /** Лучевой отрезок от радиуса {@code from} до {@code to} под углом доли шкалы {@code frac}. */
    private static void needle(VertexConsumer vc, PoseStack ms, float frac, float to, float from, float hw, float z, int argb) {
        double a = Math.toRadians(-135 + 270 * frac);
        // смотрящий с севера видит +x слева: по часовой — к −x
        float dx = (float) -Math.sin(a), dy = (float) Math.cos(a);
        float nx = -dy * hw, ny = dx * hw;
        float x0 = CX + dx * from, y0 = CY + dy * from, x1 = CX + dx * to, y1 = CY + dy * to;
        BoilerClient.quad(vc, ms, new float[][] {{x0 - nx, y0 - ny}, {x1 - nx, y1 - ny}, {x1 + nx, y1 + ny}, {x0 + nx, y0 + ny}}, z, argb);
    }
}
