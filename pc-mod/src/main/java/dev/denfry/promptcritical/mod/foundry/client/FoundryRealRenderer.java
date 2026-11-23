package dev.denfry.promptcritical.mod.foundry.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.client.whole.WholeModelHost;
import dev.denfry.promptcritical.mod.client.whole.WholeModelRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Машина литейки, у которой есть пара реального масштаба на том же BE (формовочная машина, выбивная решётка): старая —
 * {@link FoundryMachineRenderer}, реальная — полувал Create в западной грани контроллера, цельная модель и подвижная часть
 * в координатах цельной модели (сдвига нет) с движением {@link Shake}.
 */
final class FoundryRealRenderer<T extends BlockEntity & WholeModelHost> implements BlockEntityRenderer<T> {
    /** Как трясётся часть реальной машины, пока она работает. */
    enum Shake {
        /**
         * Встряхивающе-прессовая формовка: стол встряхивается на 0,8 px, затем прессуется вверх до 2 px и опускается
         * (art/blockbench/foundry/molding_station/molding_station_real.author.js).
         */
        JOLT_SQUEEZE,
        /** Инерционная выбивная решётка: настил дрожит на ±0,4 px (shakeout_grate_real.author.js). */
        VIBRATE
    }

    /** Цикл формовки на рендере, тиков: из них последние {@link #SQUEEZE_TICKS} — прессование. */
    private static final float CYCLE_TICKS = 80, SQUEEZE_TICKS = 24;

    private final FoundryMachineRenderer<T> old;
    private final FoundryMachineRenderer<T> shaft = new FoundryMachineRenderer<>(null, FoundryMachineRenderer.Motion.STATIC, 0, 0, 1);
    private final WholeModelRenderer<T> real;

    /** {@code realPart} — partial реальной машины; создаётся статикой {@link FoundryClient} до запекания моделей. */
    FoundryRealRenderer(PartialModel oldPart, PartialModel realPart, Shake shake) {
        this.old = new FoundryMachineRenderer<>(oldPart, FoundryMachineRenderer.Motion.JOLT, 0, 0, 1);
        PartialModel part = realPart;
        this.real = new WholeModelRenderer<>() {
            @Override
            protected void parts(T be, float pt, PoseStack ms, VertexConsumer vc, int light) {
                BlockState s = be.getBlockState();
                boolean running = s.hasProperty(PcProperties.RUNNING) && s.getValue(PcProperties.RUNNING);
                float dy = running ? lift(shake, AnimationTickHolder.getRenderTime(be.getLevel())) : 0;
                part(part, be, be.wholeModelFacing(), 0, dy / 16f, 0).light(light).renderInto(ms, vc);
            }
        };
    }

    /** Сдвиг части вверх, px модели, в момент {@code time} (тики) работающей машины. */
    static float lift(Shake shake, float time) {
        return switch (shake) {
            case VIBRATE -> (float) Math.sin(time * 2.5) * 0.4f;
            case JOLT_SQUEEZE -> {
                float t = time % CYCLE_TICKS, jolt = CYCLE_TICKS - SQUEEZE_TICKS;
                yield t < jolt ? (float) Math.abs(Math.sin(t * 0.6)) * 0.8f
                        : (float) Math.sin(Math.PI * (t - jolt) / SQUEEZE_TICKS) * 2f;
            }
        };
    }

    @Override
    public void render(T be, float pt, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
        if (be.wholeModelId() == null) {
            old.render(be, pt, ms, buffers, light, overlay);
            return;
        }
        shaft.render(be, pt, ms, buffers, light, overlay);
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
        return be.wholeModelId() != null ? real.getRenderBoundingBox(be) : old.getRenderBoundingBox(be);
    }
}
