package dev.denfry.promptcritical.mod.manufacturing.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.client.whole.WholeModelHost;
import dev.denfry.promptcritical.mod.client.whole.WholeModelRenderer;
import dev.denfry.promptcritical.mod.foundry.client.FoundryMachineRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Машина кузницы или машиностроения, у которой есть пара реального масштаба на том же BE (18 §18.6–18.7): у старого
 * блока — прежний рендер (или ничего), у реального — полувал Create в контроллере (у машин на валу), цельная модель и
 * подвижные части по {@link Anim}. Оси и ходы — из шапок art/blockbench/smithy/**&#47;*_real.author.js и
 * art/blockbench/rolling/real/rolling_shop_real.author.js; px модели переводятся в блоки делением на 16.
 */
final class SmithyRealRenderer<T extends BlockEntity & WholeModelHost> implements BlockEntityRenderer<T> {
    /** Рисует подвижные части реальной машины; {@code on} — работает (RUNNING) или горит (LIT), {@code t} — тики. */
    @FunctionalInterface
    interface Anim {
        void draw(Ctx c);
    }

    /** Контекст кадра для {@link Anim}. */
    record Ctx(Placer placer, boolean on, float t, PoseStack ms, VertexConsumer vc, int light) {
        /** Partial, поставленная по фасаду и сдвинутая на (dx, dy, dz) px модели (сдвиг, с которым её сохранили, — обратный). */
        SuperByteBuffer at(PartialModel p, double dx, double dy, double dz) {
            return placer.place(p, dx / 16, dy / 16, dz / 16);
        }

        void put(SuperByteBuffer b) {
            b.light(light).renderInto(ms, vc);
        }

        /** Вращение вокруг оси {@code axis} через точку (px, py, pz) px в координатах самой partial на {@code deg}. */
        static SuperByteBuffer spin(SuperByteBuffer b, Direction.Axis axis, double px, double py, double pz, float deg) {
            b.translate(px / 16, py / 16, pz / 16);
            switch (axis) {
                case X -> b.rotateXDegrees(deg);
                case Y -> b.rotateYDegrees(deg);
                case Z -> b.rotateZDegrees(deg);
            }
            return b.translate(-px / 16, -py / 16, -pz / 16);
        }

        /** Угол вращения, градусы: {@code rpm} оборотов в минуту, пока машина работает. */
        float angle(float rpm) {
            return on ? t * rpm * 0.3f % 360 : 0;
        }

        /** Ход 0…1 (0 — исходное положение): удар раз в {@code period} тиков, пока машина работает. */
        float stroke(float period) {
            if (!on) return 0;
            float ph = t % period / period;
            return ph < 0.25f ? ph / 0.25f : ph < 0.5f ? 1 - (ph - 0.25f) / 0.25f : 0;
        }
    }

    /** Partial, поставленная по фасаду вокруг контроллера и сдвинутая на (dx, dy, dz) блоков ({@link WholeModelRenderer}). */
    @FunctionalInterface
    interface Placer {
        SuperByteBuffer place(PartialModel p, double dx, double dy, double dz);
    }

    @Nullable
    private final BlockEntityRenderer<T> old;
    @Nullable
    private final FoundryMachineRenderer<T> shaft;
    private final WholeModelRenderer<T> real;

    /**
     * {@code old} — рендер старого блока (null — у старого только модель блока), {@code kinetic} — рисовать полувал Create
     * в контроллере реальной машины.
     */
    SmithyRealRenderer(@Nullable BlockEntityRenderer<T> old, boolean kinetic, Anim anim) {
        this.old = old;
        this.shaft = kinetic ? new FoundryMachineRenderer<>(null, FoundryMachineRenderer.Motion.STATIC, 0, 0, 1) : null;
        this.real = new WholeModelRenderer<>() {
            @Override
            protected void parts(T be, float pt, PoseStack ms, VertexConsumer vc, int light) {
                BlockState s = be.getBlockState();
                boolean on = s.hasProperty(PcProperties.RUNNING) ? s.getValue(PcProperties.RUNNING)
                        : s.hasProperty(BlockStateProperties.LIT) && s.getValue(BlockStateProperties.LIT);
                Direction f = be.wholeModelFacing();
                anim.draw(new Ctx((p, dx, dy, dz) -> part(p, be, f, dx, dy, dz), on,
                        AnimationTickHolder.getRenderTime(be.getLevel()), ms, vc, light));
            }
        };
    }

    @Override
    public void render(T be, float pt, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
        if (be.wholeModelId() == null) {
            if (old != null) old.render(be, pt, ms, buffers, light, overlay);
            return;
        }
        if (shaft != null) shaft.render(be, pt, ms, buffers, light, overlay);
        real.render(be, pt, ms, buffers, light, overlay);
    }

    @Override
    public boolean shouldRenderOffScreen(T be) {
        return true;
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
