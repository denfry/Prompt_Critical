package dev.denfry.promptcritical.mod.ironworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.client.whole.WholeModelRenderer;
import dev.denfry.promptcritical.mod.client.whole.WholeModels;
import dev.denfry.promptcritical.mod.ironworks.BlastBlowerEngineBlock;
import dev.denfry.promptcritical.mod.ironworks.BlastBlowerEngineBlockEntity;
import dev.denfry.promptcritical.mod.multiblock.Footprint;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.core.Direction;

/**
 * Воздуходувная машина реального масштаба (art/blockbench/ironmaking/blast_blower_engine.author.js): цельная модель,
 * поверх — шестерня на валу Create, коленвал с маховиком (втрое медленнее, через коническую пару), ползун со штоком и
 * поршнем и шатун. θ — угол кривошипа, 0 — палец к цилиндру (+x модели).
 */
final class BlastBlowerEngineRenderer extends WholeModelRenderer<BlastBlowerEngineBlockEntity> {
    static final PartialModel FLYWHEEL = partial("flywheel"), PINION = partial("pinion"), CROSSHEAD = partial("crosshead"),
            CONROD = partial("conrod");
    /** Ось коленвала по x, радиус кривошипа, длина шатуна, px модели; передаточное число конической пары. */
    private static final double CRANK_X = 28, CRANK_R = 8, ROD = 40, BEVEL = 3;

    static {
        WholeModels.register("blast_blower_engine");
    }

    private static PartialModel partial(String name) {
        return PartialModel.of(PromptCritical.id("block/blast_blower_engine/partial/" + name));
    }

    /** Загрузить класс до запекания моделей: цельная модель и partial-модели регистрируются в статике. */
    static void init() {
    }

    @Override
    protected void parts(BlastBlowerEngineBlockEntity be, float pt, PoseStack ms, VertexConsumer vc, int light) {
        Direction f = be.wholeModelFacing();
        double turned = pinionTurn(be);
        float pinion = (float) (turned % 360);
        // крутим от несвёрнутого угла: шестерня делает три оборота на один оборот кривошипа
        double theta = Math.toRadians((-turned / BEVEL) % 360);
        // шестерня: ось вдоль x через partial (·, 8, 8), сдвиг (0, 16, 0) px
        part(PINION, be, f, 0, 1, 0).translate(0, 0.5, 0.5).rotateXDegrees(pinion).translate(0, -0.5, -0.5).light(light).renderInto(ms, vc);
        // коленвал с маховиком: ось вдоль z через partial (8, 8, ·), сдвиг (20, 16, 0) px
        part(FLYWHEEL, be, f, 20 / 16.0, 1, 0).translate(0.5, 0.5, 0).rotateZDegrees((float) Math.toDegrees(theta))
                .translate(-0.5, -0.5, 0).light(light).renderInto(ms, vc);
        double sy = CRANK_R * Math.sin(theta);
        double xc = CRANK_X + CRANK_R * Math.cos(theta) + Math.sqrt(ROD * ROD - sy * sy);
        part(CROSSHEAD, be, f, (xc + 4) / 16, 1, 0).light(light).renderInto(ms, vc);
        // шатун: малая головка в partial (28, 8, 16) на пальце ползуна, поворот вокруг z через неё
        float rod = (float) -Math.toDegrees(Math.asin(sy / ROD));
        part(CONROD, be, f, (xc - 28) / 16, 1, 0).translate(28 / 16.0, 0.5, 1).rotateZDegrees(rod).translate(-28 / 16.0, -0.5, -1)
                .light(light).renderInto(ms, vc);
    }

    /** Угол шестерни без свёртки, градусы: по скорости клетки порта (её синхронизирует Create), формулой валов Create. */
    private static double pinionTurn(BlastBlowerEngineBlockEntity be) {
        if (be.getLevel() == null) return 0;
        var port = be.getBlockPos().offset(BlastBlowerEngineBlock.SHAFT_PORT.rotate(Footprint.rotation(be.wholeModelFacing())));
        if (!(be.getLevel().getBlockEntity(port) instanceof KineticBlockEntity k)) return 0;
        double time = AnimationTickHolder.getRenderTime(be.getLevel());
        return time * k.getSpeed() * 3 / 10;
    }
}
