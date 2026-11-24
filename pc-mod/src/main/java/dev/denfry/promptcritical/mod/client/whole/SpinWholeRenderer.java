package dev.denfry.promptcritical.mod.client.whole;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Машина реального масштаба на BE старой машины: старая рисуется своим рендером ({@code old}, может не быть), реальная —
 * цельной моделью и частями из шапки её арта (art/blockbench/&lt;кат&gt;/&lt;id&gt;.author.js): вращающиеся вокруг оси через
 * точку в px модели со скоростью {@code rpm} об/мин и показываемые только пока машина работает ({@code running}).
 * Сдвиг хранения части (px) возвращается при отрисовке.
 */
public final class SpinWholeRenderer<T extends BlockEntity & WholeModelHost> implements BlockEntityRenderer<T> {
    private record Spin(PartialModel part, Direction.Axis axis, float a, float b, float dx, float dy, float dz,
            ToDoubleFunction<BlockEntity> rpm) {}

    @Nullable
    private final BlockEntityRenderer<T> old;
    private final List<Spin> spins = new ArrayList<>();
    private final List<PartialModel> whileRunning = new ArrayList<>();
    /** Вариант модели по состоянию (горит, тревога): суффикс к id и когда он рисуется. */
    private record Variant(String suffix, java.util.function.Predicate<BlockState> when) {}
    private final List<Variant> variants = new ArrayList<>();
    private final WholeModelRenderer<T> real = new WholeModelRenderer<>() {
        @Override
        @Nullable
        protected String modelId(T be) {
            String id = be.wholeModelId();
            if (id == null) return null;
            for (Variant v : variants) if (v.when().test(be.getBlockState())) return id + v.suffix();
            return id;
        }

        @Override
        protected void parts(T be, float pt, PoseStack ms, VertexConsumer vc, int light) {
            Direction f = be.wholeModelFacing();
            float time = AnimationTickHolder.getRenderTime(be.getLevel());
            for (Spin s : spins) {
                float deg = (float) ((time * s.rpm().applyAsDouble(be) * 0.3) % 360);
                var buf = part(s.part(), be, f, s.dx() / 16, s.dy() / 16, s.dz() / 16);
                float a = s.a() / 16, b = s.b() / 16;
                switch (s.axis()) {
                    case X -> buf.translate(0, a, b).rotateXDegrees(deg).translate(0, -a, -b);
                    case Y -> buf.translate(a, 0, b).rotateYDegrees(deg).translate(-a, 0, -b);
                    case Z -> buf.translate(a, b, 0).rotateZDegrees(deg).translate(-a, -b, 0);
                }
                buf.light(light).renderInto(ms, vc);
            }
            BlockState st = be.getBlockState();
            if (st.hasProperty(PcProperties.RUNNING) && st.getValue(PcProperties.RUNNING))
                for (PartialModel p : whileRunning) part(p, be, f, 0, 0, 0).light(light).renderInto(ms, vc);
        }
    };

    public SpinWholeRenderer(@Nullable BlockEntityRenderer<T> old) {
        this.old = old;
    }

    /**
     * Часть, что вращается вокруг оси {@code axis} через (a, b) px модели (для X — y, z; для Y — x, z; для Z — x, y);
     * она хранится сдвинутой на −(dx, dy, dz) px, рисуется на месте.
     */
    public SpinWholeRenderer<T> spin(PartialModel part, Direction.Axis axis, float a, float b, float dx, float dy, float dz,
            ToDoubleFunction<BlockEntity> rpm) {
        spins.add(new Spin(part, axis, a, b, dx, dy, dz, rpm));
        return this;
    }

    /**
     * Вариант цельной модели {@code <id><suffix>}, пока состояние удовлетворяет {@code when} (первый подходящий);
     * модель варианта регистрирует вызывающий.
     */
    public SpinWholeRenderer<T> variant(String suffix, java.util.function.Predicate<BlockState> when) {
        variants.add(new Variant(suffix, when));
        return this;
    }

    /** Часть, видимая только пока машина работает (пена флотокамеры). */
    public SpinWholeRenderer<T> whileRunning(PartialModel part) {
        whileRunning.add(part);
        return this;
    }

    @Override
    public void render(T be, float pt, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
        if (be.wholeModelId() != null) real.render(be, pt, ms, buffers, light, overlay);
        else if (old != null) old.render(be, pt, ms, buffers, light, overlay);
    }

    @Override
    public boolean shouldRenderOffScreen(T be) {
        return be.wholeModelId() != null || old != null && old.shouldRenderOffScreen(be);
    }

    @Override
    public int getViewDistance() {
        return WholeModelRenderer.VIEW_DISTANCE;
    }

    @Override
    public AABB getRenderBoundingBox(T be) {
        if (be.wholeModelId() != null) return real.getRenderBoundingBox(be);
        return old != null ? old.getRenderBoundingBox(be) : new AABB(be.getBlockPos());
    }
}
