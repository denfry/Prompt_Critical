package dev.denfry.promptcritical.mod.production.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.client.whole.WholeModelHost;
import dev.denfry.promptcritical.mod.client.whole.WholeModelRenderer;
import dev.denfry.promptcritical.mod.client.whole.WholeModels;
import dev.denfry.promptcritical.mod.production.PumpjackBlock;
import dev.denfry.promptcritical.mod.production.PumpjackBlockEntity;
import dev.denfry.promptcritical.mod.production.SeparatorBlockEntity;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Добыча I2 реального масштаба (art/blockbench/oil_production/*_real.author.js): цельная модель и подвижные части по
 * контрактам из шапок арта (px модели, фасад на север, от угла клетки контроллера; сдвиги хранения partial
 * возвращаются при отрисовке). У старых блоков тех же BE цельной модели нет, и рендер ничего не рисует.
 */
final class ProductionRealRenderer<T extends BlockEntity & WholeModelHost> extends WholeModelRenderer<T> {
    @FunctionalInterface
    interface Parts<T> {
        void draw(T be, Direction f, float time, PoseStack ms, VertexConsumer vc, int light);
    }

    private static final PartialModel BEAM_REAR = partial("pumpjack_real", "beam_rear"), BEAM_FRONT = partial("pumpjack_real", "beam_front"),
            HORSEHEAD = partial("pumpjack_real", "horsehead"), PITMAN = partial("pumpjack_real", "pitman"),
            CRANK = partial("pumpjack_real", "crank"), CARRIER = partial("pumpjack_real", "carrier"),
            POLISHED_ROD = partial("pumpjack_real", "polished_rod"), OIL_FLOAT = partial("three_phase_separator_real", "oil_level"),
            WATER_FLOAT = partial("three_phase_separator_real", "water_level"),
            OIL_STEM = partial("three_phase_separator_real", "oil_valve_stem"),
            WATER_STEM = partial("three_phase_separator_real", "water_valve_stem");

    static {
        for (String id : new String[] {"pumpjack_real", "three_phase_separator_real"}) WholeModels.register(id);
    }

    // качалка СК-8 (px модели): опора балансира, палец траверсы, ось кривошипов, радиус кривошипа, длина шатуна,
    // радиус дуги головки балансира
    private static final double PX = 24, PY = 80, EX = -14, EY = 72, KX = -16, KY = 28, CRANK_R = 13, PITMAN_L = 44, HEAD_R = 48;

    private final Parts<T> parts;

    private ProductionRealRenderer(Parts<T> parts) {
        this.parts = parts;
    }

    private static PartialModel partial(String id, String name) {
        return PartialModel.of(PromptCritical.id("block/" + id + "/partial/" + name));
    }

    /** Загрузить класс до запекания моделей: цельные модели и partial регистрируются в статике. */
    static void init() {
    }

    /**
     * Угол балансира, рад (против часовой — головка вверх), при угле кривошипа {@code crank}: палец траверсы на
     * заднем плече должен стоять на длине шатуна от пальца кривошипа. Четырёхзвенник решается делением отрезка.
     */
    static double beamAngle(double crank) {
        double cx = KX + CRANK_R * Math.cos(crank), cy = KY + CRANK_R * Math.sin(crank);
        double lo = -0.7, hi = 0.7;
        double flo = pitmanError(lo, cx, cy);
        for (int i = 0; i < 40; i++) {
            double mid = (lo + hi) / 2, fm = pitmanError(mid, cx, cy);
            if (Math.signum(fm) == Math.signum(flo)) {
                lo = mid;
                flo = fm;
            } else {
                hi = mid;
            }
        }
        return (lo + hi) / 2;
    }

    private static double[] equalizer(double phi) {
        double dx = EX - PX, dy = EY - PY, c = Math.cos(phi), s = Math.sin(phi);
        return new double[] {PX + dx * c - dy * s, PY + dx * s + dy * c};
    }

    private static double pitmanError(double phi, double cx, double cy) {
        double[] e = equalizer(phi);
        return Math.hypot(e[0] - cx, e[1] - cy) - PITMAN_L;
    }

    /**
     * Качалка: кривошипы вращаются с выходным валом редуктора (вал Create / {@link PumpjackBlockEntity#GEAR}), балансир
     * из трёх частей качается вокруг опоры, шатуны идут от пальца кривошипа к траверсе, подвеска с полированным штоком
     * ходит по вертикали на длину дуги головки.
     */
    static ProductionRealRenderer<PumpjackBlockEntity> pumpjack() {
        return new ProductionRealRenderer<>((be, f, t, ms, vc, light) -> {
            double rpm = Math.abs(PumpjackBlock.portSpeed(be)) / PumpjackBlockEntity.GEAR;
            double crank = Math.toRadians((t * rpm * 0.3) % 360);
            double phi = beamAngle(crank);
            float phiDeg = (float) Math.toDegrees(phi);
            beam(BEAM_REAR, be, f, phiDeg, 1, -64, ms, vc, light);
            beam(BEAM_FRONT, be, f, phiDeg, -28, -64, ms, vc, light);
            beam(HORSEHEAD, be, f, phiDeg, -48, -64, ms, vc, light);
            part(CRANK, be, f, 0, 0, 0).translate((float) (KX / 16), (float) (KY / 16), 0).rotateZDegrees((float) Math.toDegrees(crank))
                    .translate((float) (-KX / 16), (float) (-KY / 16), 0).translate(-22 / 16f, 8 / 16f, 0).light(light).renderInto(ms, vc);
            double cx = KX + CRANK_R * Math.cos(crank), cy = KY + CRANK_R * Math.sin(crank);
            double[] e = equalizer(phi);
            float pitman = (float) Math.toDegrees(Math.atan2(-(e[0] - cx), e[1] - cy));
            part(PITMAN, be, f, 0, 0, 0).translate((float) (cx / 16), (float) (cy / 16), 0).rotateZDegrees(pitman)
                    .translate(0, 14 / 16f, 0).light(light).renderInto(ms, vc);
            double dy = HEAD_R * phi;
            part(CARRIER, be, f, 64 / 16.0, (52 + dy) / 16, 0).light(light).renderInto(ms, vc);
            part(POLISHED_ROD, be, f, 64 / 16.0, (20 + dy) / 16, 0).light(light).renderInto(ms, vc);
        });
    }

    /** Часть балансира, хранится сдвинутой на (sx, sy) px; поворот вокруг опоры (24, 80). */
    private static void beam(PartialModel p, BlockEntity be, Direction f, float deg, float sx, float sy, PoseStack ms, VertexConsumer vc,
            int light) {
        part(p, be, f, 0, 0, 0).translate((float) (PX / 16), (float) (PY / 16), 0).rotateZDegrees(deg)
                .translate((float) (-PX / 16), (float) (-PY / 16), 0).translate(-sx / 16, -sy / 16, 0).light(light).renderInto(ms, vc);
    }

    /**
     * Сепаратор: поплавок нефтяного уровнемера ходит по y 14…36, поплавок раздела фаз — по 8…22; штоки регуляторов
     * уровня поднимаются до 1,5 px, когда уровень выше середины (клапан открывается и сбрасывает фазу). Штоки
     * хранятся сдвинутыми на −80 и −32 px по x.
     */
    static ProductionRealRenderer<SeparatorBlockEntity> separator() {
        return new ProductionRealRenderer<>((be, f, t, ms, vc, light) -> {
            float oil = Math.clamp(be.viewOil(), 0f, 1f), water = Math.clamp(be.viewWater(), 0f, 1f);
            part(OIL_FLOAT, be, f, 0, 22 * oil / 16.0, 0).light(light).renderInto(ms, vc);
            part(WATER_FLOAT, be, f, 0, 14 * water / 16.0, 0).light(light).renderInto(ms, vc);
            part(OIL_STEM, be, f, 80 / 16.0, lift(oil), 0).light(light).renderInto(ms, vc);
            part(WATER_STEM, be, f, 32 / 16.0, lift(water), 0).light(light).renderInto(ms, vc);
        });
    }

    /** Ход штока регулятора, блоков: закрыт до половины уровня, открыт полностью (1,5 px) к 90 %. */
    private static double lift(float level) {
        return Math.clamp((level - 0.5) / 0.4, 0, 1) * 1.5 / 16;
    }

    @Override
    protected void parts(T be, float pt, PoseStack ms, VertexConsumer vc, int light) {
        float time = be.getLevel() == null ? 0 : AnimationTickHolder.getRenderTime(be.getLevel());
        parts.draw(be, be.wholeModelFacing(), time, ms, vc, light);
    }
}
