package dev.denfry.promptcritical.mod.foundry.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.denfry.promptcritical.mod.foundry.PortKineticMachineBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.phys.AABB;

/** Один BE на станок старой серии и реального масштаба: рисует цельную модель, если она есть, иначе старый рендер. */
final class RealOrOldRenderer<T extends PortKineticMachineBlockEntity<?>> implements BlockEntityRenderer<T> {
    private final BlockEntityRenderer<T> old;
    private final ShopToolRealRenderer real;

    RealOrOldRenderer(BlockEntityRenderer<T> old, ShopToolRealRenderer real) {
        this.old = old;
        this.real = real;
    }

    @Override
    public void render(T be, float pt, PoseStack ms, MultiBufferSource buf, int light, int overlay) {
        if (be.real()) real.render(be, pt, ms, buf, light, overlay);
        else old.render(be, pt, ms, buf, light, overlay);
    }

    @Override
    public boolean shouldRenderOffScreen(T be) {
        return be.real() || old.shouldRenderOffScreen(be);
    }

    @Override
    public int getViewDistance() {
        return real.getViewDistance();
    }

    @Override
    public AABB getRenderBoundingBox(T be) {
        return be.real() ? real.getRenderBoundingBox(be) : old.getRenderBoundingBox(be);
    }
}
