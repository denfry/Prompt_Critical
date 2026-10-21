package dev.denfry.promptcritical.mod.grid.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.grid.DisconnectorBlockEntity;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

/**
 * Нож разъединителя: шарнир на левом изоляторе (x −10, y 18 px модели), отключён — нож поднят на 70° (видимый
 * разрыв). Поворот плавный, по кадрам клиента.
 */
final class DisconnectorRenderer implements BlockEntityRenderer<DisconnectorBlockEntity> {
    private static final float PIVOT_X = -10 / 16f, PIVOT_Y = 18 / 16f, PIVOT_Z = 8 / 16f, OPEN_DEG = 70;

    @Override
    public void render(DisconnectorBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        boolean open = state.getValue(BlockStateProperties.OPEN);
        float target = open ? OPEN_DEG : 0;
        be.bladeAngle = Float.isNaN(be.bladeAngle) ? target : be.bladeAngle + (target - be.bladeAngle) * 0.15f;
        Direction facing = state.getBlock() instanceof PcBlock pc ? pc.facing(state) : Direction.NORTH;
        SuperByteBuffer buf = CachedBuffers.partial(GridClient.BLADE, state);
        buf.center().rotateYDegrees(-(facing.toYRot() + 180)).uncenter()
                .translate(PIVOT_X, PIVOT_Y, PIVOT_Z).rotateZDegrees(be.bladeAngle).translate(-PIVOT_X, -PIVOT_Y, -PIVOT_Z)
                .light(light).renderInto(ms, buffers.getBuffer(RenderType.cutoutMipped()));
    }

    @Override
    public AABB getRenderBoundingBox(DisconnectorBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(2);
    }
}
