package dev.denfry.promptcritical.mod.foundry.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.foundry.KineticMachineBlock;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Рендер машин литейки и цеха: полувал Create в грани входа вала и подвижная часть из арт-реестра 08 §8.21
 * (шпиндель, эксцентрик пульсатора, стол встряхивания, тигель). Части лежат в координатах модели с фасадом на север;
 * анимация — вокруг оси части в её центре.
 */
public final class FoundryMachineRenderer<T extends BlockEntity> implements BlockEntityRenderer<T> {
    /** Как двигается часть. */
    public enum Motion { STATIC, SPIN_X, SPIN_Y, SPIN_Z, JOLT }

    @Nullable
    private final PartialModel part;
    private final Motion motion;
    /** Ось части (пиксели модели): для SPIN_X — y, z; для SPIN_Y — x, z; для SPIN_Z — x, y. */
    private final float a, b;
    /** Передаточное число шпинделя к валу. */
    private final float gear;
    /** Рисовать полувал Create в грани входа (нет, если вал есть в самой части). */
    private final boolean halfShaft;

    public FoundryMachineRenderer(@Nullable PartialModel part, Motion motion, float a, float b, float gear) {
        this(part, motion, a, b, gear, true);
    }

    public FoundryMachineRenderer(@Nullable PartialModel part, Motion motion, float a, float b, float gear, boolean halfShaft) {
        this.halfShaft = halfShaft;
        this.part = part;
        this.motion = motion;
        this.a = a / 16f;
        this.b = b / 16f;
        this.gear = gear;
    }

    @Override
    public void render(T be, float partialTicks, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        VertexConsumer vc = buffers.getBuffer(RenderType.cutoutMipped());
        KineticBlockEntity kbe = be instanceof KineticBlockEntity k ? k : null;
        if (halfShaft && kbe != null && state.getBlock() instanceof KineticMachineBlock km) {
            SuperByteBuffer shaft = CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state, km.shaftFace(state));
            KineticBlockEntityRenderer.standardKineticRotationTransform(shaft, kbe, light).renderInto(ms, vc);
        }
        if (part == null) return;
        SuperByteBuffer buf = CachedBuffers.partial(part, state);
        Direction facing = state.getBlock() instanceof PcBlock pc ? pc.facing(state) : Direction.NORTH;
        buf.center().rotateYDegrees(-(facing.toYRot() + 180)).uncenter();
        float time = AnimationTickHolder.getRenderTime(be.getLevel());
        float speed = kbe == null ? 0 : kbe.getSpeed() * gear;
        float angle = (time * speed * 3f / 10f) % 360f;
        switch (motion) {
            case SPIN_X -> buf.translate(0, a, b).rotateXDegrees(angle).translate(0, -a, -b);
            case SPIN_Y -> buf.translate(a, 0, b).rotateYDegrees(angle).translate(-a, 0, -b);
            case SPIN_Z -> buf.translate(a, b, 0).rotateZDegrees(angle).translate(-a, -b, 0);
            case JOLT -> {
                boolean running = state.hasProperty(PcProperties.RUNNING) && state.getValue(PcProperties.RUNNING);
                if (running) buf.translate(0, (float) Math.abs(Math.sin(time * 0.6)) * 1.5f / 16f, 0);
            }
            case STATIC -> {
            }
        }
        buf.light(light).renderInto(ms, vc);
    }

    @Override
    public boolean shouldRenderOffScreen(T be) {
        return true;
    }
}
