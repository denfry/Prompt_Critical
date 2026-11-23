package dev.denfry.promptcritical.mod.site;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.denfry.promptcritical.mod.client.whole.WholeModelMath;
import dev.denfry.promptcritical.mod.client.whole.WholeModelRenderer;
import dev.denfry.promptcritical.mod.client.whole.WholeModels;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.phys.AABB;

/**
 * Призрак машины над стройплощадкой: цельная модель полупрозрачной; чем дальше стройка, тем она плотнее.
 */
public final class SiteGhostRenderer implements BlockEntityRenderer<ConstructionSiteBlockEntity> {
    @Override
    public void render(ConstructionSiteBlockEntity be, float pt, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
        WholeModels.Model m = model(be);
        if (m == null) return;
        SitePlans.Plan p = be.plan();
        float done = p == null ? 0 : (float) be.stage() / p.stages().size();
        int alpha = (int) (70 + 120 * done);
        VertexConsumer vc = buffers.getBuffer(RenderType.translucent());
        float yRot = -(be.facing().toYRot() + 180);
        for (WholeModels.Chunk c : m.chunks()) {
            SuperByteBuffer b = CachedBuffers.partial(c.partial(), be.getBlockState());
            b.center().rotateYDegrees(yRot).uncenter().translate(c.x(), c.y(), c.z())
                    .color(150, 200, 255, alpha).light(LightTexture.FULL_BRIGHT).renderInto(ms, vc);
        }
    }

    private static WholeModels.Model model(ConstructionSiteBlockEntity be) {
        SitePlans.Plan p = be.plan();
        if (p == null) return null;
        return WholeModels.get(BuiltInRegistries.BLOCK.getKey(p.unit().get()).getPath());
    }

    @Override
    public boolean shouldRenderOffScreen(ConstructionSiteBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return WholeModelRenderer.VIEW_DISTANCE;
    }

    @Override
    public AABB getRenderBoundingBox(ConstructionSiteBlockEntity be) {
        WholeModels.Model m = model(be);
        if (m == null) return new AABB(be.getBlockPos());
        Direction f = be.facing();
        double[] a = WholeModelMath.rotate(m.min(), f), b = WholeModelMath.rotate(m.max(), f);
        var q = be.getBlockPos();
        return new AABB(q.getX() + Math.min(a[0], b[0]), q.getY() + Math.min(a[1], b[1]), q.getZ() + Math.min(a[2], b[2]),
                q.getX() + Math.max(a[0], b[0]), q.getY() + Math.max(a[1], b[1]), q.getZ() + Math.max(a[2], b[2])).inflate(1);
    }
}
