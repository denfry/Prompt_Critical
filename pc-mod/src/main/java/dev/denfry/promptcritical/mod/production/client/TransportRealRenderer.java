package dev.denfry.promptcritical.mod.production.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.client.whole.WholeModelHost;
import dev.denfry.promptcritical.mod.client.whole.WholeModelRenderer;
import dev.denfry.promptcritical.mod.client.whole.WholeModels;
import dev.denfry.promptcritical.mod.production.LineHeaterBlockEntity;
import dev.denfry.promptcritical.mod.production.MainlinePumpBlock;
import dev.denfry.promptcritical.mod.production.MainlinePumpBlockEntity;
import dev.denfry.promptcritical.mod.production.OilTankBlockEntity;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

/**
 * Транспорт нефти I2 реального масштаба (art/blockbench/oil_transport, oil_production/line_heater_real, storage): цельная
 * модель и подвижные части по контрактам из шапок арта (px модели, фасад на север, от угла клетки контроллера; сдвиги
 * хранения partial возвращаются при отрисовке).
 */
final class TransportRealRenderer<T extends BlockEntity & WholeModelHost> extends WholeModelRenderer<T> {
    @FunctionalInterface
    interface Parts<T> {
        void draw(T be, Direction f, float time, PoseStack ms, VertexConsumer vc, int light);
    }

    private static final PartialModel COUPLING = partial("mainline_pump_real", "coupling"), MOTOR_FAN = partial("mainline_pump_real", "motor_fan"),
            DAMPER = partial("line_heater_real", "stack_damper");
    private static final PartialModel[][] ROOF = new PartialModel[4][4];
    private static final PartialModel[] LADDER = new PartialModel[4];

    static {
        for (String id : new String[] {"mainline_pump_real", "line_heater_real", "line_heater_real_lit", "oil_tank_real", "oil_tank_floating_real"})
            WholeModels.register(id);
        for (int i = 0; i < 4; i++) {
            for (int k = 0; k < 4; k++) ROOF[i][k] = partial("oil_tank_floating_real", "floating_roof_" + i + "_" + k);
            LADDER[i] = partial("oil_tank_floating_real", "roof_ladder_" + i);
        }
    }

    /** Вал агрегата: ось x модели на y 14,4, z 8. */
    private static final float SHAFT_Y = 14.4f / 16, SHAFT_Z = 8 / 16f;
    /** Ось шибера дымовой трубы (−56, 100, 8) и его ход: открыт 0°, закрыт 70°. */
    private static final float DAMPER_X = -56 / 16f, DAMPER_Y = 100 / 16f, DAMPER_Z = 8 / 16f, DAMPER_CLOSED = 70;
    /** Понтон: верх настила на y 8 при пустом резервуаре, 186 при полном; шарнир катучей лестницы, длина лестницы, px. */
    private static final float DECK_EMPTY = 8, DECK_FULL = 186, HINGE_Y = 196, HINGE_Z = -68, LADDER_PX = 160;

    private final Parts<T> parts;
    private final boolean lit;

    private TransportRealRenderer(Parts<T> parts, boolean lit) {
        this.parts = parts;
        this.lit = lit;
    }

    private static PartialModel partial(String id, String name) {
        return PartialModel.of(PromptCritical.id("block/" + id + "/partial/" + name));
    }

    /** Загрузить класс до запекания моделей: цельные модели и partial регистрируются в статике. */
    static void init() {
    }

    /** Насос: муфта и вентилятор двигателя вращаются с валом Create вокруг оси вала; вентилятор хранится на −40 px по x. */
    static TransportRealRenderer<MainlinePumpBlockEntity> mainlinePump() {
        return new TransportRealRenderer<>((be, f, t, ms, vc, light) -> {
            float deg = (float) ((t * MainlinePumpBlock.portSpeed(be) * 0.3) % 360);
            spin(COUPLING, be, f, deg, 0, ms, vc, light);
            spin(MOTOR_FAN, be, f, deg, 40, ms, vc, light);
        }, false);
    }

    private static void spin(PartialModel p, BlockEntity be, Direction f, float deg, float storedDx, PoseStack ms, VertexConsumer vc, int light) {
        part(p, be, f, 0, 0, 0).translate(0, SHAFT_Y, SHAFT_Z).rotateXDegrees(deg).translate(0, -SHAFT_Y, -SHAFT_Z)
                .translate(storedDx / 16, 0, 0).light(light).renderInto(ms, vc);
    }

    /**
     * Подогреватель: горит горелка — шибер в трубе открыт (тяга), погашена — прикрыт на 70°, чтобы баня не остывала
     * через трубу. Шибер хранится сдвинутым на (+56, −88) px. Горящий вариант модели — {@code line_heater_real_lit}.
     */
    static TransportRealRenderer<LineHeaterBlockEntity> lineHeater() {
        return new TransportRealRenderer<>((be, f, t, ms, vc, light) -> {
            BlockState s = be.getBlockState();
            boolean on = s.hasProperty(BlockStateProperties.LIT) && s.getValue(BlockStateProperties.LIT);
            part(DAMPER, be, f, 0, 0, 0).translate(DAMPER_X, DAMPER_Y, DAMPER_Z).rotateXDegrees(on ? 0 : DAMPER_CLOSED)
                    .translate(-DAMPER_X, -DAMPER_Y, -DAMPER_Z).translate(-56 / 16f, 88 / 16f, 0).light(light).renderInto(ms, vc);
        }, true);
    }

    /**
     * Резервуар: у плавающей крыши понтон из 16 плиток лежит на продукте (уровень от BE), катучая лестница висит из
     * шарнира у кольца жёсткости и поворачивается к центру так, чтобы её конец лёг на понтон. У стационарной крыши
     * подвижных частей нет.
     */
    static TransportRealRenderer<OilTankBlockEntity> oilTank() {
        return new TransportRealRenderer<>((be, f, t, ms, vc, light) -> {
            if (!be.floatingRoof()) return;
            float fill = Math.clamp(be.viewFill(), 0f, 1f);
            float deck = DECK_EMPTY + (DECK_FULL - DECK_EMPTY) * fill;
            for (int i = 0; i < 4; i++)
                for (int k = 0; k < 4; k++)
                    part(ROOF[i][k], be, f, (48 * (i - 2) + 16) / 16.0, (deck - DECK_EMPTY) / 16.0, (48 * (k - 2) + 16) / 16.0)
                            .light(light).renderInto(ms, vc);
            // лестница длиной 160 px висит из шарнира; до понтона по вертикали h — угол от вертикали acos(h / L) к центру (+z)
            double h = HINGE_Y - deck;
            float tilt = (float) Math.toDegrees(Math.acos(Math.clamp(h / LADDER_PX, 0, 1)));
            for (int n = 0; n < 4; n++)
                part(LADDER[n], be, f, 0, 0, 0).translate(0, HINGE_Y / 16, HINGE_Z / 16).rotateXDegrees(-tilt)
                        .translate(0, -HINGE_Y / 16, -HINGE_Z / 16).translate(0, (164 - 40 * n) / 16f, -68 / 16f).light(light).renderInto(ms, vc);
        }, false);
    }

    @Override
    @Nullable
    protected String modelId(T be) {
        String id = be.wholeModelId();
        if (!lit || id == null) return id;
        BlockState s = be.getBlockState();
        return s.hasProperty(BlockStateProperties.LIT) && s.getValue(BlockStateProperties.LIT) ? id + "_lit" : id;
    }

    @Override
    protected void parts(T be, float pt, PoseStack ms, VertexConsumer vc, int light) {
        float time = be.getLevel() == null ? 0 : AnimationTickHolder.getRenderTime(be.getLevel());
        parts.draw(be, be.wholeModelFacing(), time, ms, vc, light);
    }
}
