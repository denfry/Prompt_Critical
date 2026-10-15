package dev.denfry.promptcritical.mod.boiler.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.denfry.promptcritical.mod.boiler.FireTubeBoilerBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;

/**
 * Водоуказательное стекло котла (арт 07 §7.23, элемент {@code gauge_glass} модели): столбик воды по уровню из
 * {@link FireTubeBoilerBlockEntity#glassFraction()}; пустое стекло — красная риска нижнего допустимого уровня.
 */
final class FireTubeBoilerRenderer implements BlockEntityRenderer<FireTubeBoilerBlockEntity> {
    /** Стекло модели: x −6…−4, y 10…18 px на грани z = −16. */
    private static final float X0 = -5.7f, X1 = -4.3f, Y0 = 10.3f, Y1 = 17.7f, Z = -16.06f;
    /** Риска нижнего допустимого уровня (потолок жаровой трубы) на шкале стекла. */
    private static final float CROWN_MARK = (float) ((0.45 - FireTubeBoilerBlockEntity.GLASS_BOTTOM_M) / FireTubeBoilerBlockEntity.GLASS_SPAN_M);

    @Override
    public void render(FireTubeBoilerBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
        VertexConsumer vc = buffers.getBuffer(RenderType.debugQuads());
        ms.pushPose();
        BoilerClient.toModel(ms, be.facing());
        float top = Y0 + (Y1 - Y0) * be.glassFraction();
        BoilerClient.rect(vc, ms, X0, Y0, X1, Y1, Z + 0.01f, 0xC0303844);
        if (be.glassFraction() > 0.005f) BoilerClient.rect(vc, ms, X0, Y0, X1, top, Z, 0xE03C78D8);
        float mark = Y0 + (Y1 - Y0) * CROWN_MARK;
        BoilerClient.rect(vc, ms, X0 - 0.5f, mark - 0.15f, X1 + 0.5f, mark + 0.15f, Z - 0.01f, 0xFFD02020);
        ms.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(FireTubeBoilerBlockEntity be) {
        return true;
    }
}
