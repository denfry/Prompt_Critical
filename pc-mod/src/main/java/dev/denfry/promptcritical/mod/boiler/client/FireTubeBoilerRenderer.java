package dev.denfry.promptcritical.mod.boiler.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.denfry.promptcritical.mod.boiler.FireTubeBoilerBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;

/**
 * Водоуказательные стёкла котла (арт 07 §7.23, элементы {@code glass_w}, {@code glass_e} модели): столбик воды по
 * уровню из {@link FireTubeBoilerBlockEntity#glassFraction()}; красная риска — нижний допустимый уровень.
 */
final class FireTubeBoilerRenderer implements BlockEntityRenderer<FireTubeBoilerBlockEntity> {
    /** Трубки стёкол модели (левые края по x), y 11…17 px на грани z = −16. */
    private static final float[] TUBES_X = {-2.0f, 17.0f};
    private static final float TUBE_W = 1.0f, Y0 = 11.0f, Y1 = 17.0f, Z = -16.06f;
    /** Риска нижнего допустимого уровня (потолок жаровой трубы) на шкале стекла. */
    private static final float CROWN_MARK = (float) ((0.45 - FireTubeBoilerBlockEntity.GLASS_BOTTOM_M) / FireTubeBoilerBlockEntity.GLASS_SPAN_M);

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
        ms.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(FireTubeBoilerBlockEntity be) {
        return true;
    }
}
