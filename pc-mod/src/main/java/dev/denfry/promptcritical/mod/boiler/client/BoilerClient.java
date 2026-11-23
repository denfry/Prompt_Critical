package dev.denfry.promptcritical.mod.boiler.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.denfry.promptcritical.mod.boiler.FireTubeBoilerBlockEntity;
import dev.denfry.promptcritical.mod.boiler.SteamEngineBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.boiler.BoilerBlockEntities;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.core.Direction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.joml.Matrix4f;

/** Клиент котельной: рендер подвижных частей и приборов. Только на клиенте. */
public final class BoilerClient {
    static final PartialModel FLYWHEEL = partial("steam_engine_cylinder/partial/flywheel");
    static final PartialModel CRANK = partial("steam_engine_cylinder/partial/crank");
    static final PartialModel CON_ROD = partial("steam_engine_cylinder/partial/con_rod");
    static final PartialModel PISTON_ROD = partial("steam_engine_cylinder/partial/piston_rod");
    static final PartialModel ECC_ROD = partial("steam_engine_cylinder/partial/ecc_rod");
    static final PartialModel VALVE_ROD = partial("steam_engine_cylinder/partial/valve_rod");
    static final PartialModel GOV_HEAD = partial("steam_engine_cylinder/partial/gov_head");
    static final PartialModel GOV_ARM = partial("steam_engine_cylinder/partial/gov_arm");
    static final PartialModel CHAIN = partial("grate_stoker/partial/chain");
    static final PartialModel LANCASHIRE_FIRE = partial("lancashire_boiler/partial/fire");

    static {
        // ланкаширский котёл реального масштаба: цельная модель, части — до запекания
        dev.denfry.promptcritical.mod.client.whole.WholeModels.register("lancashire_boiler");
        dev.denfry.promptcritical.mod.client.whole.WholeModels.register("mill_engine");
        dev.denfry.promptcritical.mod.client.whole.WholeModels.register("lancashire_stoker");
        MillEngineRenderer.init();
        LancashireStokerRenderer.init();
    }

    private BoilerClient() {
    }

    private static PartialModel partial(String path) {
        return PartialModel.of(PromptCritical.id("block/" + path));
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(BoilerClient::registerRenderers);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // котёл: у старой серии — свой рендер 3×3×6, у ланкаширского реального масштаба — цельная модель
        event.registerBlockEntityRenderer(BoilerBlockEntities.FIRE_TUBE_BOILER.get(), c -> new BlockEntityRenderer<FireTubeBoilerBlockEntity>() {
            private final FireTubeBoilerRenderer old = new FireTubeBoilerRenderer();
            private final dev.denfry.promptcritical.mod.client.whole.WholeModelRenderer<FireTubeBoilerBlockEntity> whole =
                    new dev.denfry.promptcritical.mod.client.whole.WholeModelRenderer<>() {
                        @Override
                        protected void parts(FireTubeBoilerBlockEntity be, float pt, PoseStack ms, VertexConsumer vc, int light) {
                            // прорези дверец и зольник светятся, пока на решётке горит
                            if (be.getBlockState().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT))
                                part(LANCASHIRE_FIRE, be, be.facing(), 0, 0, 0).light(net.minecraft.client.renderer.LightTexture.FULL_BRIGHT)
                                        .renderInto(ms, vc);
                        }
                    };

            @Override
            public void render(FireTubeBoilerBlockEntity be, float pt, PoseStack ms, net.minecraft.client.renderer.MultiBufferSource buf,
                    int light, int overlay) {
                if (be.wholeModelId() != null) whole.render(be, pt, ms, buf, light, overlay);
                else old.render(be, pt, ms, buf, light, overlay);
            }

            @Override
            public boolean shouldRenderOffScreen(FireTubeBoilerBlockEntity be) {
                return be.wholeModelId() != null || old.shouldRenderOffScreen(be);
            }

            @Override
            public int getViewDistance() {
                return dev.denfry.promptcritical.mod.client.whole.WholeModelRenderer.VIEW_DISTANCE;
            }

            @Override
            public net.minecraft.world.phys.AABB getRenderBoundingBox(FireTubeBoilerBlockEntity be) {
                return be.wholeModelId() != null ? whole.getRenderBoundingBox(be) : old.getRenderBoundingBox(be);
            }
        });
        // паровая машина: старая серия — свой механизм 3×3×5, mill_engine — цельная модель со своим механизмом
        event.registerBlockEntityRenderer(BoilerBlockEntities.STEAM_ENGINE.get(), c -> new BlockEntityRenderer<SteamEngineBlockEntity>() {
            private final SteamEngineRenderer old = new SteamEngineRenderer();
            private final MillEngineRenderer mill = new MillEngineRenderer();

            @Override
            public void render(SteamEngineBlockEntity be, float pt, PoseStack ms, net.minecraft.client.renderer.MultiBufferSource buf,
                    int light, int overlay) {
                if (be.wholeModelId() != null) mill.render(be, pt, ms, buf, light, overlay);
                else old.render(be, pt, ms, buf, light, overlay);
            }

            @Override
            public boolean shouldRenderOffScreen(SteamEngineBlockEntity be) {
                return true;
            }

            @Override
            public int getViewDistance() {
                return dev.denfry.promptcritical.mod.client.whole.WholeModelRenderer.VIEW_DISTANCE;
            }

            @Override
            public net.minecraft.world.phys.AABB getRenderBoundingBox(SteamEngineBlockEntity be) {
                return be.wholeModelId() != null ? mill.getRenderBoundingBox(be) : old.getRenderBoundingBox(be);
            }
        });
        event.registerBlockEntityRenderer(BoilerBlockEntities.GRATE_STOKER.get(), c -> new GrateStokerRenderer());
        event.registerBlockEntityRenderer(BoilerBlockEntities.LANCASHIRE_STOKER.get(), c -> new LancashireStokerRenderer());
        event.registerBlockEntityRenderer(BoilerBlockEntities.PRESSURE_GAUGE.get(), c -> new PressureGaugeRenderer());
    }

    /** Повернуть позу в систему модели (фасад на север) для блока с фасадом {@code facing}. */
    public static void toModel(PoseStack ms, Direction facing) {
        ms.translate(0.5, 0.5, 0.5);
        ms.mulPose(Axis.YP.rotationDegrees(-(facing.toYRot() + 180)));
        ms.translate(-0.5, -0.5, -0.5);
    }

    /**
     * Цветной прямоугольник в плоскости z = const (пиксели модели), видимый с обеих сторон.
     */
    static void rect(VertexConsumer vc, PoseStack ms, float x0, float y0, float x1, float y1, float z, int argb) {
        Matrix4f m = ms.last().pose();
        float a = (argb >>> 24) / 255f, r = (argb >> 16 & 255) / 255f, g = (argb >> 8 & 255) / 255f, b = (argb & 255) / 255f;
        float[][] v = {{x0, y0}, {x1, y0}, {x1, y1}, {x0, y1}};
        for (float[] p : v) vc.addVertex(m, p[0] / 16, p[1] / 16, z / 16).setColor(r, g, b, a);
        for (int i = 3; i >= 0; i--) vc.addVertex(m, v[i][0] / 16, v[i][1] / 16, z / 16).setColor(r, g, b, a);
    }

    /** Четырёхугольник по четырём точкам (x, y) в плоскости z, видимый с обеих сторон. */
    public static void quad(VertexConsumer vc, PoseStack ms, float[][] pts, float z, int argb) {
        Matrix4f m = ms.last().pose();
        float a = (argb >>> 24) / 255f, r = (argb >> 16 & 255) / 255f, g = (argb >> 8 & 255) / 255f, b = (argb & 255) / 255f;
        for (float[] p : pts) vc.addVertex(m, p[0] / 16, p[1] / 16, z / 16).setColor(r, g, b, a);
        for (int i = 3; i >= 0; i--) vc.addVertex(m, pts[i][0] / 16, pts[i][1] / 16, z / 16).setColor(r, g, b, a);
    }
}
