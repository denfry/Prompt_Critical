package dev.denfry.promptcritical.mod.npp.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.npp.CanduFuelling;
import dev.denfry.promptcritical.mod.npp.CanduReactorBlockEntity;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Перегрузочная машина CANDU на торце реактора (арт — art/blockbench/core/candu_reactor.author.js): мост ходит по
 * колоннам вверх-вниз, машина — по мосту вдоль торца. Стоит внизу у западной колонны; после перегрузки канала
 * ({@link CanduReactorBlockEntity#refuelTick()}) едет к его концевой арматуре, стоит у неё, пока толкатели сдвигают
 * пучки, и возвращается.
 */
final class CanduRenderer implements BlockEntityRenderer<CanduReactorBlockEntity> {
    @Override
    public void render(CanduReactorBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null) return;
        BlockState state = be.getBlockState();
        Direction facing = state.getBlock() instanceof PcBlock pc ? pc.facing(state) : Direction.NORTH;
        // контроллер внутри глухого объёма: свет — с площадки перед торцом
        int lit = LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos().relative(facing, 2));
        double t = be.getLevel().getGameTime() - be.refuelTick() + partialTicks;
        double[] p = CanduFuelling.offset(be.refuelChannel(), t);
        VertexConsumer vc = buffers.getBuffer(RenderType.cutoutMipped());
        SuperByteBuffer bridge = CachedBuffers.partial(NppClient.CANDU_BRIDGE, state);
        bridge.center().rotateYDegrees(-(facing.toYRot() + 180)).uncenter()
                .translate(0, (float) p[1] / 16f, 0).light(lit).renderInto(ms, vc);
        SuperByteBuffer head = CachedBuffers.partial(NppClient.CANDU_HEAD, state);
        head.center().rotateYDegrees(-(facing.toYRot() + 180)).uncenter()
                .translate((float) p[0] / 16f, (float) p[1] / 16f, (float) p[2] / 16f).light(lit).renderInto(ms, vc);
    }

    @Override
    public AABB getRenderBoundingBox(CanduReactorBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(2);
    }
}
