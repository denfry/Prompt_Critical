package dev.denfry.promptcritical.mod.client.whole;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

/**
 * Рисует цельную модель машины с контроллера (спецификация real-scale machines, §1): каждый кусок — partial со
 * смещением из манифеста, вся модель повёрнута по фасаду вокруг центра контроллера. Границы отрисовки — вся модель,
 * чтобы она не пропадала, когда сам контроллер вне поля зрения.
 */
public class WholeModelRenderer<T extends BlockEntity & WholeModelHost> implements BlockEntityRenderer<T> {
    /** Дальность отрисовки, блоков: большие машины видны издалека. */
    public static final int VIEW_DISTANCE = 256;

    @Override
    public void render(T be, float partialTicks, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null) return;
        String id = modelId(be);
        WholeModels.Model m = id == null ? null : WholeModels.get(id);
        if (m == null) return;
        Direction facing = be.wholeModelFacing();
        int lit = LevelRenderer.getLightColor(be.getLevel(), be.wholeModelLightPos());
        VertexConsumer vc = buffers.getBuffer(RenderType.cutoutMipped());
        float yRot = -(facing.toYRot() + 180);
        for (WholeModels.Chunk c : m.chunks()) {
            SuperByteBuffer b = CachedBuffers.partial(c.partial(), be.getBlockState());
            b.center().rotateYDegrees(yRot).uncenter().translate(c.x(), c.y(), c.z()).light(lit).renderInto(ms, vc);
        }
        parts(be, partialTicks, ms, vc, lit);
    }

    /**
     * Какую цельную модель рисовать: по умолчанию — {@link WholeModelHost#wholeModelId()}; машина с вариантом состояния
     * (горит — {@code <id>_lit}) подменяет её здесь, а порты и формы клеток остаются по id блока.
     */
    @org.jetbrains.annotations.Nullable
    protected String modelId(T be) {
        return be.wholeModelId();
    }

    /** Подвижные части (partial-модели) поверх цельной модели; по умолчанию их нет. */
    protected void parts(T be, float partialTicks, PoseStack ms, VertexConsumer vc, int light) {}

    /**
     * Вторая цельная модель (wholemodels/{@code id}.json), повёрнутая на {@code angle} рад вокруг оси x модели через точку
     * ({@code px}, {@code py}, {@code pz}) блоков от угла клетки контроллера, — например барабан подъёмной машины или
     * поворотный корпус конвертера; фасад — как у основной модели. Модели нет — ничего не рисует.
     */
    protected static void wholeAboutX(String id, BlockEntity be, Direction facing, double px, double py, double pz, float angle,
            PoseStack ms, VertexConsumer vc, int light) {
        WholeModels.Model m = WholeModels.get(id);
        if (m == null) return;
        for (WholeModels.Chunk c : m.chunks())
            CachedBuffers.partial(c.partial(), be.getBlockState()).center().rotateYDegrees(-(facing.toYRot() + 180)).uncenter()
                    .translate(px, py, pz).rotateX(angle).translate(-px, -py, -pz)
                    .translate(c.x(), c.y(), c.z()).light(light).renderInto(ms, vc);
    }

    /**
     * Partial подвижной части, повёрнутая по фасаду вокруг контроллера и сдвинутая на (dx, dy, dz) блоков в пространстве
     * модели (фасад на север) — как куски цельной модели; дальше вызывающий добавляет своё движение.
     */
    protected static SuperByteBuffer part(dev.engine_room.flywheel.lib.model.baked.PartialModel p, BlockEntity be, Direction facing,
            double dx, double dy, double dz) {
        return CachedBuffers.partial(p, be.getBlockState()).center().rotateYDegrees(-(facing.toYRot() + 180)).uncenter().translate(dx, dy, dz);
    }

    @Override
    public boolean shouldRenderOffScreen(T be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }

    @Override
    public AABB getRenderBoundingBox(T be) {
        String id = modelId(be);
        WholeModels.Model m = id == null ? null : WholeModels.get(id);
        if (m == null) return new AABB(be.getBlockPos());
        double[] a = WholeModelMath.rotate(m.min(), be.wholeModelFacing()), b = WholeModelMath.rotate(m.max(), be.wholeModelFacing());
        var p = be.getBlockPos();
        return new AABB(p.getX() + Math.min(a[0], b[0]), p.getY() + Math.min(a[1], b[1]), p.getZ() + Math.min(a[2], b[2]),
                p.getX() + Math.max(a[0], b[0]), p.getY() + Math.max(a[1], b[1]), p.getZ() + Math.max(a[2], b[2])).inflate(1);
    }
}
