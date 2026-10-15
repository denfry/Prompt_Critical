package dev.denfry.promptcritical.mod.boiler.client;

import com.mojang.blaze3d.vertex.PoseStack;
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
    static final PartialModel CHAIN = partial("grate_stoker/partial/chain");

    private BoilerClient() {
    }

    private static PartialModel partial(String path) {
        return PartialModel.of(PromptCritical.id("block/" + path));
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(BoilerClient::registerRenderers);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BoilerBlockEntities.FIRE_TUBE_BOILER.get(), c -> new FireTubeBoilerRenderer());
        event.registerBlockEntityRenderer(BoilerBlockEntities.STEAM_ENGINE.get(), c -> new SteamEngineRenderer());
        event.registerBlockEntityRenderer(BoilerBlockEntities.GRATE_STOKER.get(), c -> new GrateStokerRenderer());
        event.registerBlockEntityRenderer(BoilerBlockEntities.PRESSURE_GAUGE.get(), c -> new PressureGaugeRenderer());
    }

    /** Повернуть позу в систему модели (фасад на север) для блока с фасадом {@code facing}. */
    static void toModel(PoseStack ms, Direction facing) {
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
    static void quad(VertexConsumer vc, PoseStack ms, float[][] pts, float z, int argb) {
        Matrix4f m = ms.last().pose();
        float a = (argb >>> 24) / 255f, r = (argb >> 16 & 255) / 255f, g = (argb >> 8 & 255) / 255f, b = (argb & 255) / 255f;
        for (float[] p : pts) vc.addVertex(m, p[0] / 16, p[1] / 16, z / 16).setColor(r, g, b, a);
        for (int i = 3; i >= 0; i--) vc.addVertex(m, pts[i][0] / 16, pts[i][1] / 16, z / 16).setColor(r, g, b, a);
    }
}
