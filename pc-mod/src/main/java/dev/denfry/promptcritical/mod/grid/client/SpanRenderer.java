package dev.denfry.promptcritical.mod.grid.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.denfry.promptcritical.mod.grid.ConnectorBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Провода пролётов ВЛ: парабола со стрелой провеса из BE (по температуре провода), крестовая лента ~1,5 px. Пролёт
 * рисует конец с меньшей позицией. Цвет — изолированный НН чёрный, голый медный — медь; темнее ночью и в тени.
 */
final class SpanRenderer implements BlockEntityRenderer<ConnectorBlockEntity> {
    private static final float HALF = 0.75f / 16;
    private static final int INSULATED = 0x1C1C1C, BARE = 0xB06A3C;

    @Override
    public void render(ConnectorBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
        if (be.spans().isEmpty()) return;
        VertexConsumer vc = buffers.getBuffer(RenderType.debugQuads());
        float shade = shade(be, light);
        Vec3 origin = Vec3.atLowerCornerOf(be.getBlockPos());
        Vec3 a = be.attachPoint().subtract(origin);
        for (ConnectorBlockEntity.Span s : be.spans()) {
            if (s.other().compareTo(be.getBlockPos()) < 0) continue;
            Vec3 b = ConnectorBlockEntity.attachPoint(s.other(), be.mediumVoltage()).subtract(origin);
            int rgb = s.insulated() ? INSULATED : BARE;
            int argb = 0xFF000000 | (int) ((rgb >> 16 & 255) * shade) << 16 | (int) ((rgb >> 8 & 255) * shade) << 8
                    | (int) ((rgb & 255) * shade);
            int n = Math.max(8, (int) Math.ceil(a.distanceTo(b)));
            Vec3 prev = point(a, b, s.sagM(), 0);
            for (int i = 1; i <= n; i++) {
                Vec3 p = point(a, b, s.sagM(), (double) i / n);
                ribbon(vc, ms, prev, p, argb);
                prev = p;
            }
        }
    }

    /** Точка провода: прямая между подвесами минус парабола стрелы {@code sag} (наибольшая в середине). */
    private static Vec3 point(Vec3 a, Vec3 b, float sag, double t) {
        return a.lerp(b, t).subtract(0, 4 * sag * t * (1 - t), 0);
    }

    /** Отрезок двумя лентами — горизонтальной и вертикальной, видимыми с обеих сторон. */
    private static void ribbon(VertexConsumer vc, PoseStack ms, Vec3 p0, Vec3 p1, int argb) {
        Vec3 d = p1.subtract(p0);
        Vec3 side = new Vec3(-d.z, 0, d.x);
        side = side.lengthSqr() < 1e-8 ? new Vec3(HALF, 0, 0) : side.normalize().scale(HALF);
        Vec3 up = new Vec3(0, HALF, 0);
        quad(vc, ms, p0, p1, side, argb);
        quad(vc, ms, p0, p1, up, argb);
    }

    private static void quad(VertexConsumer vc, PoseStack ms, Vec3 p0, Vec3 p1, Vec3 w, int argb) {
        Matrix4f m = ms.last().pose();
        Vec3[] v = {p0.subtract(w), p1.subtract(w), p1.add(w), p0.add(w)};
        for (Vec3 p : v) vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(argb);
        for (int i = 3; i >= 0; i--) vc.addVertex(m, (float) v[i].x, (float) v[i].y, (float) v[i].z).setColor(argb);
    }

    /** Яркость по свету блока: небо с поправкой на время суток, факелы — как есть. */
    private static float shade(ConnectorBlockEntity be, int light) {
        float sky = LightTexture.sky(light) / 15f, block = LightTexture.block(light) / 15f;
        if (be.getLevel() != null) sky *= 1 - be.getLevel().getSkyDarken() / 15f;
        return 0.25f + 0.75f * Math.max(sky, block);
    }

    @Override
    public AABB getRenderBoundingBox(ConnectorBlockEntity be) {
        return be.spanBounds();
    }

    @Override
    public boolean shouldRenderOffScreen(ConnectorBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
