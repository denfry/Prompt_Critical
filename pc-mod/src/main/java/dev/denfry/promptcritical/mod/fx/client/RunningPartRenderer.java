package dev.denfry.promptcritical.mod.fx.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Подвижная часть машины без вала Create, которая двигается, пока горит свойство {@code running} блока: ход вниз
 * (штанга гидрорезки), вращение (мешалка, вентилятор). Часть лежит в координатах модели с фасадом на север; стоит
 * машина — часть в исходном положении.
 */
public final class RunningPartRenderer<T extends BlockEntity> implements BlockEntityRenderer<T> {
    public enum Motion {
        /** Вниз на {@code a} пикселей и обратно, период {@code period} тиков: медленный рабочий ход, быстрый подъём. */
        STROKE_Y,
        /** Вращение вокруг вертикали через x = a, z = b пикселей, оборот за {@code period} тиков. */
        SPIN_Y
    }

    private final PartialModel part;
    private final Motion motion;
    private final float a, b, period;

    public RunningPartRenderer(PartialModel part, Motion motion, float a, float b, float period) {
        this.part = part;
        this.motion = motion;
        this.a = a;
        this.b = b;
        this.period = period;
    }

    @Override
    public void render(T be, float partialTicks, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        Direction facing = state.getBlock() instanceof PcBlock pc ? pc.facing(state) : Direction.NORTH;
        boolean running = state.hasProperty(PcProperties.RUNNING) && state.getValue(PcProperties.RUNNING);
        float time = AnimationTickHolder.getRenderTime(be.getLevel());
        SuperByteBuffer buf = CachedBuffers.partial(part, state);
        buf.center().rotateYDegrees(-(facing.toYRot() + 180)).uncenter();
        if (running) {
            float phase = (time % period) / period;
            switch (motion) {
                case STROKE_Y -> {
                    float down = phase < 0.8f ? phase / 0.8f : (1 - phase) / 0.2f;
                    buf.translate(0, -down * a / 16f, 0);
                }
                case SPIN_Y -> buf.translate(a / 16f, 0, b / 16f).rotateYDegrees(phase * 360).translate(-a / 16f, 0, -b / 16f);
            }
        }
        // часть может стоять в глухом объёме машины: свет — над ней
        int lit = be.getLevel() == null ? light : LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos().above());
        buf.light(Math.max(light, lit)).renderInto(ms, buffers.getBuffer(RenderType.cutoutMipped()));
    }

    @Override
    public AABB getRenderBoundingBox(T be) {
        return new AABB(be.getBlockPos()).inflate(2);
    }
}
