package dev.denfry.promptcritical.mod.ops.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.denfry.promptcritical.mod.ops.MechanicalDisconnectBlockEntity;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Расцепная муфта: две половины вала Create по оси блока. Половина со стороны источника крутится с сетью, выходная —
 * только пока муфта сцеплена (арт 13 §13.16: {@code shaft_in} / {@code shaft_out}).
 */
public final class MechanicalDisconnectRenderer implements BlockEntityRenderer<MechanicalDisconnectBlockEntity> {
    public MechanicalDisconnectRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(MechanicalDisconnectBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffers, int light,
            int overlay) {
        BlockState state = be.getBlockState();
        Direction.Axis axis = state.getValue(BlockStateProperties.AXIS);
        VertexConsumer vc = buffers.getBuffer(RenderType.solid());
        for (Direction d : new Direction[] {Direction.get(Direction.AxisDirection.POSITIVE, axis),
                Direction.get(Direction.AxisDirection.NEGATIVE, axis)}) {
            SuperByteBuffer half = CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state, d);
            if (be.getRotationSpeedModifier(d) != 0) KineticBlockEntityRenderer.standardKineticRotationTransform(half, be, light);
            else half.light(light);
            half.renderInto(ms, vc);
        }
    }
}
