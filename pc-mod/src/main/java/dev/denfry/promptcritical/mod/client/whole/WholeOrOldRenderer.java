package dev.denfry.promptcritical.mod.client.whole;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

/**
 * Один тип BE на старый блок и блок реального масштаба: у блока с цельной моделью рисует {@code real}, у старого —
 * его прежний рендер {@code old}.
 */
public final class WholeOrOldRenderer<T extends BlockEntity & WholeModelHost> implements BlockEntityRenderer<T> {
    private final WholeModelRenderer<T> real;
    private final BlockEntityRenderer<T> old;

    public WholeOrOldRenderer(WholeModelRenderer<T> real, BlockEntityRenderer<T> old) {
        this.real = real;
        this.old = old;
    }

    private BlockEntityRenderer<T> of(T be) {
        return be.wholeModelId() != null ? real : old;
    }

    @Override
    public void render(T be, float pt, PoseStack ms, MultiBufferSource buf, int light, int overlay) {
        of(be).render(be, pt, ms, buf, light, overlay);
    }

    @Override
    public boolean shouldRenderOffScreen(T be) {
        return of(be).shouldRenderOffScreen(be);
    }

    @Override
    public int getViewDistance() {
        return WholeModelRenderer.VIEW_DISTANCE;
    }

    @Override
    public AABB getRenderBoundingBox(T be) {
        return of(be).getRenderBoundingBox(be);
    }
}
