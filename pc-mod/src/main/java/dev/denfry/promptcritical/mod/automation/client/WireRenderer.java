package dev.denfry.promptcritical.mod.automation.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.denfry.promptcritical.mod.automation.WiredDevice;
import dev.denfry.promptcritical.mod.automation.WiringToolItem;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.automation.TerminalType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * Провода {@code wiring_tool} и клеммы блока (03c §3c.2): провод — лента ~0,7 px с небольшим провесом, рисует конец
 * с меньшей позицией; цвет — по типу цепи (24 В DC синий, ±10 В белый, 4–20 мА оранжевый). С инструментом в руке —
 * квадраты клемм и подпись клеммы под прицелом. Может оборачивать собственный рендер блока ({@code inner}).
 *
 * @param <T> BE с клеммами
 */
public final class WireRenderer<T extends BlockEntity> implements BlockEntityRenderer<T> {
    private static final float HALF = 0.35f / 16, MARK = 0.7f / 16;
    private static final int DISCRETE = 0x2F55C8, VOLTAGE = 0xE6E6E6, CURRENT = 0xE07A20;

    @Nullable
    private final BlockEntityRenderer<T> inner;

    public WireRenderer(@Nullable BlockEntityRenderer<T> inner) {
        this.inner = inner;
    }

    @Override
    public void render(T be, float partialTicks, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
        if (inner != null) inner.render(be, partialTicks, ms, buffers, light, overlay);
        if (!(be instanceof WiredDevice d) || be.getLevel() == null) return;
        Level level = be.getLevel();
        Vec3 origin = Vec3.atLowerCornerOf(be.getBlockPos());
        VertexConsumer vc = buffers.getBuffer(RenderType.debugQuads());
        float shade = shade(level, light);
        for (WiredDevice.Wire w : d.wires()) {
            WiredDevice o = w.other().equals(be.getBlockPos()) ? d
                    : level.isLoaded(w.other()) && level.getBlockEntity(w.other()) instanceof WiredDevice x ? x : null;
            if (o != null && !owns(be.getBlockPos(), w)) continue;
            if (w.from() >= d.wiredKind().terminalCount()) continue;
            Vec3 a = d.terminalPoint(w.from()).subtract(origin);
            Vec3 b = (o != null && w.to() < o.wiredKind().terminalCount() ? o.terminalPoint(w.to()) : Vec3.atCenterOf(w.other()))
                    .subtract(origin);
            int rgb = color(d.wiredKind().terminal(w.from()).type());
            wire(vc, ms, a, b, argb(rgb, shade));
        }
        if (holdingTool()) terminals(be, d, origin, ms, buffers, vc, light);
    }

    /** Провод рисует конец с меньшей позицией (у проводов внутри блока — с меньшей клеммой). */
    private static boolean owns(BlockPos pos, WiredDevice.Wire w) {
        int c = pos.compareTo(w.other());
        return c < 0 || c == 0 && w.from() < w.to();
    }

    private static boolean holdingTool() {
        var p = Minecraft.getInstance().player;
        return p != null && (p.getMainHandItem().is(PcItems.WIRING_TOOL.get()) || p.getOffhandItem().is(PcItems.WIRING_TOOL.get()));
    }

    private static int color(TerminalType t) {
        return switch (t) {
            case DISCRETE -> DISCRETE;
            case ANALOG_VOLTAGE -> VOLTAGE;
            case ANALOG_CURRENT -> CURRENT;
        };
    }

    /** Отрезки с провесом 5 см + 2 % длины (провод в лотке и между щитами). */
    private static void wire(VertexConsumer vc, PoseStack ms, Vec3 a, Vec3 b, int argb) {
        double len = a.distanceTo(b);
        double sag = Math.min(1.0, 0.05 + 0.02 * len);
        int n = Math.max(4, (int) Math.ceil(len * 2));
        Vec3 prev = a;
        for (int i = 1; i <= n; i++) {
            double t = (double) i / n;
            Vec3 p = a.lerp(b, t).subtract(0, 4 * sag * t * (1 - t), 0);
            ribbon(vc, ms, prev, p, argb);
            prev = p;
        }
    }

    private static void ribbon(VertexConsumer vc, PoseStack ms, Vec3 p0, Vec3 p1, int argb) {
        Vec3 d = p1.subtract(p0);
        Vec3 side = new Vec3(-d.z, 0, d.x);
        side = side.lengthSqr() < 1e-8 ? new Vec3(HALF, 0, 0) : side.normalize().scale(HALF);
        quad(vc, ms, p0, p1, side, argb);
        quad(vc, ms, p0, p1, new Vec3(0, HALF, 0), argb);
    }

    private static void quad(VertexConsumer vc, PoseStack ms, Vec3 p0, Vec3 p1, Vec3 w, int argb) {
        Matrix4f m = ms.last().pose();
        Vec3[] v = {p0.subtract(w), p1.subtract(w), p1.add(w), p0.add(w)};
        for (Vec3 p : v) vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(argb);
        for (int i = 3; i >= 0; i--) vc.addVertex(m, (float) v[i].x, (float) v[i].y, (float) v[i].z).setColor(argb);
    }

    /** Клеммы блока и подпись той, что под прицелом. */
    private static void terminals(BlockEntity be, WiredDevice d, Vec3 origin, PoseStack ms, MultiBufferSource buffers,
            VertexConsumer vc, int light) {
        Minecraft mc = Minecraft.getInstance();
        int hovered = -1;
        if (mc.hitResult instanceof BlockHitResult hit && be.getLevel() != null
                && WiringToolItem.deviceAt(be.getLevel(), hit.getBlockPos()) == d)
            hovered = d.terminalAt(hit.getLocation(), WiringToolItem.PICK_RADIUS);
        for (int t = 0; t < d.wiredKind().terminalCount(); t++) {
            Vec3 p = d.terminalPoint(t).subtract(origin);
            Direction n = d.terminalNormal(t);
            float size = t == hovered ? MARK * 1.8f : MARK;
            int rgb = t == hovered ? 0xFFFF40 : color(d.wiredKind().terminal(t).type());
            square(vc, ms, p.add(Vec3.atLowerCornerOf(n.getNormal()).scale(0.004)), n, size, 0xFF000000 | rgb);
        }
        if (hovered < 0) return;
        Vec3 p = d.terminalPoint(hovered).subtract(origin).add(Vec3.atLowerCornerOf(d.terminalNormal(hovered).getNormal()).scale(0.12))
                .add(0, 0.12, 0);
        Component label = WiringToolItem.describe(d.wiredKind(), hovered);
        ms.pushPose();
        ms.translate(p.x, p.y, p.z);
        ms.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        ms.scale(0.012f, -0.012f, 0.012f);
        Font font = mc.font;
        float x = -font.width(label) / 2f;
        font.drawInBatch(label, x, 0, 0xFFFFFFFF, false, ms.last().pose(), buffers, Font.DisplayMode.SEE_THROUGH, 0x60000000,
                LightTexture.FULL_BRIGHT);
        ms.popPose();
    }

    /** Квадрат клеммы в плоскости колодки. */
    private static void square(VertexConsumer vc, PoseStack ms, Vec3 c, Direction n, float h, int argb) {
        Vec3 u, v;
        if (n.getAxis() == Direction.Axis.Y) {
            u = new Vec3(h, 0, 0);
            v = new Vec3(0, 0, h);
        } else {
            u = new Vec3(n.getStepZ() * h, 0, -n.getStepX() * h);
            v = new Vec3(0, h, 0);
        }
        Matrix4f m = ms.last().pose();
        Vec3[] pts = {c.subtract(u).subtract(v), c.add(u).subtract(v), c.add(u).add(v), c.subtract(u).add(v)};
        for (Vec3 p : pts) vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(argb);
        for (int i = 3; i >= 0; i--) vc.addVertex(m, (float) pts[i].x, (float) pts[i].y, (float) pts[i].z).setColor(argb);
    }

    private static int argb(int rgb, float shade) {
        return 0xFF000000 | (int) ((rgb >> 16 & 255) * shade) << 16 | (int) ((rgb >> 8 & 255) * shade) << 8 | (int) ((rgb & 255) * shade);
    }

    private static float shade(Level level, int light) {
        float sky = LightTexture.sky(light) / 15f * (1 - level.getSkyDarken() / 15f), block = LightTexture.block(light) / 15f;
        return 0.3f + 0.7f * Math.max(sky, block);
    }

    @Override
    public AABB getRenderBoundingBox(T be) {
        AABB box = new AABB(be.getBlockPos()).inflate(1);
        if (be instanceof WiredDevice d) for (WiredDevice.Wire w : d.wires()) box = box.minmax(new AABB(w.other()).inflate(0, 1.5, 0));
        return box;
    }

    @Override
    public boolean shouldRenderOffScreen(T be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
