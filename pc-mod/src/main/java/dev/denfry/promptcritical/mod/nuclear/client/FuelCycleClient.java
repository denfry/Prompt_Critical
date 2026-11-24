package dev.denfry.promptcritical.mod.nuclear.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.client.whole.WholeModelHost;
import dev.denfry.promptcritical.mod.client.whole.WholeModelRenderer;
import dev.denfry.promptcritical.mod.client.whole.WholeModels;
import dev.denfry.promptcritical.mod.nuclear.NuclearBlockEntities;
import dev.denfry.promptcritical.mod.nuclear.SinteringFurnaceBlockEntity;
import dev.denfry.promptcritical.mod.uranium.UraniumBlockEntities;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Клиент топливного цикла реального масштаба (art/blockbench/fuelcycle/fuelcycle.author.js — шапка и есть спецификация
 * частей): ротор вскрытой центрифуги, муфта питателя фторатора, колпак вентиля 30B, дверь автоклава, карусель пресса,
 * толкатель и двери печи спекания, твэл и сварочная головка, поворотный стол, таль и каретка стенда с ТВС в гнезде,
 * головка ТВС. Сдвиг хранения части (−dx, −dy, −dz px) снимается при отрисовке. Старые блоки рисуются моделью блока.
 */
public final class FuelCycleClient {
    private static final PartialModel ROTOR = partial("gas_centrifuge_real/partial/rotor");
    private static final PartialModel FEEDER = partial("fluorinator_real/partial/feeder_coupling");
    private static final PartialModel VALVE_CAP = partial("uf6_cylinder_30b_real/partial/valve_cap");
    private static final PartialModel AUTOCLAVE_DOOR = partial("uf6_autoclave_real/partial/door");
    private static final PartialModel TURRET = partial("pellet_press_real/partial/turret");
    private static final PartialModel UPPER = partial("pellet_press_real/partial/upper_punches");
    private static final PartialModel LOWER = partial("pellet_press_real/partial/lower_punches");
    private static final PartialModel PUSHER = partial("sintering_furnace_real/partial/pusher_ram");
    private static final PartialModel ENTRY_DOOR = partial("sintering_furnace_real/partial/entry_door");
    private static final PartialModel EXIT_DOOR = partial("sintering_furnace_real/partial/exit_door");
    private static final PartialModel ROD = partial("fuel_rod_welder_real/partial/rod");
    private static final PartialModel WELD_HEAD = partial("fuel_rod_welder_real/partial/weld_head");
    private static final PartialModel TURNTABLE = partial("fuel_assembly_stand_real/partial/turntable");
    private static final PartialModel HOOK = partial("fuel_assembly_stand_real/partial/hoist_hook");
    private static final PartialModel GAUGE = partial("fuel_assembly_stand_real/partial/gauge_carriage");
    private static final PartialModel HEAD = partial("vver440_fuel_assembly_real/partial/head");
    private static final String ASSEMBLY = "vver440_fuel_assembly_real";

    static {
        for (String id : new String[] {"gas_centrifuge_real", "gas_centrifuge_real_open", "centrifuge_cascade_section_real", "fluorinator_real",
                "uf6_cylinder_30b_real", "uf6_autoclave_real", "uf6_autoclave_real_on", "pellet_press_real", "sintering_furnace_real",
                "sintering_furnace_real_on", "fuel_rod_welder_real", "fuel_assembly_stand_real", ASSEMBLY})
            WholeModels.register(id);
    }

    private FuelCycleClient() {
    }

    private static PartialModel partial(String path) {
        return PartialModel.of(PromptCritical.id("block/" + path));
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(FuelCycleClient::registerRenderers);
    }

    private static boolean is(BlockState s, BooleanProperty p) {
        return s.hasProperty(p) && s.getValue(p);
    }

    private static float time(BlockEntity be) {
        return AnimationTickHolder.getRenderTime(be.getLevel());
    }

    /** Угол вращения в градусах при {@code rpm} об/мин (20 тиков в секунду). */
    private static float deg(BlockEntity be, double rpm) {
        return (float) ((time(be) * rpm * 0.3) % 360);
    }

    /** Цельная модель с вариантом и движущимися частями, заданными функцией. */
    private static final class Moving<T extends BlockEntity & WholeModelHost> extends WholeModelRenderer<T> {
        interface Parts<T> {
            void draw(T be, Direction f, float pt, PoseStack ms, VertexConsumer vc, int light);
        }

        private final java.util.function.Function<T, String> variant;
        private final Parts<T> parts;

        Moving(java.util.function.Function<T, String> variant, Parts<T> parts) {
            this.variant = variant;
            this.parts = parts;
        }

        @Override
        @Nullable
        protected String modelId(T be) {
            String id = be.wholeModelId();
            return id == null ? null : id + variant.apply(be);
        }

        @Override
        protected void parts(T be, float pt, PoseStack ms, VertexConsumer vc, int light) {
            parts.draw(be, be.wholeModelFacing(), pt, ms, vc, light);
        }

        static net.createmod.catnip.render.SuperByteBuffer at(PartialModel p, BlockEntity be, Direction f, double dxPx, double dyPx, double dzPx) {
            return part(p, be, f, dxPx / 16, dyPx / 16, dzPx / 16);
        }
    }

    private static <T extends BlockEntity & WholeModelHost> Moving<T> moving(java.util.function.Function<T, String> variant, Moving.Parts<T> parts) {
        return new Moving<>(variant, parts);
    }

    /** Чередование 0..1..0 с периодом {@code ticks}. */
    private static float wave(BlockEntity be, float ticks) {
        return (float) (0.5 - 0.5 * Math.cos(time(be) * 2 * Math.PI / ticks));
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // центрифуга: вскрытый корпус показывает ротор (стоит); секция каскада — одна модель, роторы закрыты
        event.registerBlockEntityRenderer(NuclearBlockEntities.CENTRIFUGE_REAL.get(), c -> moving(
                be -> is(be.getBlockState(), BlockStateProperties.OPEN) ? "_open" : "",
                (be, f, pt, ms, vc, light) -> {
                    if (is(be.getBlockState(), BlockStateProperties.OPEN)) Moving.at(ROTOR, be, f, 0, 0, 0).light(light).renderInto(ms, vc);
                }));
        // фторатор: муфта шнекового питателя вращается вокруг x через (y 88, z 8), хранится на 64 px ниже
        event.registerBlockEntityRenderer(UraniumBlockEntities.FLUORINATION.get(), c -> moving(be -> "", (be, f, pt, ms, vc, light) -> {
            float a = is(be.getBlockState(), PcProperties.RUNNING) ? deg(be, 30) : 0;
            Moving.at(FEEDER, be, f, 0, 64, 0).translate(0, 88 / 16f, 8 / 16f).rotateXDegrees(a).translate(0, -88 / 16f, -8 / 16f)
                    .light(light).renderInto(ms, vc);
        }));
        // баллон 30B и ТВС: колпак вентиля и головка на своих местах
        event.registerBlockEntityRenderer(NuclearBlockEntities.PLACED_ITEM.get(), c -> moving(be -> "", (be, f, pt, ms, vc, light) -> {
            if (ASSEMBLY.equals(be.wholeModelId())) Moving.at(HEAD, be, f, 0, 32, 0).light(light).renderInto(ms, vc);
            else Moving.at(VALVE_CAP, be, f, 16, 0, 0).light(light).renderInto(ms, vc);
        }));
        // автоклав: _on — прогрет (лампа шкафа); дверь открыта на 100° (петля x −28, z −6), пока баллона нет
        event.registerBlockEntityRenderer(NuclearBlockEntities.UF6_AUTOCLAVE.get(), c -> moving(
                be -> is(be.getBlockState(), BlockStateProperties.LIT) ? "_on" : "",
                (be, f, pt, ms, vc, light) -> {
                    float a = is(be.getBlockState(), BlockStateProperties.OCCUPIED) ? 0 : -100;
                    Moving.at(AUTOCLAVE_DOOR, be, f, -16, 0, 0).translate(-28 / 16f, 0, -6 / 16f).rotateYDegrees(a)
                            .translate(28 / 16f, 0, 6 / 16f).light(light).renderInto(ms, vc);
                }));
        // пресс: карусель и пуансоны вращаются вокруг y через (8, 8), пуансоны ходят на кулачках на 2 px
        event.registerBlockEntityRenderer(NuclearBlockEntities.PELLET_PRESS.get(), c -> moving(be -> "", (be, f, pt, ms, vc, light) -> {
            boolean run = is(be.getBlockState(), PcProperties.RUNNING);
            float a = run ? deg(be, 20) : 0, bob = run ? 2 * wave(be, 6) : 0;
            Moving.at(TURRET, be, f, 0, 0, 0).translate(0.5f, 0, 0.5f).rotateYDegrees(a).translate(-0.5f, 0, -0.5f).light(light).renderInto(ms, vc);
            Moving.at(UPPER, be, f, 0, bob, 0).translate(0.5f, 0, 0.5f).rotateYDegrees(a).translate(-0.5f, 0, -0.5f).light(light).renderInto(ms, vc);
            Moving.at(LOWER, be, f, 0, -bob, 0).translate(0.5f, 0, 0.5f).rotateYDegrees(a).translate(-0.5f, 0, -0.5f).light(light).renderInto(ms, vc);
        }));
        // печь спекания: _on — смотровые окна и огненные завесы; толкатель ходит на 16 px, двери приподнимаются на толчок
        event.registerBlockEntityRenderer(NuclearBlockEntities.SINTERING_FURNACE.get(), c -> moving(
                be -> is(be.getBlockState(), BlockStateProperties.LIT) ? "_on" : "",
                (be, f, pt, ms, vc, light) -> {
                    float push = is(be.getBlockState(), BlockStateProperties.LIT) ? wave(be, SinteringFurnaceBlockEntity.PUSH_TICKS) : 0;
                    Moving.at(PUSHER, be, f, -56 + 16 * push, 0, 0).light(light).renderInto(ms, vc);
                    Moving.at(ENTRY_DOOR, be, f, -48, 8 * push, 0).light(light).renderInto(ms, vc);
                    Moving.at(EXIT_DOOR, be, f, 64, 8 * push, 0).light(light).renderInto(ms, vc);
                }));
        // сварка: твэл въезжает в камеру (x −8..0), цанга вращается вокруг x через (y 14,9, z 8)
        event.registerBlockEntityRenderer(NuclearBlockEntities.ROD_LOADING_STATION.get(), c -> moving(be -> "", (be, f, pt, ms, vc, light) -> {
            boolean on = is(be.getBlockState(), BlockStateProperties.LIT);
            float slide = on ? -8 + 8 * wave(be, 60) : -8;
            Moving.at(ROD, be, f, -14 + slide, 0, 0).light(light).renderInto(ms, vc);
            float a = on ? deg(be, 40) : 0;
            Moving.at(WELD_HEAD, be, f, 0, 0, 0).translate(0, 14.9f / 16, 0.5f).rotateXDegrees(a).translate(0, -14.9f / 16, -0.5f)
                    .light(light).renderInto(ms, vc);
        }));
        // стенд: пока идёт сборка — ТВС в гнезде (y 6), стол медленно поворачивается, таль и каретка калибра ходят
        event.registerBlockEntityRenderer(NuclearBlockEntities.ASSEMBLY_JIG.get(), c -> moving(be -> "", (be, f, pt, ms, vc, light) -> {
            boolean on = is(be.getBlockState(), BlockStateProperties.LIT);
            float a = on ? deg(be, 2) : 0;
            Moving.at(TURNTABLE, be, f, 0, 0, 0).translate(0.5f, 0, 0.5f).rotateYDegrees(a).translate(-0.5f, 0, -0.5f).light(light).renderInto(ms, vc);
            Moving.at(HOOK, be, f, 0, 48 - (on ? 24 * wave(be, 200) : 0), 0).light(light).renderInto(ms, vc);
            Moving.at(GAUGE, be, f, 0, on ? 48 * wave(be, 160) : 0, 0).light(light).renderInto(ms, vc);
            if (on) assembly(be, f, a, ms, vc, light);
        }));
    }

    /** ТВС ВВЭР-440 в гнезде стенда: та же цельная модель, на 6 px выше, поворачивается со столом. */
    private static void assembly(BlockEntity be, Direction f, float deg, PoseStack ms, VertexConsumer vc, int light) {
        WholeModels.Model m = WholeModels.get(ASSEMBLY);
        if (m == null) return;
        for (WholeModels.Chunk c : m.chunks())
            CachedBuffers.partial(c.partial(), be.getBlockState()).center().rotateYDegrees(-(f.toYRot() + 180) + deg).uncenter()
                    .translate(c.x(), c.y() + 6 / 16f, c.z()).light(light).renderInto(ms, vc);
        Moving.at(HEAD, be, f, 0, 32 + 6, 0).light(light).renderInto(ms, vc);
    }
}
