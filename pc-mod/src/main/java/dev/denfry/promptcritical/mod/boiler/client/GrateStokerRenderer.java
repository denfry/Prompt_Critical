package dev.denfry.promptcritical.mod.boiler.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.denfry.promptcritical.mod.boiler.GrateStokerBlockEntity;
import dev.denfry.promptcritical.mod.foundry.KineticMachineBlock;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Цепная решётка: полувал Create и цепь, которая ползёт к топке (звено 4 px) со скоростью вала. */
final class GrateStokerRenderer implements BlockEntityRenderer<GrateStokerBlockEntity> {
    @Override
    public void render(GrateStokerBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        VertexConsumer vc = buffers.getBuffer(RenderType.cutoutMipped());
        if (state.getBlock() instanceof KineticMachineBlock km) {
            SuperByteBuffer shaft = CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state, km.shaftFace(state));
            KineticBlockEntityRenderer.standardKineticRotationTransform(shaft, be, light).renderInto(ms, vc);
        }
        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        // цепь идёт от вала и при пустом бункере; running — только раскалённый слой в модели
        float time = AnimationTickHolder.getRenderTime(be.getLevel());
        float offset = (time * Math.abs(be.getSpeed()) / 32f * 0.2f) % 4f;
        SuperByteBuffer chain = CachedBuffers.partial(BoilerClient.CHAIN, state).center()
                .rotateYDegrees(-(facing.toYRot() + 180)).uncenter().translate(0, 0, (offset - 2) / 16f);
        chain.light(light).renderInto(ms, vc);
    }
}
