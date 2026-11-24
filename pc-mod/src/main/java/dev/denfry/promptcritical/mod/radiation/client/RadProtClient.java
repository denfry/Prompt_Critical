package dev.denfry.promptcritical.mod.radiation.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.client.whole.SpinWholeRenderer;
import dev.denfry.promptcritical.mod.client.whole.WholeModelRenderer;
import dev.denfry.promptcritical.mod.client.whole.WholeModels;
import dev.denfry.promptcritical.mod.radiation.HotCellBlockEntity;
import dev.denfry.promptcritical.mod.radiation.RadiationBlockEntities;
import dev.denfry.promptcritical.mod.radiation.SpentFuelCaskBlockEntity;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Клиент радзащиты реального масштаба (art/blockbench/radprot/radprot.author.js — шапка и есть спецификация частей):
 * цельные модели с вариантами, маячки мониторов, манипуляторы и шибер горячей камеры, защитная плита контейнера,
 * колёсные пары тележки, дверь модуля хранения. Сдвиг хранения части снимается при отрисовке. Только на клиенте.
 */
public final class RadProtClient {
    private static final PartialModel PORTAL_LAMP = partial("portal_monitor_real/partial/status_lamp");
    private static final PartialModel HAND_FOOT_LAMP = partial("hand_foot_monitor_real/partial/status_lamp");
    private static final PartialModel HANDLE_L = partial("hot_cell_real/partial/master_handle_l");
    private static final PartialModel HANDLE_R = partial("hot_cell_real/partial/master_handle_r");
    private static final PartialModel TONG_L = partial("hot_cell_real/partial/slave_tong_l");
    private static final PartialModel TONG_R = partial("hot_cell_real/partial/slave_tong_r");
    private static final PartialModel GATE = partial("hot_cell_real/partial/transfer_gate");
    private static final PartialModel CASK_PLATE = partial("spent_fuel_cask_real/partial/protective_plate");
    private static final PartialModel DOOR = partial("dry_storage_module_real/partial/door");
    /** Колёсные пары: ось вдоль z через (x, 5) px, хранятся сдвинутыми на +56, +44, −44, −56 px по x. */
    private static final float[][] WHEELSETS = {{-48, 56}, {-36, 44}, {52, -44}, {64, -56}};
    private static final PartialModel[] WHEELS = new PartialModel[4];

    static {
        for (int i = 0; i < WHEELS.length; i++) WHEELS[i] = partial("cask_transport_cart_real/partial/wheelset_" + i);
        for (String id : new String[] {"portal_monitor_real", "portal_monitor_real_alarm", "hand_foot_monitor_real",
                "hand_foot_monitor_real_alarm", "hot_cell_real", "hot_cell_real_on", "spent_fuel_cask_real", "cask_transport_cart_real",
                "cask_transport_cart_real_loaded", "dry_storage_module_real"})
            WholeModels.register(id);
    }

    private RadProtClient() {
    }

    private static PartialModel partial(String path) {
        return PartialModel.of(PromptCritical.id("block/" + path));
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(RadProtClient::registerRenderers);
    }

    private static boolean is(BlockState s, net.minecraft.world.level.block.state.properties.BooleanProperty p) {
        return s.hasProperty(p) && s.getValue(p);
    }

    /** Маячок: 90 об/мин, пока тревога. */
    private static double beacon(BlockEntity be) {
        return is(be.getBlockState(), BlockStateProperties.POWERED) ? 90 : 0;
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // мониторы: в тревоге — вариант _alarm (красное табло и маячок) и вращающийся отражатель маячка; старые
        // одноблочные мониторы рисуются моделью блока
        event.registerBlockEntityRenderer(RadiationBlockEntities.PORTAL_MONITOR.get(), c -> new SpinWholeRenderer<>(null)
                .variant("_alarm", s -> is(s, BlockStateProperties.POWERED))
                .spin(PORTAL_LAMP, Direction.Axis.Y, 8, 8, 0, 24, 0, RadProtClient::beacon));
        event.registerBlockEntityRenderer(RadiationBlockEntities.HAND_FOOT_MONITOR.get(), c -> new SpinWholeRenderer<>(null)
                .variant("_alarm", s -> is(s, BlockStateProperties.POWERED))
                .spin(HAND_FOOT_LAMP, Direction.Axis.Y, 8, 12, 0, 0, 0, RadProtClient::beacon));
        event.registerBlockEntityRenderer(RadiationBlockEntities.HOT_CELL.get(), c -> new HotCellRenderer());
        event.registerBlockEntityRenderer(RadiationBlockEntities.SPENT_FUEL_CASK.get(), c -> new CaskRenderer());
        // тележка стоит, пока её не везёт контрапция: колёсные пары — на своих местах; с контейнером — вариант _loaded
        SpinWholeRendererFactory cart = () -> {
            SpinWholeRenderer<dev.denfry.promptcritical.mod.radiation.CaskCartBlockEntity> r = new SpinWholeRenderer<>(null);
            r.variant("_loaded", s -> is(s, BlockStateProperties.OCCUPIED));
            for (int i = 0; i < WHEELS.length; i++)
                r.spin(WHEELS[i], Direction.Axis.Z, WHEELSETS[i][0], 5, -WHEELSETS[i][1], 0, 0, be -> 0);
            return r;
        };
        event.registerBlockEntityRenderer(RadiationBlockEntities.CASK_CART.get(), c -> cart.make());
        // дверь модуля закрыта всегда: пенал задвигают и вытягивают через неё за кадром
        event.registerBlockEntityRenderer(RadiationBlockEntities.DRY_STORAGE_MODULE.get(), c -> new SpinWholeRenderer<>(null)
                .spin(DOOR, Direction.Axis.Y, 8, 8, 0, 16, 0, be -> 0));
    }

    private interface SpinWholeRendererFactory {
        SpinWholeRenderer<dev.denfry.promptcritical.mod.radiation.CaskCartBlockEntity> make();
    }

    /**
     * Горячая камера: с питанием — вариант {@code _on} (лампы камеры и окно светятся). В работе рукояти копирующих
     * манипуляторов качаются вокруг x через (x, 23, −6), схваты — через (x, 21, 20,5), шибер шлюза поднимается на
     * 14 px и опускается (хранится сдвинутым на −16 px по x).
     */
    static final class HotCellRenderer extends WholeModelRenderer<HotCellBlockEntity> {
        @Override
        @Nullable
        protected String modelId(HotCellBlockEntity be) {
            String id = be.wholeModelId();
            return id != null && is(be.getBlockState(), BlockStateProperties.LIT) ? id + "_on" : id;
        }

        @Override
        protected void parts(HotCellBlockEntity be, float pt, PoseStack ms, VertexConsumer vc, int light) {
            Direction f = be.wholeModelFacing();
            float phase = be.workPhase(pt);
            float swing = phase < 0 ? 0 : (float) Math.sin(phase * Math.PI * 4) * 25;
            float lift = phase < 0 ? 0 : (float) Math.sin(phase * Math.PI) * 14 / 16f;
            float[][] arms = {{-12, 1}, {28, -1}};
            PartialModel[] handles = {HANDLE_L, HANDLE_R}, tongs = {TONG_L, TONG_R};
            for (int i = 0; i < 2; i++) {
                float a = swing * arms[i][1];
                part(handles[i], be, f, 0, 0, 0).translate(0, 23 / 16f, -6 / 16f).rotateXDegrees(a).translate(0, -23 / 16f, 6 / 16f)
                        .light(light).renderInto(ms, vc);
                // схват повторяет рукоять (копирующий манипулятор), зеркально через стену
                part(tongs[i], be, f, 0, 0, 0).translate(0, 21 / 16f, 20.5f / 16f).rotateXDegrees(-a).translate(0, -21 / 16f, -20.5f / 16f)
                        .light(light).renderInto(ms, vc);
            }
            part(GATE, be, f, 16 / 16f, lift, 0).light(light).renderInto(ms, vc);
        }
    }

    /** Контейнер: защитная плита на крышке, пока он закрыт; открытый — плиту снял кран. Над рёбрами — марево (BE). */
    static final class CaskRenderer extends WholeModelRenderer<SpentFuelCaskBlockEntity> {
        @Override
        protected void parts(SpentFuelCaskBlockEntity be, float pt, PoseStack ms, VertexConsumer vc, int light) {
            if (be.open()) return;
            part(CASK_PLATE, be, be.wholeModelFacing(), 0, 80 / 16f, 0).light(light).renderInto(ms, vc);
        }
    }
}
