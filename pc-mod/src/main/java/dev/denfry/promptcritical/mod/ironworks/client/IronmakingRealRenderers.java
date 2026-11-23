package dev.denfry.promptcritical.mod.ironworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.client.whole.WholeModelHost;
import dev.denfry.promptcritical.mod.client.whole.WholeModelRenderer;
import dev.denfry.promptcritical.mod.client.whole.WholeModels;
import dev.denfry.promptcritical.mod.ironworks.CastHouseMachineRealBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.DustCatcherRealBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.GasExhausterRealBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.LadleRealBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.SinterStrandRealBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.SlagGranulatorRealBlockEntity;
import dev.denfry.promptcritical.mod.multiblock.Footprint;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Рендеры агломерации, газоочистки, литейного двора и ковша реального масштаба: цельная модель и подвижные части
 * по контрактам art-24 (шапки *.author.js в art/blockbench/ironmaking и art/blockbench/coke). Сдвиг partial → цельная
 * модель — в блоках; оси вращения — в px partial, как сохранены.
 */
final class IronmakingRealRenderers {
    static final String[] WHOLE = {"sinter_strand_real", "dust_catcher_real", "gas_exhauster_real", "byproduct_plant_cooler_real",
            "byproduct_plant_precipitator_real", "byproduct_plant_saturator_real", "byproduct_plant_scrubber_real",
            "cast_house_mud_gun_real", "cast_house_drill_real", "ladle_real", "slag_granulator_real"};
    static final PartialModel SPROCKET_TAIL = p("sinter_strand_real", "sprocket_tail"), SPROCKET_HEAD = p("sinter_strand_real", "sprocket_head"),
            SINTER_GLOW = p("sinter_strand_real", "glow"), CATCHER_VALVE = p("dust_catcher_real", "valve"),
            EXHAUSTER_ROTOR = p("gas_exhauster_real", "rotor"), GUN = p("cast_house_mud_gun_real", "gun"),
            BIT = p("cast_house_drill_real", "bit"), LADLE_BODY = p("ladle_real", "body"), LADLE_MELT = p("ladle_real", "melt"),
            GRANULATOR_HEAD = p("slag_granulator_real", "head"), GRANULATOR_GLOW = p("slag_granulator_real", "glow");
    /** Передача от вала к звёздочкам ленты и к головке элеватора (медленные машины). */
    private static final double STRAND_RATIO = 1 / 24.0, ELEVATOR_RPM = 8;

    static {
        for (String id : WHOLE) WholeModels.register(id);
    }

    private IronmakingRealRenderers() {}

    private static PartialModel p(String id, String name) {
        return PartialModel.of(PromptCritical.id("block/" + id + "/partial/" + name));
    }

    /** Загрузить класс до запекания моделей: цельные модели и partial-модели регистрируются в статике. */
    static void init() {
    }

    /** Угол вала Create в клетке порта без свёртки, градусы (формула валов Create); нет вала — 0. */
    static double shaftTurn(BlockEntity be, Direction facing, BlockPos port) {
        if (be.getLevel() == null) return 0;
        BlockPos at = be.getBlockPos().offset(port.rotate(Footprint.rotation(facing)));
        if (!(be.getLevel().getBlockEntity(at) instanceof KineticBlockEntity k)) return 0;
        return AnimationTickHolder.getRenderTime(be.getLevel()) * k.getSpeed() * 3 / 10;
    }

    /** Агломашина: звёздочки хвоста и головы на валу через редуктор, пламя горна и фронт спекания. */
    static final class Sinter extends WholeModelRenderer<SinterStrandRealBlockEntity> {
        @Override
        protected void parts(SinterStrandRealBlockEntity be, float pt, PoseStack ms, VertexConsumer vc, int light) {
            Direction f = be.wholeModelFacing();
            float a = (float) ((shaftTurn(be, f, SinterStrandRealBlockEntity.SHAFT_PORT) * STRAND_RATIO) % 360);
            part(SPROCKET_TAIL, be, f, 0, 0, 0).translate(0, 15 / 16.0, 0.5).rotateXDegrees(a).translate(0, -15 / 16.0, -0.5)
                    .light(light).renderInto(ms, vc);
            part(SPROCKET_HEAD, be, f, 0, 0, 7).translate(0, 15 / 16.0, 1.5).rotateXDegrees(a).translate(0, -15 / 16.0, -1.5)
                    .light(light).renderInto(ms, vc);
            if (be.getBlockState().getValue(BlockStateProperties.LIT))
                part(SINTER_GLOW, be, f, 0, 0, 1.5).light(LightTexture.FULL_BRIGHT).renderInto(ms, vc);
        }
    }

    /** Пылеуловитель: клапан выпуска пыли закрыт (выгрузку пыли симуляция не ведёт). */
    static final class Catcher extends WholeModelRenderer<DustCatcherRealBlockEntity> {
        @Override
        protected void parts(DustCatcherRealBlockEntity be, float pt, PoseStack ms, VertexConsumer vc, int light) {
            part(CATCHER_VALVE, be, be.wholeModelFacing(), 0, 1, 0).light(light).renderInto(ms, vc);
        }
    }

    /** Эксгаустер: ротор на валу Create, ось вдоль x через (y 24, z 8). */
    static final class Exhauster extends WholeModelRenderer<GasExhausterRealBlockEntity> {
        @Override
        protected void parts(GasExhausterRealBlockEntity be, float pt, PoseStack ms, VertexConsumer vc, int light) {
            Direction f = be.wholeModelFacing();
            float a = (float) (shaftTurn(be, f, GasExhausterRealBlockEntity.SHAFT_PORT) % 360);
            part(EXHAUSTER_ROTOR, be, f, 1, 0, 0).translate(0, 1.5, 0.5).rotateXDegrees(a).translate(0, -1.5, -0.5)
                    .light(light).renderInto(ms, vc);
        }
    }

    /** Машина литейного двора: пушка в положении у лётки или бур на оси летки. */
    static final class CastHouse extends WholeModelRenderer<CastHouseMachineRealBlockEntity> {
        @Override
        protected void parts(CastHouseMachineRealBlockEntity be, float pt, PoseStack ms, VertexConsumer vc, int light) {
            Direction f = be.wholeModelFacing();
            if (be.kind() == PcProperties.CastHouseKind.MUD_GUN) part(GUN, be, f, 0, 1, 1).light(light).renderInto(ms, vc);
            else part(BIT, be, f, 0, 0, 0).light(light).renderInto(ms, vc);
        }
    }

    /** Ковш: кожух и расплав (светится, пока ковш полон) на цапфах; стоит прямо, разливка — Shift + ПКМ. */
    static final class Ladle extends WholeModelRenderer<LadleRealBlockEntity> {
        @Override
        protected void parts(LadleRealBlockEntity be, float pt, PoseStack ms, VertexConsumer vc, int light) {
            Direction f = be.wholeModelFacing();
            part(LADLE_BODY, be, f, 0, 1, 0).light(light).renderInto(ms, vc);
            if (be.getBlockState().getValue(PcProperties.FILLED))
                part(LADLE_MELT, be, f, 0, 1, 0).light(LightTexture.FULL_BRIGHT).renderInto(ms, vc);
        }
    }

    /** Грануляция: головка элеватора крутится и светится шлак, пока идёт грануляция. */
    static final class Granulator extends WholeModelRenderer<SlagGranulatorRealBlockEntity> {
        @Override
        protected void parts(SlagGranulatorRealBlockEntity be, float pt, PoseStack ms, VertexConsumer vc, int light) {
            Direction f = be.wholeModelFacing();
            boolean running = be.getBlockState().getValue(PcProperties.RUNNING);
            float a = running && be.getLevel() != null
                    ? (float) ((AnimationTickHolder.getRenderTime(be.getLevel()) * ELEVATOR_RPM * 6 / 20) % 360) : 0;
            part(GRANULATOR_HEAD, be, f, 0, 2, 2).translate(0, 1.5, 1.25).rotateXDegrees(a).translate(0, -1.5, -1.25)
                    .light(light).renderInto(ms, vc);
            if (running) part(GRANULATOR_GLOW, be, f, 0, 0, 0).light(LightTexture.FULL_BRIGHT).renderInto(ms, vc);
        }
    }

    /** Цельная модель без подвижных частей (аппараты улавливания). */
    static <T extends BlockEntity & WholeModelHost> WholeModelRenderer<T> plain() {
        return new WholeModelRenderer<>();
    }
}
