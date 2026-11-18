package dev.denfry.promptcritical.mod.grid.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.grid.GridDeviceBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * Запрещающий плакат «Не включать! Работают люди» на лицевой стороне аппарата, пока на нём открыт наряд-допуск
 * (ПОТ ЭЭ, приложение «Плакаты и знаки безопасности»: белый фон, красная кайма, чёрные буквы). Плейсхолдер-декаль
 * {@code textures/block/permit_poster.png} размером 6×4 пикселя модели; угол — {@code x0, y0} в пикселях модели фасадом
 * на север. Оборачивает собственный рендер блока ({@code inner}).
 *
 * @param <T> аппарат
 */
public final class PosterRenderer<T extends GridDeviceBlockEntity<?>> implements BlockEntityRenderer<T> {
    private static final ResourceLocation TEXTURE = PromptCritical.id("textures/block/permit_poster.png");
    private static final float W = 6 / 16f, H = 4 / 16f, OUT = -0.03f / 16;

    @Nullable
    private final BlockEntityRenderer<T> inner;
    private final float x0, y0;

    public PosterRenderer(@Nullable BlockEntityRenderer<T> inner, float x0, float y0) {
        this.inner = inner;
        this.x0 = x0 / 16;
        this.y0 = y0 / 16;
    }

    @Override
    public void render(T be, float partialTicks, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
        if (inner != null) inner.render(be, partialTicks, ms, buffers, light, overlay);
        if (!be.posterHung()) return;
        BlockState state = be.getBlockState();
        Direction facing = state.getBlock() instanceof PcBlock pc ? pc.facing(state) : Direction.NORTH;
        ms.pushPose();
        ms.translate(0.5, 0, 0.5);
        ms.mulPose(Axis.YP.rotationDegrees(-(facing.toYRot() + 180)));
        ms.translate(-0.5, 0, -0.5);
        Matrix4f m = ms.last().pose();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        // лицевая грань модели — z = 0 (север), нормаль наружу −z
        quad(vc, ms, m, x0 + W, y0, 1, 1, light);
        quad(vc, ms, m, x0, y0, 0, 1, light);
        quad(vc, ms, m, x0, y0 + H, 0, 0, light);
        quad(vc, ms, m, x0 + W, y0 + H, 1, 0, light);
        ms.popPose();
    }

    private static void quad(VertexConsumer vc, PoseStack ms, Matrix4f m, float x, float y, float u, float v, int light) {
        vc.addVertex(m, x, y, OUT).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(ms.last(), 0, 0, -1);
    }

    @Override
    public AABB getRenderBoundingBox(T be) {
        return new AABB(be.getBlockPos()).inflate(2);
    }
}
