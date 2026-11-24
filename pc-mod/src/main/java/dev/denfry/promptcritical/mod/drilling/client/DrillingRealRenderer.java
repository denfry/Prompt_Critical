package dev.denfry.promptcritical.mod.drilling.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.client.whole.WholeModelHost;
import dev.denfry.promptcritical.mod.client.whole.WholeModelRenderer;
import dev.denfry.promptcritical.mod.client.whole.WholeModels;
import dev.denfry.promptcritical.mod.drilling.DerrickRealBlockEntity;
import dev.denfry.promptcritical.mod.drilling.MudPumpBlock;
import dev.denfry.promptcritical.mod.drilling.MudPumpBlockEntity;
import dev.denfry.promptcritical.mod.drilling.RotaryTableBlockEntity;
import dev.denfry.promptcritical.mod.foundry.PortedKineticMachineBlock;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Буровая I2 реального масштаба (art/blockbench/drilling/*_real.author.js): цельная модель и подвижные части по
 * контрактам из шапок арта (px модели, фасад на север, от угла клетки контроллера; сдвиги хранения partial
 * возвращаются при отрисовке). У старых блоков тех же BE цельной модели нет, и рендер ничего не рисует.
 */
final class DrillingRealRenderer<T extends BlockEntity & WholeModelHost> extends WholeModelRenderer<T> {
    /** Как рисуются части одной машины. */
    @FunctionalInterface
    interface Parts {
        void draw(BlockEntity be, Direction f, BlockState s, float time, PoseStack ms, VertexConsumer vc, int light);
    }

    /** Подмена id цельной модели по соседям (секции вышки); {@code null} — модель блока. */
    @FunctionalInterface
    interface Variant {
        String of(BlockEntity be);
    }

    private static final PartialModel TURNTABLE = partial("rotary_table_real", "turntable"),
            KELLY_BUSHING = partial("rotary_table_real", "kelly_bushing"), KELLY = partial("rotary_table_real", "kelly"),
            SWIVEL = partial("rotary_table_real", "swivel"), TRAVELING_BLOCK = partial("rotary_table_real", "traveling_block"),
            DRUM = partial("drawworks_real", "drum"), BRAKE_LEVER = partial("drawworks_real", "brake_lever"),
            CATHEAD = partial("drawworks_real", "cathead"), PUMP_INPUT = partial("mud_pump_real", "input"),
            ROD_A = partial("mud_pump_real", "rod_a"), ROD_B = partial("mud_pump_real", "rod_b");

    static {
        for (String id : new String[] {"rotary_table_real", "drawworks_real", "mud_pump_real", "derrick_segment_real",
                "derrick_segment_real_bottom", "derrick_segment_real_top"})
            WholeModels.register(id);
    }

    /** Ведущая труба над ротором, метров (отрезков модели по 1 м): стоит на вкладыше, её держит вертлюг. */
    private static final int KELLY_M = 2;
    /** Ход поршня насоса, px модели (400 мм). */
    private static final float STROKE_PX = 6.4f;

    private final Parts parts;
    private final Variant variant;

    private DrillingRealRenderer(Parts parts, Variant variant) {
        this.parts = parts;
        this.variant = variant;
    }

    private static PartialModel partial(String id, String name) {
        return PartialModel.of(PromptCritical.id("block/" + id + "/partial/" + name));
    }

    /** Загрузить класс до запекания моделей: цельные модели и partial регистрируются в статике. */
    static void init() {
    }

    /** Угол, град, вала со скоростью {@code rpm} об/мин к моменту {@code time} тиков. */
    private static float angle(float time, double rpm) {
        return (float) ((time * rpm * 0.3) % 360);
    }

    private static boolean running(BlockState s) {
        return s.hasProperty(PcProperties.RUNNING) && s.getValue(PcProperties.RUNNING);
    }

    /**
     * Ротор: стол с вкладышем вращается вокруг оси скважины (8, 8) со скоростью стола; под вышкой реального масштаба
     * — квадратный вкладыш на столе (y 12), ведущая труба из двух метровых отрезков, вертлюг над ней и талевый блок с
     * крюком на штропах вертлюга.
     */
    static DrillingRealRenderer<RotaryTableBlockEntity> rotary() {
        return new DrillingRealRenderer<>((be, f, s, t, ms, vc, light) -> {
            float a = angle(t, PortedKineticMachineBlock.portSpeed(be) * RotaryTableBlockEntity.GEAR);
            spinY(TURNTABLE, be, f, 0, a, ms, vc, light);
            if (be.getLevel() == null || !be.getLevel().getBlockState(be.getBlockPos().above()).is(PcBlocks.DERRICK_SEGMENT_REAL.get()))
                return;
            spinY(KELLY_BUSHING, be, f, 12, a, ms, vc, light);
            for (int k = 0; k < KELLY_M; k++) spinY(KELLY, be, f, 17 + 16 * k, a, ms, vc, light);
            float top = 17 + 16 * KELLY_M;
            part(SWIVEL, be, f, 0, top / 16, 0).light(light).renderInto(ms, vc);
            // талевый блок хранится сдвинутым на −16 px по y (диапазон Java-модели)
            part(TRAVELING_BLOCK, be, f, 0, (top + 23 + 16) / 16, 0).light(light).renderInto(ms, vc);
        }, null);
    }

    private static void spinY(PartialModel p, BlockEntity be, Direction f, float yPx, float a, PoseStack ms, VertexConsumer vc, int light) {
        part(p, be, f, 0, yPx / 16, 0).translate(0.5f, 0, 0.5f).rotateYDegrees(a).translate(-0.5f, 0, -0.5f).light(light).renderInto(ms, vc);
    }

    /**
     * Лебёдка: пока идёт бурение — тормоз отпущен (рычаг −30° вокруг x через y 14, z −10), барабан медленно травит
     * канат (вокруг x через y 22, z 8), катушки вращаются с трансмиссией (вокруг x через y 38, z 22; хранятся сдвинутыми
     * на −24 x, −16 y).
     */
    static DrillingRealRenderer<dev.denfry.promptcritical.mod.drilling.DrawworksRealBlockEntity> drawworks() {
        return new DrillingRealRenderer<>((be, f, s, t, ms, vc, light) -> {
            boolean run = running(s);
            float drum = run ? angle(t, 2) : 0, cat = run ? angle(t, 40) : 0;
            part(DRUM, be, f, 0, 0, 0).translate(0, 22 / 16f, 8 / 16f).rotateXDegrees(drum).translate(0, -22 / 16f, -8 / 16f)
                    .light(light).renderInto(ms, vc);
            part(BRAKE_LEVER, be, f, 0, 0, 0).translate(0, 14 / 16f, -10 / 16f).rotateXDegrees(run ? -30 : 0)
                    .translate(0, -14 / 16f, 10 / 16f).light(light).renderInto(ms, vc);
            // сдвиг хранения возвращается до поворота: ось катушек (y 38, z 22) — в координатах модели
            part(CATHEAD, be, f, 0, 0, 0).translate(0, 38 / 16f, 22 / 16f).rotateXDegrees(cat)
                    .translate(0, -38 / 16f, -22 / 16f).translate(24 / 16f, 1, 0).light(light).renderInto(ms, vc);
        }, null);
    }

    /**
     * Буровой насос: приводной вал с муфтой вращается вокруг z через (x −26, y 12) со скоростью вала Create (хранится на
     * +16 px по x);
     * крейцкопфы со штоками двух цилиндров ходят вдоль x на 0…6,4 px от кривошипов, сдвинутых на 90°.
     */
    static DrillingRealRenderer<MudPumpBlockEntity> mudPump() {
        return new DrillingRealRenderer<>((be, f, s, t, ms, vc, light) -> {
            double rpm = MudPumpBlock.portSpeed(be);
            float in = angle(t, rpm);
            // хранится сдвинутым на +16 px по x (диапазон Java-модели): сдвиг снимается до поворота
            part(PUMP_INPUT, be, f, 0, 0, 0).translate(-26 / 16f, 12 / 16f, 0).rotateZDegrees(in).translate(26 / 16f, -12 / 16f, 0)
                    .translate(-1, 0, 0).light(light).renderInto(ms, vc);
            double crank = Math.toRadians(angle(t, Math.abs(rpm) * MudPumpBlockEntity.MAX_SPM / MudPumpBlockEntity.NOMINAL_RPM / 2));
            rod(ROD_A, be, f, crank, ms, vc, light);
            rod(ROD_B, be, f, crank + Math.PI / 2, ms, vc, light);
        }, null);
    }

    private static void rod(PartialModel p, BlockEntity be, Direction f, double crank, PoseStack ms, VertexConsumer vc, int light) {
        double x = STROKE_PX * (1 - Math.cos(crank)) / 2 / 16;
        part(p, be, f, x, 0, 0).light(light).renderInto(ms, vc);
    }

    /** Секция вышки: нижняя, средняя или верхняя по соседям; подвижных частей нет. */
    static DrillingRealRenderer<DerrickRealBlockEntity> derrick() {
        return new DrillingRealRenderer<>((be, f, s, t, ms, vc, light) -> {
        }, be -> be.getLevel() == null ? null : DerrickRealBlockEntity.variant(be.getLevel(), be.getBlockPos()));
    }

    @Override
    protected String modelId(T be) {
        String v = variant == null ? null : variant.of(be);
        return v != null ? v : super.modelId(be);
    }

    @Override
    protected void parts(T be, float pt, PoseStack ms, VertexConsumer vc, int light) {
        float time = be.getLevel() == null ? 0 : AnimationTickHolder.getRenderTime(be.getLevel());
        parts.draw(be, be.wholeModelFacing(), be.getBlockState(), time, ms, vc, light);
    }
}
