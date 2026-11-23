package dev.denfry.promptcritical.mod.foundry.client;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.foundry.FoundryBlockEntities;
import dev.denfry.promptcritical.mod.foundry.FoundryComponents;
import dev.denfry.promptcritical.mod.foundry.FoundryText;
import dev.denfry.promptcritical.mod.foundry.client.FoundryMachineRenderer.Motion;
import dev.denfry.promptcritical.sim.metallurgy.CastingSpec;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Клиент литейки и цеха: рендер машин, подсказки отливок и форм. Только на клиенте. */
public final class FoundryClient {
    /** Подвижные части из арт-реестра; создаются до запекания моделей. */
    private static final PartialModel PULSATOR = partial("coal_jig/partial/pulsator");
    private static final PartialModel CRUCIBLE = partial("crucible_furnace/partial/crucible");
    private static final PartialModel DRILL_SPINDLE = partial("drill_press/partial/spindle");
    private static final PartialModel LATHE_SPINDLE = partial("machine_lathe/partial/spindle");
    private static final PartialModel TABLE = partial("molding_station/partial/table");
    private static final PartialModel SHAKEOUT_DECK = partial("shakeout_grate/partial/deck");
    private static final PartialModel MILL_SPINDLE = partial("milling_machine/partial/spindle");
    private static final PartialModel SHAPER_RAM = partial("shaping_machine/partial/ram");
    private static final PartialModel GRINDER_WHEEL = partial("surface_grinder/partial/wheel");
    private static final PartialModel PVC_AGITATOR = partial("pvc_reactor/partial/agitator");
    private static final PartialModel GAS_CRANKSHAFT = partial("gas_compressor/partial/crankshaft");
    private static final PartialModel CC_ROTOR = partial("centrifugal_compressor/partial/rotor");

    static {
        // тигельная печь реального масштаба: цельная модель, части — до запекания
        dev.denfry.promptcritical.mod.client.whole.WholeModels.register("crucible_furnace_real");
        CrucibleRealRenderer.init();
    }

    private FoundryClient() {
    }

    private static PartialModel partial(String path) {
        return PartialModel.of(PromptCritical.id("block/" + path));
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(FoundryClient::registerRenderers);
        NeoForge.EVENT_BUS.addListener(FoundryClient::tooltip);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // оси частей — центры их моделей (пиксели, фасад на север)
        event.registerBlockEntityRenderer(FoundryBlockEntities.COAL_JIG.get(), c -> new FoundryMachineRenderer<>(PULSATOR, Motion.SPIN_X, 27, 8, 1));
        event.registerBlockEntityRenderer(FoundryBlockEntities.CUPOLA.get(), c -> new FoundryMachineRenderer<>(null, Motion.STATIC, 0, 0, 1));
        // тигельная печь: старая серия — свой тигель, crucible_furnace_real — цельная модель ямы
        event.registerBlockEntityRenderer(FoundryBlockEntities.CRUCIBLE.get(), c -> new net.minecraft.client.renderer.blockentity.BlockEntityRenderer<
                dev.denfry.promptcritical.mod.foundry.CrucibleBlockEntity>() {
            private final FoundryMachineRenderer<dev.denfry.promptcritical.mod.foundry.CrucibleBlockEntity> old =
                    new FoundryMachineRenderer<>(CRUCIBLE, Motion.STATIC, 0, 0, 1);
            private final CrucibleRealRenderer real = new CrucibleRealRenderer();

            @Override
            public void render(dev.denfry.promptcritical.mod.foundry.CrucibleBlockEntity be, float pt, com.mojang.blaze3d.vertex.PoseStack ms,
                    net.minecraft.client.renderer.MultiBufferSource buf, int light, int overlay) {
                if (be.wholeModelId() != null) real.render(be, pt, ms, buf, light, overlay);
                else old.render(be, pt, ms, buf, light, overlay);
            }

            @Override
            public boolean shouldRenderOffScreen(dev.denfry.promptcritical.mod.foundry.CrucibleBlockEntity be) {
                return be.wholeModelId() != null;
            }

            @Override
            public int getViewDistance() {
                return dev.denfry.promptcritical.mod.client.whole.WholeModelRenderer.VIEW_DISTANCE;
            }

            @Override
            public net.minecraft.world.phys.AABB getRenderBoundingBox(dev.denfry.promptcritical.mod.foundry.CrucibleBlockEntity be) {
                return be.wholeModelId() != null ? real.getRenderBoundingBox(be) : old.getRenderBoundingBox(be);
            }
        });
        event.registerBlockEntityRenderer(FoundryBlockEntities.MOLDING_STATION.get(), c -> new FoundryMachineRenderer<>(TABLE, Motion.JOLT, 0, 0, 1));
        event.registerBlockEntityRenderer(FoundryBlockEntities.SHAKEOUT_GRATE.get(), c -> new FoundryMachineRenderer<>(SHAKEOUT_DECK, Motion.JOLT, 0, 0, 1));
        event.registerBlockEntityRenderer(FoundryBlockEntities.MACHINE_LATHE.get(), c -> new FoundryMachineRenderer<>(LATHE_SPINDLE, Motion.SPIN_X, 14, 8, 1));
        event.registerBlockEntityRenderer(FoundryBlockEntities.DRILL_PRESS.get(), c -> new FoundryMachineRenderer<>(DRILL_SPINDLE, Motion.SPIN_Y, 8, 7, 4));
        event.registerBlockEntityRenderer(FoundryBlockEntities.MILLING_MACHINE.get(), c -> new FoundryMachineRenderer<>(MILL_SPINDLE, Motion.SPIN_Z, 8, 18, 2));
        // ползун ходит на ±4 пикселя; кривошип — один двойной ход на два оборота вала
        event.registerBlockEntityRenderer(FoundryBlockEntities.SHAPING_MACHINE.get(), c -> new FoundryMachineRenderer<>(SHAPER_RAM, Motion.SLIDE_X, 4, 0, 0.5f));
        event.registerBlockEntityRenderer(FoundryBlockEntities.SURFACE_GRINDER.get(), c -> new FoundryMachineRenderer<>(GRINDER_WHEEL, Motion.SPIN_Z, 13, 14, 4));
        // мешалка автоклава ПВХ (art/blockbench/polymers/pvc_reactor.author.js): муфта в фонаре привода, ось x 8, z 8;
        // суспензионные реакторы мешают на ~100–150 об/мин
        event.registerBlockEntityRenderer(dev.denfry.promptcritical.mod.chlor.ChlorBlockEntities.PVC_REACTOR.get(),
                c -> FoundryMachineRenderer.whileRunning(PVC_AGITATOR, Motion.SPIN_Y, 8, 8, 120));
        // коленвал газового компрессора с маховиком (art/blockbench/gas_processing/gas_compressor.author.js): ось x на y 8, z 16
        event.registerBlockEntityRenderer(dev.denfry.promptcritical.mod.gas.GasBlockEntities.GAS_COMPRESSOR.get(),
                c -> new FoundryMachineRenderer<>(GAS_CRANKSHAFT, Motion.SPIN_X, 8, 16, 1));
        // муфта ротора центробежного компрессора (art/blockbench/oil_transport/centrifugal_compressor): ось x на y 12, z 16
        event.registerBlockEntityRenderer(dev.denfry.promptcritical.mod.gas.GasBlockEntities.CENTRIFUGAL_COMPRESSOR.get(),
                c -> new FoundryMachineRenderer<>(CC_ROTOR, Motion.SPIN_X, 12, 16, 1));
    }

    private static void tooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        CastingSpec c = stack.get(FoundryComponents.CASTING_SPEC.get());
        if (c != null) FoundryText.castingTooltip(c, event.getToolTip());
        FoundryComponents.Finish f = stack.get(FoundryComponents.MACHINED_FINISH.get());
        if (f != null) event.getToolTip().add(FoundryText.finish(f).withStyle(ChatFormatting.GRAY));
        FoundryComponents.Pattern p = stack.get(FoundryComponents.CASTING_PATTERN.get());
        if (p != null) event.getToolTip().add(FoundryText.riser(p.riser()).withStyle(ChatFormatting.GRAY));
        FoundryComponents.Mold m = stack.get(FoundryComponents.SAND_MOLD.get());
        if (m != null) event.getToolTip().add(FoundryText.msg("mold_item", FoundryText.kind(m.kind()), FoundryText.riser(m.riser()),
                FoundryText.num(m.moisture() * 100)).withStyle(ChatFormatting.GRAY));
    }
}
