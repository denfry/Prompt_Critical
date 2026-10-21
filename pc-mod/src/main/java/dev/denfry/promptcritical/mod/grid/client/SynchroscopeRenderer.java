package dev.denfry.promptcritical.mod.grid.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.denfry.promptcritical.mod.boiler.client.BoilerClient;
import dev.denfry.promptcritical.mod.grid.InstrumentBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Стрелка синхроноскопа на циферблате модели {@code instrument_synchroscope} (центр x 8, y 8 px, плоскость z 10,9):
 * вверх — фазы совпали (момент включения), по часовой — подключаемая машина быстрее сети. Без напряжения с обеих
 * сторон стрелки нет.
 */
final class SynchroscopeRenderer implements BlockEntityRenderer<InstrumentBlockEntity> {
    private static final float CX = 8, CY = 8, Z = 10.85f, LEN = 4f, HALF_WIDTH = 0.3f;

    @Override
    public void render(InstrumentBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
        VertexConsumer vc = buffers.getBuffer(RenderType.debugQuads());
        ms.pushPose();
        BoilerClient.toModel(ms, be.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING));
        // риска «синхронно» сверху
        BoilerClient.quad(vc, ms, new float[][] {{CX - 0.3f, CY + LEN - 0.2f}, {CX + 0.3f, CY + LEN - 0.2f},
                {CX + 0.3f, CY + LEN + 0.4f}, {CX - 0.3f, CY + LEN + 0.4f}}, Z + 0.02f, 0xFFC02020);
        float a = be.angle();
        if (!Float.isNaN(a)) {
            // смотрящий с севера видит +x слева: по часовой — к −x
            float dx = (float) -Math.sin(a), dy = (float) Math.cos(a);
            float nx = -dy * HALF_WIDTH, ny = dx * HALF_WIDTH;
            float x0 = CX - dx, y0 = CY - dy, x1 = CX + dx * LEN, y1 = CY + dy * LEN;
            BoilerClient.quad(vc, ms, new float[][] {{x0 - nx, y0 - ny}, {x1 - nx, y1 - ny}, {x1 + nx, y1 + ny}, {x0 + nx, y0 + ny}},
                    Z, 0xFF101010);
        }
        ms.popPose();
    }
}
