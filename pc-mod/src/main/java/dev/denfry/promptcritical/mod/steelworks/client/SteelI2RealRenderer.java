package dev.denfry.promptcritical.mod.steelworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.client.whole.RealScaleBlockEntity;
import dev.denfry.promptcritical.mod.client.whole.WholeModelRenderer;
import dev.denfry.promptcritical.mod.client.whole.WholeModels;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Сталь и прокат I2 реального масштаба: цельная модель и подвижные части по контрактам арта (px модели, фасад на
 * север; сдвиги, с которыми partial сохранены, возвращаются при отрисовке). Старые блоки того же BE своего рендера
 * не имеют: у них {@code wholeModelId() == null}, и {@link WholeModelRenderer} ничего не рисует.
 */
final class SteelI2RealRenderer<T extends BlockEntity & RealScaleBlockEntity> extends WholeModelRenderer<T> {
    /** Как рисуются части одной машины. */
    @FunctionalInterface
    interface Parts {
        void draw(BlockEntity be, Direction f, BlockState s, float time, PoseStack ms, VertexConsumer vc, int light);
    }

    static final PartialModel EAF_ELECTRODES = partial("electric_arc_furnace_real", "electrodes"),
            EAF_GLOW = partial("electric_arc_furnace_real", "glow"), LADLE_LANCE = partial("ladle_station_real", "lance"),
            MOLD_MELT = partial("ingot_mold_real", "melt"), HT_DOOR = partial("heat_treatment_furnace_real", "door"),
            HT_GLOW = partial("heat_treatment_furnace_real", "glow"), WELDER_ROLLS = partial("pipe_welder_real", "forming_rolls"),
            WELDER_GLOW = partial("pipe_welder_real", "glow"), PIERCER_ROLL_L = partial("piercing_mill_real", "roll_l"),
            PIERCER_ROLL_R = partial("piercing_mill_real", "roll_r");

    static {
        for (String id : new String[] {"electric_arc_furnace_real", "ladle_station_real", "ingot_mold_real", "heat_treatment_furnace_real",
                "pipe_welder_real", "piercing_mill_real"})
            WholeModels.register(id);
    }

    private final Parts parts;

    SteelI2RealRenderer(Parts parts) {
        this.parts = parts;
    }

    private static PartialModel partial(String id, String name) {
        return PartialModel.of(PromptCritical.id("block/" + id + "/partial/" + name));
    }

    /** Загрузить класс до запекания моделей: цельные модели и partial регистрируются в статике. */
    static void init() {
    }

    private static boolean on(BlockState s, BooleanProperty p) {
        return s.hasProperty(p) && s.getValue(p);
    }

    /** Электроды опущены на 8 px, пока горит дуга; свечение окна и дуги. Сохранены сдвинутыми на 48 и 20 px вниз. */
    static Parts arcFurnace() {
        return (be, f, s, t, ms, vc, light) -> {
            boolean lit = on(s, BlockStateProperties.LIT);
            part(EAF_ELECTRODES, be, f, 0, 3 - (lit ? 0.5 : 0), 0).light(light).renderInto(ms, vc);
            if (lit) part(EAF_GLOW, be, f, 0, 20 / 16.0, 0).light(LightTexture.FULL_BRIGHT).renderInto(ms, vc);
        };
    }

    /** Аргонная фурма опускается на 24 px в ковш, пока идёт продувка; сохранена сдвинутой на 48 px вниз. */
    static Parts ladleStation() {
        return (be, f, s, t, ms, vc, light) -> part(LADLE_LANCE, be, f, 0, 3 - (on(s, PcProperties.RUNNING) ? 1.5 : 0), 0)
                .light(light).renderInto(ms, vc);
    }

    /** Зеркало металла, пока изложница полна. */
    static Parts ingotMold() {
        return (be, f, s, t, ms, vc, light) -> {
            if (on(s, PcProperties.FILLED)) part(MOLD_MELT, be, f, 0, 0, 0).light(LightTexture.FULL_BRIGHT).renderInto(ms, vc);
        };
    }

    /** Дверь поднята на 24 px, пока открыта; свечение, пока печь горит. */
    static Parts heatTreatment() {
        return (be, f, s, t, ms, vc, light) -> {
            part(HT_DOOR, be, f, 0, on(s, BlockStateProperties.OPEN) ? 1.5 : 0, 0).light(light).renderInto(ms, vc);
            if (on(s, BlockStateProperties.LIT)) part(HT_GLOW, be, f, 0, 0, 0).light(LightTexture.FULL_BRIGHT).renderInto(ms, vc);
        };
    }

    /** Формовочные клети (сохранены на 16 px севернее) и свечение шва (на 32 px), пока стан варит. */
    static Parts pipeWelder() {
        return (be, f, s, t, ms, vc, light) -> {
            part(WELDER_ROLLS, be, f, 0, 0, 1).light(light).renderInto(ms, vc);
            if (on(s, PcProperties.RUNNING)) part(WELDER_GLOW, be, f, 0, 0, 2).light(LightTexture.FULL_BRIGHT).renderInto(ms, vc);
        };
    }

    /**
     * Бочковидные валки крутятся вокруг z в одну сторону, пока идёт прошивка: оси (x −1, y 24) и (x 17, y 24) px;
     * сохранены на 24 px севернее.
     */
    static Parts piercingMill() {
        return (be, f, s, t, ms, vc, light) -> {
            float a = on(s, PcProperties.RUNNING) ? (t * 12) % 360 : 0;
            float y = 24 / 16f;
            part(PIERCER_ROLL_L, be, f, 0, 0, 1.5).translate(-1 / 16f, y, 0).rotateZDegrees(a).translate(1 / 16f, -y, 0)
                    .light(light).renderInto(ms, vc);
            part(PIERCER_ROLL_R, be, f, 0, 0, 1.5).translate(17 / 16f, y, 0).rotateZDegrees(a).translate(-17 / 16f, -y, 0)
                    .light(light).renderInto(ms, vc);
        };
    }

    @Override
    protected void parts(T be, float pt, PoseStack ms, VertexConsumer vc, int light) {
        float time = be.getLevel() == null ? 0 : AnimationTickHolder.getRenderTime(be.getLevel());
        parts.draw(be, be.wholeModelFacing(), be.getBlockState(), time, ms, vc, light);
    }
}
