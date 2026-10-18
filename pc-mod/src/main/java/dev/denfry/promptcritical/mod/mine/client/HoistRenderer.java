package dev.denfry.promptcritical.mod.mine.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.boiler.BoilerGeometry;
import dev.denfry.promptcritical.mod.mine.HoistGeometry;
import dev.denfry.promptcritical.mod.mine.MineHoistBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Подъём на клиенте (арт 08 §8.21): барабан машины вокруг оси X модели (pivot [8, 8, 8]), шкив на оголовке копра
 * вокруг оси X его модели (pivot [8, 16, 8]) и канат — от барабана к шкиву и от шкива вниз к крыше клети.
 */
final class HoistRenderer implements BlockEntityRenderer<MineHoistBlockEntity> {
    /** Видимые радиусы барабана и шкива по моделям, блоков. */
    private static final double DRUM_R = 13 / 16.0, SHEAVE_R = 13 / 16.0, ROPE_W = 0.07;

    @Override
    public void render(MineHoistBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (!state.is(PcBlocks.MINE_HOIST.get())) return;
        Direction facing = be.facing();
        VertexConsumer vc = buffers.getBuffer(RenderType.cutoutMipped());
        float drum = be.drumAngle(partialTicks);
        SuperByteBuffer d = CachedBuffers.partial(MineClient.HOIST_DRUM, state);
        d.center().rotateYDegrees(-(facing.toYRot() + 180)).uncenter()
                .translate(0, 0.5f, 0.5f).rotateX(drum).translate(0, -0.5f, -0.5f)
                .light(light).renderInto(ms, vc);

        BlockPos sheave = be.sheave();
        if (be.getLevel() == null || sheave == null) return;
        BlockPos origin = be.getBlockPos();
        BlockState hs = be.getLevel().getBlockState(sheave);
        Direction hf = hs.is(PcBlocks.HEADFRAME.get()) ? PcBlocks.HEADFRAME.get().facing(hs) : Direction.NORTH;
        float sheaveAngle = (float) (drum * 1.25 / 1.5);
        int sheaveLight = LevelRenderer.getLightColor(be.getLevel(), sheave.above());
        ms.pushPose();
        ms.translate(sheave.getX() - origin.getX(), sheave.getY() - origin.getY(), sheave.getZ() - origin.getZ());
        SuperByteBuffer s = CachedBuffers.partial(MineClient.SHEAVE, hs);
        s.center().rotateYDegrees(-(hf.toYRot() + 180)).uncenter()
                .translate(0, 1f, 0.5f).rotateX(sheaveAngle).translate(0, -1f, -0.5f)
                .light(sheaveLight).renderInto(ms, vc);
        ms.popPose();

        // канат: точки в координатах блока машины
        TextureAtlasSprite rope = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(PromptCritical.id("block/common/plant_rope"));
        Vec3 o = Vec3.atLowerCornerOf(origin);
        Vec3 drumC = BoilerGeometry.world(origin, facing, 8, 8, 8).subtract(o);
        Vec3 sheaveC = new Vec3(sheave.getX() + 0.5, sheave.getY() + 1.0, sheave.getZ() + 0.5).subtract(o);
        Vec3 toDrum = horizontal(drumC.subtract(sheaveC));
        draw(vc, ms, drumC.add(0, DRUM_R, 0), sheaveC.add(toDrum.scale(SHEAVE_R)).add(0, 0.1, 0), rope, light);
        HoistGeometry.Shaft shaft = be.shaft();
        if (shaft == null) return;
        double depth = be.renderDepth(partialTicks);
        Vec3 cageTop = cageTop(shaft, depth).subtract(o);
        Vec3 toCage = horizontal(cageTop.subtract(sheaveC));
        Vec3 from = sheaveC.add(toCage.scale(Math.min(SHEAVE_R, cageTop.subtract(sheaveC).multiply(1, 0, 1).length())));
        int cageLight = LevelRenderer.getLightColor(be.getLevel(), BlockPos.containing(cageTop.add(o)));
        draw(vc, ms, from, new Vec3(from.x, cageTop.y, from.z), rope, cageLight);
    }

    /** Центр крыши клети (бриделя) при глубине {@code depth}. */
    private static Vec3 cageTop(HoistGeometry.Shaft shaft, double depth) {
        BlockPos c = new BlockPos(shaft.cageX(), shaft.topY(), shaft.cageZ());
        Vec3 center = BoilerGeometry.world(c, shaft.cageFacing(), 16, 8, 16);
        return new Vec3(center.x, shaft.topY() - depth + 2, center.z);
    }

    private static Vec3 horizontal(Vec3 v) {
        Vec3 h = new Vec3(v.x, 0, v.z);
        double l = h.length();
        return l < 1e-6 ? Vec3.ZERO : h.scale(1 / l);
    }

    /** Канат — две скрещённые ленты с обеих сторон. */
    private static void draw(VertexConsumer vc, PoseStack ms, Vec3 a, Vec3 b, TextureAtlasSprite sprite, int light) {
        Vec3 dir = b.subtract(a);
        if (dir.lengthSqr() < 1e-6) return;
        Vec3 n = dir.normalize();
        Vec3 side1 = Math.abs(n.y) > 0.99 ? new Vec3(1, 0, 0) : n.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 side2 = n.cross(side1).normalize();
        PoseStack.Pose pose = ms.last();
        for (Vec3 side : new Vec3[] {side1.scale(ROPE_W / 2), side2.scale(ROPE_W / 2)}) {
            quad(vc, pose, a.subtract(side), a.add(side), b.add(side), b.subtract(side), sprite, light);
            quad(vc, pose, b.subtract(side), b.add(side), a.add(side), a.subtract(side), sprite, light);
        }
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose pose, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, TextureAtlasSprite s, int light) {
        float u0 = s.getU0(), u1 = s.getU(0.25f), v0 = s.getV0(), v1 = s.getV1();
        vertex(vc, pose, p0, u0, v0, light);
        vertex(vc, pose, p1, u1, v0, light);
        vertex(vc, pose, p2, u1, v1, light);
        vertex(vc, pose, p3, u0, v1, light);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, Vec3 p, float u, float v, int light) {
        vc.addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setColor(255, 255, 255, 255).setUv(u, v).setLight(light)
                .setNormal(pose, 0, 1, 0);
    }

    @Override
    public boolean shouldRenderOffScreen(MineHoistBlockEntity be) {
        return true;
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(MineHoistBlockEntity be) {
        return net.minecraft.world.phys.AABB.INFINITE;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
