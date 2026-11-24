package dev.denfry.promptcritical.mod.fx.client;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.chem.ChemBlockEntities;
import dev.denfry.promptcritical.mod.chlor.ChlorBlockEntities;
import dev.denfry.promptcritical.mod.client.whole.SpinWholeRenderer;
import dev.denfry.promptcritical.mod.client.whole.WholeModels;
import dev.denfry.promptcritical.mod.nitrogen.NitrogenBlockEntities;
import dev.denfry.promptcritical.mod.powerhouse.PowerhouseBlockEntities;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * I3 реального масштаба (art/blockbench/i3/i3.author.js): цельные модели и части по шапке арта. Вращающиеся части
 * рисуются, пока машина работает или горит; заслонки труб стоят открытыми. Пары (a, b) и сдвиги — в px модели; ось
 * части задана в координатах её хранения (сдвиг хранения вычтен), сдвиг рисования возвращает её на место. Колонна
 * синтеза аммиака, реактор метанола и реактор ПВХ рисуются через свои прежние рендеры ({@link ProcessArtClient},
 * {@code FoundryClient}), обёрнутые в {@link SpinWholeRenderer}.
 */
public final class I3RealClient {
    /** Муфта нагрузки ГТУ: ось x на (y 48, z 8), хранится на +80 x, −32 y. */
    static final PartialModel GT_COUPLING = partial("gas_turbine_heavy_real", "coupling");
    /** Заслонка трубы котла-утилизатора: ось z на (104, 200), хранится на −96 x, −184 y. */
    static final PartialModel HRSG_DAMPER = partial("hrsg_real", "damper");
    /** Муфта дымососа печи риформинга: ось z на (64, 10), хранится на −48 x, +8 z. */
    static final PartialModel REFORMER_FAN = partial("steam_reformer_real", "fan_coupling");
    /** Шибер трубы печи пиролиза: ось x на (y 240, z 8), хранится на −224 y. */
    static final PartialModel PYROLYSIS_DAMPER = partial("pyrolysis_furnace_real", "damper");
    /** Муфта мешалки реактора ПВХ: ось y на (8, 8), хранится на −96 y. */
    public static final PartialModel PVC_COUPLING = partial("pvc_reactor_real", "agitator_coupling");

    static {
        for (String id : new String[] {"gas_turbine_heavy_real", "hrsg_real", "steam_reformer_real", "steam_reformer_real_lit",
                "ammonia_converter_real", "methanol_converter_real", "pyrolysis_furnace_real", "pyrolysis_furnace_real_lit",
                "chlor_alkali_electrolyser_real", "pvc_reactor_real"})
            WholeModels.register(id);
    }

    private I3RealClient() {
    }

    private static PartialModel partial(String id, String name) {
        return PartialModel.of(PromptCritical.id("block/" + id + "/partial/" + name));
    }

    /** Загрузить класс до запекания моделей и подписать рендеры. */
    public static void register(IEventBus modBus) {
        modBus.addListener(I3RealClient::registerRenderers);
    }

    static double whileRunning(BlockEntity be, double rpm) {
        BlockState s = be.getBlockState();
        return s.hasProperty(PcProperties.RUNNING) && s.getValue(PcProperties.RUNNING) ? rpm : 0;
    }

    static double whileLit(BlockEntity be, double rpm) {
        BlockState s = be.getBlockState();
        return s.hasProperty(BlockStateProperties.LIT) && s.getValue(BlockStateProperties.LIT) ? rpm : 0;
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // у старых ГТУ, котла-утилизатора, печей и электролизёра своего рендера нет: блоки рисуются моделями состояний
        event.registerBlockEntityRenderer(PowerhouseBlockEntities.GAS_TURBINE.get(), c -> new SpinWholeRenderer<>(null)
                .spin(GT_COUPLING, Direction.Axis.X, 48 - 32, 8, -80, 32, 0, be -> whileRunning(be, 30)));
        event.registerBlockEntityRenderer(PowerhouseBlockEntities.HRSG.get(), c -> new SpinWholeRenderer<>(null)
                .spin(HRSG_DAMPER, Direction.Axis.Z, 104 - 96, 200 - 184, 96, 184, 0, be -> 0));
        event.registerBlockEntityRenderer(NitrogenBlockEntities.STEAM_REFORMER.get(), c -> new SpinWholeRenderer<>(null).lit()
                .spin(REFORMER_FAN, Direction.Axis.Z, 64 - 48, 10, 48, 0, -8, be -> whileLit(be, 60)));
        event.registerBlockEntityRenderer(ChemBlockEntities.PYROLYSIS_FURNACE.get(), c -> new SpinWholeRenderer<>(null).lit()
                .spin(PYROLYSIS_DAMPER, Direction.Axis.X, 240 - 224, 8, 0, 224, 0, be -> 0));
        event.registerBlockEntityRenderer(ChlorBlockEntities.CELL_ROOM.get(), c -> new SpinWholeRenderer<>(null));
    }
}
