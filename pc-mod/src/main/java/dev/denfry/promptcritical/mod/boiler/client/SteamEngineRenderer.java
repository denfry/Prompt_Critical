package dev.denfry.promptcritical.mod.boiler.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.denfry.promptcritical.mod.boiler.SteamEngineBlockEntity;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Кривошипно-шатунный механизм паровой машины (арт 07 §7.23) по углу коленвала с сервера: коленвал и маховик
 * вращаются вокруг оси X на якоре рамы (y = z = 8 px, рама на 2 блока за цилиндром), палец кривошипа на радиусе
 * 5 px, шатун 20 px качается между пальцем и крейцкопфом, шток с крейцкопфом ходит вдоль оси цилиндра (y = 4 px).
 */
final class SteamEngineRenderer implements BlockEntityRenderer<SteamEngineBlockEntity> {
    private static final float FRAME_Z = 32, AXIS_Y = 8, AXIS_Z = 8, CRANK_R = 5, ROD_L = 20, CROSSHEAD_Y = 4,
            CROSSHEAD_REST_Z = 16;

    @Override
    public void render(SteamEngineBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        Direction facing = be.facing();
        VertexConsumer vc = buffers.getBuffer(RenderType.cutoutMipped());
        float theta = be.renderCrank(partialTicks);
        float deg = (float) Math.toDegrees(theta);

        for (PartialModel p : new PartialModel[] {BoilerClient.CRANK, BoilerClient.FLYWHEEL}) {
            SuperByteBuffer buf = oriented(p, state, facing);
            buf.translate(0, AXIS_Y / 16, (FRAME_Z + AXIS_Z) / 16).rotateXDegrees(deg).translate(0, -AXIS_Y / 16, -(FRAME_Z + AXIS_Z) / 16)
                    .translate(0, 0, FRAME_Z / 16);
            buf.light(light).renderInto(ms, vc);
        }

        // палец кривошипа (поворот вокруг +X: y' = y cos θ − z sin θ, z' = y sin θ + z cos θ)
        float pinY = AXIS_Y + CRANK_R * (float) Math.cos(theta);
        float pinZ = FRAME_Z + AXIS_Z + CRANK_R * (float) Math.sin(theta);
        float dy = CROSSHEAD_Y - pinY;
        float crossZ = pinZ - (float) Math.sqrt(Math.max(0, ROD_L * ROD_L - dy * dy));
        float dz = crossZ - pinZ;
        float phi = (float) Math.toDegrees(Math.atan2(-dy, dz));
        SuperByteBuffer rod = oriented(BoilerClient.CON_ROD, state, facing);
        rod.translate(0, pinY / 16, pinZ / 16).rotateXDegrees(phi);
        rod.light(light).renderInto(ms, vc);

        SuperByteBuffer piston = oriented(BoilerClient.PISTON_ROD, state, facing);
        piston.translate(0, 0, (crossZ - CROSSHEAD_REST_Z) / 16);
        piston.light(light).renderInto(ms, vc);
    }

    private static SuperByteBuffer oriented(PartialModel p, BlockState state, Direction facing) {
        return CachedBuffers.partial(p, state).center().rotateYDegrees(-(facing.toYRot() + 180)).uncenter();
    }

    @Override
    public boolean shouldRenderOffScreen(SteamEngineBlockEntity be) {
        return true;
    }
}
