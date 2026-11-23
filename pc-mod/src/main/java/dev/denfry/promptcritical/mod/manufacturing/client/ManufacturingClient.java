package dev.denfry.promptcritical.mod.manufacturing.client;

import dev.denfry.promptcritical.mod.manufacturing.ManufacturingComponents;
import dev.denfry.promptcritical.mod.manufacturing.ManufacturingText;
import dev.denfry.promptcritical.sim.manufacturing.PartSpec;
import dev.denfry.promptcritical.mod.foundry.client.FoundryMachineRenderer;
import dev.denfry.promptcritical.mod.foundry.client.FoundryMachineRenderer.Motion;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Клиент производства деталей: подсказка паспорта детали. Только на клиенте. */
public final class ManufacturingClient {
    private ManufacturingClient() {
    }

    private static final dev.engine_room.flywheel.lib.model.baked.PartialModel HAMMER_RAM = partial("power_hammer/partial/ram"),
            PRESS_RAM = partial("crank_press/partial/ram");

    private static dev.engine_room.flywheel.lib.model.baked.PartialModel partial(String path) {
        return dev.engine_room.flywheel.lib.model.baked.PartialModel.of(dev.denfry.promptcritical.mod.PromptCritical.id("block/" + path));
    }

    public static void register(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(ManufacturingClient::tooltip);
        modBus.addListener(ManufacturingClient::renderers);
    }

    private static void renderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        // баба молота ходит на 3 пикселя, ползун пресса на 2 (art/blockbench/smithy/smithy.author.js)
        event.registerBlockEntityRenderer(dev.denfry.promptcritical.mod.manufacturing.SmithyBlockEntities.POWER_HAMMER.get(),
                c -> new FoundryMachineRenderer<>(HAMMER_RAM, Motion.HAMMER, 3, 0, 1));
        event.registerBlockEntityRenderer(dev.denfry.promptcritical.mod.manufacturing.SmithyBlockEntities.CRANK_PRESS.get(),
                c -> new FoundryMachineRenderer<>(PRESS_RAM, Motion.HAMMER, 2, 0, 0.5f));
        event.registerBlockEntityRenderer(dev.denfry.promptcritical.mod.manufacturing.SmithyBlockEntities.WIRE_MILL.get(),
                c -> new FoundryMachineRenderer<>(null, Motion.STATIC, 0, 0, 1));
        event.registerBlockEntityRenderer(dev.denfry.promptcritical.mod.manufacturing.SmithyBlockEntities.SHEET_MILL.get(),
                c -> new FoundryMachineRenderer<>(null, Motion.STATIC, 0, 0, 1));
        event.registerBlockEntityRenderer(dev.denfry.promptcritical.mod.manufacturing.SmithyBlockEntities.WINDING_MACHINE.get(),
                c -> new FoundryMachineRenderer<>(null, Motion.STATIC, 0, 0, 1));
        event.registerBlockEntityRenderer(dev.denfry.promptcritical.mod.manufacturing.SmithyBlockEntities.NOTCHING_PRESS.get(),
                c -> new FoundryMachineRenderer<>(null, Motion.STATIC, 0, 0, 1));
    }

    private static void tooltip(ItemTooltipEvent event) {
        PartSpec p = event.getItemStack().get(ManufacturingComponents.PART_SPEC.get());
        if (p != null) ManufacturingText.tooltip(p, event.getToolTip());
        var w = event.getItemStack().get(dev.denfry.promptcritical.mod.manufacturing.ManufacturingComponents.WORKPIECE.get());
        var lvl = net.minecraft.client.Minecraft.getInstance().level;
        if (w != null && lvl != null) ManufacturingText.workpieceTooltip(w, lvl.getGameTime(), event.getToolTip());
        var wire = event.getItemStack().get(ManufacturingComponents.WIRE.get());
        if (wire != null) {
            ManufacturingText.wireTooltip(wire, event.getToolTip());
            ManufacturingText.magnetWireTooltip(wire, event.getToolTip());
        }
        var st = event.getItemStack();
        var sheet = st.get(ManufacturingComponents.ELECTRICAL_SHEET.get());
        if (sheet != null) ManufacturingText.sheetTooltip(sheet, event.getToolTip());
        var lam = st.get(ManufacturingComponents.LAMINATION.get());
        if (lam != null) ManufacturingText.laminationTooltip(lam, event.getToolTip());
        var core = st.get(ManufacturingComponents.STATOR_CORE.get());
        if (core != null) ManufacturingText.coreTooltip(core, event.getToolTip());
        var stator = st.get(ManufacturingComponents.STATOR.get());
        if (stator != null) ManufacturingText.statorTooltip(stator, event.getToolTip());
    }
}
