package dev.denfry.promptcritical.mod.ironworks.client;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.foundry.client.FoundryMachineRenderer;
import dev.denfry.promptcritical.mod.foundry.client.FoundryMachineRenderer.Motion;
import dev.denfry.promptcritical.mod.ironworks.IronworksBlockEntities;
import dev.denfry.promptcritical.mod.ironworks.IronworksComponents;
import dev.denfry.promptcritical.mod.ironworks.IronworksText;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Клиент коксохима и домны: вращающиеся части машин на валу (неподвижные части — в мультипарте состояний) и
 * подсказки кокса, агломерата и электродов. Только на клиенте.
 */
public final class IronworksClient {
    private static final PartialModel EXHAUSTER_ROTOR = partial("gas_exhauster/partial/rotor");
    private static final PartialModel BLOWER_ROTOR = partial("blast_blower/partial/rotor");
    private static final PartialModel EXTRUDER_SCREW = partial("electrode_extruder/partial/screw");
    private static final PartialModel SEPARATOR_DRUM = partial("magnetic_separator/partial/drum");
    /** Барабанный сепаратор реального масштаба: цельная модель и барабан — до запекания. */
    private static final PartialModel SEPARATOR_DRUM_REAL = partial("magnetic_separator_real/partial/drum");

    static {
        dev.denfry.promptcritical.mod.client.whole.WholeModels.register("magnetic_separator_real");
    }

    static {
        // доменный цех реального масштаба: цельные модели и partial-модели — до запекания
        HotBlastStoveRealRenderer.init();
        BlastBlowerEngineRenderer.init();
    }

    private IronworksClient() {
    }

    static {
        // башня тушения реального масштаба: цельная модель и partial — до запекания
        QuenchTowerRenderer.init();
    }

    private static PartialModel partial(String path) {
        return PartialModel.of(PromptCritical.id("block/" + path));
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(IronworksClient::registerRenderers);
        NeoForge.EVENT_BUS.addListener(IronworksClient::tooltip);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // оси частей — центры их моделей (пиксели, фасад на север)
        event.registerBlockEntityRenderer(IronworksBlockEntities.QUENCH_TOWER.get(), c -> new QuenchTowerRenderer());
        event.registerBlockEntityRenderer(IronworksBlockEntities.GAS_EXHAUSTER.get(), c -> new FoundryMachineRenderer<>(EXHAUSTER_ROTOR, Motion.SPIN_Z, 16, 16, 1));
        event.registerBlockEntityRenderer(IronworksBlockEntities.BLAST_BLOWER.get(), c -> new FoundryMachineRenderer<>(BLOWER_ROTOR, Motion.SPIN_Z, 16, 16, 1));
        event.registerBlockEntityRenderer(IronworksBlockEntities.ELECTRODE_EXTRUDER.get(), c -> new FoundryMachineRenderer<>(EXTRUDER_SCREW, Motion.SPIN_X, 8, 8, 1));
        event.registerBlockEntityRenderer(IronworksBlockEntities.HOT_BLAST_STOVE_REAL.get(), c -> new HotBlastStoveRealRenderer());
        event.registerBlockEntityRenderer(IronworksBlockEntities.BLAST_BLOWER_ENGINE.get(), c -> new BlastBlowerEngineRenderer());
        // реальный: барабан вокруг x через (y 24, z 8), хранится на 8 px восточнее; скорость — вала в клетке-порте
        event.registerBlockEntityRenderer(IronworksBlockEntities.MAGNETIC_SEPARATOR.get(), c -> new dev.denfry.promptcritical.mod.client.whole.SpinWholeRenderer<>(
                new FoundryMachineRenderer<>(SEPARATOR_DRUM, Motion.SPIN_X, 10, 8, 1))
                .spin(SEPARATOR_DRUM_REAL, net.minecraft.core.Direction.Axis.X, 24, 8, -8, 0, 0,
                        dev.denfry.promptcritical.mod.foundry.PortedKineticMachineBlock::portSpeed));
    }

    private static void tooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        IronworksComponents.CokeAssay coke = stack.get(IronworksComponents.COKE_ASSAY.get());
        if (coke != null) IronworksText.cokeTooltip(coke, event.getToolTip());
        IronworksComponents.SinterAssay sinter = stack.get(IronworksComponents.SINTER_ASSAY.get());
        if (sinter != null) IronworksText.sinterTooltip(sinter, event.getToolTip());
        IronworksComponents.Electrode e = stack.get(IronworksComponents.ELECTRODE_STATE.get());
        if (e != null) IronworksText.electrodeTooltip(e, event.getToolTip());
    }
}
