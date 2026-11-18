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
 * Движущиеся части паровой машины (модели {@code art/blockbench/boilers/steam_engine.author.js}) по углу коленвала с
 * сервера. Коленвал с маховиком вращаются вокруг оси X на высоте оси цилиндра (y = 4 px, рама на 2 блока за
 * цилиндром, z = 8 px рамы); палец кривошипа на радиусе 5 px, шатун 20 px качается между пальцем и крейцкопфом. Эксцентрик
 * опережает кривошип на 120° с эксцентриситетом 1,2 px, его тяга 17 px двигает золотниковый шток. Шары центробежного
 * регулятора вращаются с валиком, а угол их подъёма считается из равновесия: cos α = g / (ω² L).
 */
final class SteamEngineRenderer implements BlockEntityRenderer<SteamEngineBlockEntity> {
    private static final float FRAME_Z = 32, AXIS_Y = 4, AXIS_Z = 8, CRANK_R = 5, ROD_L = 20, CROSSHEAD_Y = 4,
            CROSSHEAD_REST_Z = 16;
    /** Эксцентрик: радиус, опережение относительно кривошипа, длина тяги, покой пальца золотникового штока. */
    private static final float ECC_R = 1.2f, ECC_LEAD = (float) Math.toRadians(120), ECC_ROD_L = 17, VALVE_Y = 4, VALVE_REST_Z = 23;
    /** Регулятор: ось валика, высота шарниров, вылет шарнира от оси, передача от коленвала, длина рычага шара (м). */
    private static final float GOV_X = 15, GOV_Z = -4.5f, GOV_PIVOT_Y = 25, GOV_PIVOT_R = 0.8f, GOV_RATIO = 0.57f;
    private static final double GOV_ARM_M = 0.25, G = 9.80665;

    @Override
    public void render(SteamEngineBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        Direction facing = be.facing();
        VertexConsumer vc = buffers.getBuffer(RenderType.cutoutMipped());
        float theta = be.renderCrank(partialTicks);
        float deg = (float) Math.toDegrees(theta);
        float crankY = AXIS_Y, crankZ = FRAME_Z + AXIS_Z;

        for (PartialModel p : new PartialModel[] {BoilerClient.CRANK, BoilerClient.FLYWHEEL}) {
            SuperByteBuffer buf = oriented(p, state, facing);
            buf.translate(0, crankY / 16, crankZ / 16).rotateXDegrees(deg).translate(0, -AXIS_Y / 16, -(FRAME_Z + AXIS_Z) / 16)
                    .translate(0, 0, FRAME_Z / 16);
            buf.light(light).renderInto(ms, vc);
        }

        // палец кривошипа (поворот вокруг +X: y' = y cos θ − z sin θ, z' = y sin θ + z cos θ)
        float pinY = crankY + CRANK_R * (float) Math.cos(theta);
        float pinZ = crankZ + CRANK_R * (float) Math.sin(theta);
        float crossZ = slide(pinY, pinZ, CROSSHEAD_Y, ROD_L);
        rod(BoilerClient.CON_ROD, state, facing, pinY, pinZ, CROSSHEAD_Y, crossZ, light, ms, vc);
        SuperByteBuffer piston = oriented(BoilerClient.PISTON_ROD, state, facing);
        piston.translate(0, 0, (crossZ - CROSSHEAD_REST_Z) / 16);
        piston.light(light).renderInto(ms, vc);

        // эксцентрик и золотниковый шток
        float eccY = crankY + ECC_R * (float) Math.cos(theta + ECC_LEAD);
        float eccZ = crankZ + ECC_R * (float) Math.sin(theta + ECC_LEAD);
        float valveZ = slide(eccY, eccZ, VALVE_Y, ECC_ROD_L);
        rod(BoilerClient.ECC_ROD, state, facing, eccY, eccZ, VALVE_Y, valveZ, light, ms, vc);
        SuperByteBuffer valve = oriented(BoilerClient.VALVE_ROD, state, facing);
        valve.translate(0, 0, (valveZ - VALVE_REST_Z) / 16);
        valve.light(light).renderInto(ms, vc);

        governor(be, state, facing, deg, light, ms, vc);
    }

    /** Положение ползуна на линии y = {@code slideY} перед шарниром ({@code fromY}, {@code fromZ}) при длине тяги {@code len}. */
    private static float slide(float fromY, float fromZ, float slideY, float len) {
        float dy = slideY - fromY;
        return fromZ - (float) Math.sqrt(Math.max(0, len * len - dy * dy));
    }

    /** Тяга, смоделированная от шарнира в начале координат вдоль +z, — от (y0, z0) к (y1, z1). */
    private static void rod(PartialModel p, BlockState state, Direction facing, float y0, float z0, float y1, float z1, int light,
            PoseStack ms, VertexConsumer vc) {
        float phi = (float) Math.toDegrees(Math.atan2(-(y1 - y0), z1 - z0));
        SuperByteBuffer buf = oriented(p, state, facing);
        buf.translate(0, y0 / 16, z0 / 16).rotateXDegrees(phi);
        buf.light(light).renderInto(ms, vc);
    }

    private static void governor(SteamEngineBlockEntity be, BlockState state, Direction facing, float crankDeg, int light,
            PoseStack ms, VertexConsumer vc) {
        float spin = crankDeg * GOV_RATIO;
        double w = Math.abs(be.omega()) * GOV_RATIO;
        double cos = w > 1e-3 ? G / (w * w * GOV_ARM_M) : 1;
        float lift = (float) Math.toDegrees(Math.acos(Math.min(1, cos)));
        SuperByteBuffer head = oriented(BoilerClient.GOV_HEAD, state, facing);
        head.translate(GOV_X / 16, GOV_PIVOT_Y / 16, GOV_Z / 16).rotateYDegrees(spin);
        head.light(light).renderInto(ms, vc);
        for (int k = 0; k < 2; k++) {
            SuperByteBuffer arm = oriented(BoilerClient.GOV_ARM, state, facing);
            arm.translate(GOV_X / 16, GOV_PIVOT_Y / 16, GOV_Z / 16).rotateYDegrees(spin + 180 * k)
                    .translate(GOV_PIVOT_R / 16, 0, 0).rotateZDegrees(lift);
            arm.light(light).renderInto(ms, vc);
        }
    }

    private static SuperByteBuffer oriented(PartialModel p, BlockState state, Direction facing) {
        return CachedBuffers.partial(p, state).center().rotateYDegrees(-(facing.toYRot() + 180)).uncenter();
    }

    @Override
    public boolean shouldRenderOffScreen(SteamEngineBlockEntity be) {
        return true;
    }
}
