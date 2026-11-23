package dev.denfry.promptcritical.mod.mine.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.client.whole.WholeModelRenderer;
import dev.denfry.promptcritical.mod.mine.GearedDrainagePumpBlock;
import dev.denfry.promptcritical.mod.mine.MineDrainagePumpBlockEntity;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.core.Direction;

/**
 * Трёхплунжерный насос реального масштаба (mine_drainage_pump_geared): цельная модель и механизм по таблице размеров
 * (docs/research/mine_drainage_pump-dimensions.md). Шестерня сидит на входном валу и крутится с ним; колесо и
 * коленвал — вдвое медленнее и в обратную сторону. Кривошипы через 120°: палец k в (y, z) = (20 + 2,5 cos φ_k,
 * 24 + 2,5 sin φ_k) px, φ_k = θ + 120°·k; ползун плунжера y_k = 20 + 2,5 cos φ_k − √(8² − (2,5 sin φ_k)²), шатун 8 px.
 */
final class GearedPumpRenderer extends WholeModelRenderer<MineDrainagePumpBlockEntity> {
    static final PartialModel CRANK = partial("crank"), PINION = partial("pinion"), RAM = partial("ram"), CONROD = partial("conrod");
    private static final float AXIS_Y = 20, CRANK_R = 2.5f, ROD_L = 8;
    private static final float[] THROW_X = {14, 24, 34};
    /** Передача 2 : 1 от шестерни к колесу. */
    private static final float RATIO = 2;

    private static PartialModel partial(String name) {
        return PartialModel.of(PromptCritical.id("block/mine_drainage_pump_geared/partial/" + name));
    }

    /** Загрузить класс до запекания моделей: partial-модели регистрируются в статике. */
    static void init() {
    }

    /** Обороты входного вала с клетки-порта (контроллер вала не имеет), об/мин со знаком. */
    private static float inputRpm(MineDrainagePumpBlockEntity be, Direction f) {
        return be.getLevel() != null
                && be.getLevel().getBlockEntity(GearedDrainagePumpBlock.portPos(be.getBlockPos(), f)) instanceof KineticBlockEntity k
                ? k.getSpeed() : 0;
    }

    @Override
    protected void parts(MineDrainagePumpBlockEntity be, float pt, PoseStack ms, VertexConsumer vc, int light) {
        Direction f = be.wholeModelFacing();
        // как у Create: град = время · об/мин · 3/10
        float pinionDeg = (AnimationTickHolder.getRenderTime(be.getLevel()) * inputRpm(be, f) * 0.3f) % 720f;
        float thetaDeg = -pinionDeg / RATIO;
        double theta = Math.toRadians(thetaDeg);
        part(PINION, be, f, 2.0, 0, 1.0).translate(0, 0.5, 0.5).rotateXDegrees(pinionDeg).translate(0, -0.5, -0.5)
                .light(light).renderInto(ms, vc);
        part(CRANK, be, f, 1.25, 0.75, 1.0).translate(0, 0.5, 0.5).rotateXDegrees(thetaDeg).translate(0, -0.5, -0.5)
                .light(light).renderInto(ms, vc);
        for (int k = 0; k < 3; k++) {
            double phi = theta + Math.toRadians(120 * k);
            double dz = CRANK_R * Math.sin(phi);
            double y = AXIS_Y + CRANK_R * Math.cos(phi) - Math.sqrt(ROD_L * ROD_L - dz * dz);
            double tx = (THROW_X[k] - 8) / 16.0, ty = (y - 8) / 16.0;
            part(RAM, be, f, tx, ty, 1.0).light(light).renderInto(ms, vc);
            float rodDeg = (float) Math.toDegrees(Math.asin(dz / ROD_L));
            part(CONROD, be, f, tx, ty, 1.0).translate(0, 0.5, 0.5).rotateXDegrees(rodDeg).translate(0, -0.5, -0.5)
                    .light(light).renderInto(ms, vc);
        }
    }
}
