package dev.denfry.promptcritical.mod.manufacturing.client;

import net.minecraft.core.Direction;
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

    // подвижные части машин реального масштаба (art/blockbench/smithy, rolling/real): px модели, фасад на север
    private static final dev.engine_room.flywheel.lib.model.baked.PartialModel
            PH_RAM = partial("power_hammer_real/partial/ram"), PH_CRANK = partial("power_hammer_real/partial/crank"),
            BL_GLOW = partial("bloomery_real/partial/glow"), BL_BELLOWS = partial("bloomery_real/partial/bellows"),
            FH_GLOW = partial("forge_hearth_real/partial/glow"), FH_BELLOWS = partial("forge_hearth_real/partial/bellows"),
            CP_RAM = partial("crank_press_real/partial/ram"), CP_FLYWHEEL = partial("crank_press_real/partial/flywheel"),
            WM_DRUM = partial("wire_mill_real/partial/drum"),
            SM_TOP = partial("sheet_mill_real/partial/roll_top"), SM_BOTTOM = partial("sheet_mill_real/partial/roll_bottom"),
            NP_RAM = partial("notching_press_real/partial/ram"), NP_INDEX = partial("notching_press_real/partial/index"),
            NP_FLYWHEEL = partial("notching_press_real/partial/flywheel"),
            WD_FACEPLATE = partial("winding_machine_real/partial/faceplate"), WD_SPOOL = partial("winding_machine_real/partial/spool"),
            HF_DOOR = partial("heating_furnace_real/partial/door"), HF_GLOW = partial("heating_furnace_real/partial/glow"),
            HD_DOOR = partial("hardening_furnace_real/partial/door"), HD_GLOW = partial("hardening_furnace_real/partial/glow");

    /** Цельные модели кузницы и машиностроения реального масштаба. */
    private static final String[] REAL = {"power_hammer_real", "bloomery_real", "forge_hearth_real", "crank_press_real",
            "wire_mill_real", "sheet_mill_real", "notching_press_real", "winding_machine_real", "heating_furnace_real",
            "hardening_furnace_real", "assembly_stand"};

    private static dev.engine_room.flywheel.lib.model.baked.PartialModel partial(String path) {
        return dev.engine_room.flywheel.lib.model.baked.PartialModel.of(dev.denfry.promptcritical.mod.PromptCritical.id("block/" + path));
    }

    public static void register(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(ManufacturingClient::tooltip);
        for (String id : REAL) dev.denfry.promptcritical.mod.client.whole.WholeModels.register(id);
        modBus.addListener(ManufacturingClient::renderers);
    }

    private static void renderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        // старые машины — прежние рендеры; реальные (тот же BE) — цельная модель и подвижные части
        event.registerBlockEntityRenderer(dev.denfry.promptcritical.mod.manufacturing.SmithyBlockEntities.POWER_HAMMER.get(),
                c -> new SmithyRealRenderer<>(new FoundryMachineRenderer<>(HAMMER_RAM, Motion.HAMMER, 3, 0, 1), true, x -> {
                    // баба: ход 6,2 px вниз; коленвал с маховиком — вокруг x через (y 8, z 8)
                    x.put(x.at(PH_RAM, 0, -6.2 * x.stroke(14), 0));
                    x.put(SmithyRealRenderer.Ctx.spin(x.at(PH_CRANK, 0, 0, 0), Direction.Axis.X, 0, 8, 8, x.angle(64)));
                }));
        event.registerBlockEntityRenderer(dev.denfry.promptcritical.mod.manufacturing.SmithyBlockEntities.CRANK_PRESS.get(),
                c -> new SmithyRealRenderer<>(new FoundryMachineRenderer<>(PRESS_RAM, Motion.HAMMER, 2, 0, 0.5f), true, x -> {
                    // ползун 3 px вниз; маховик сохранён на 16 px ниже, ось z через (x 8, y 40) модели
                    x.put(x.at(CP_RAM, 0, -3 * x.stroke(20), 0));
                    x.put(SmithyRealRenderer.Ctx.spin(x.at(CP_FLYWHEEL, 0, 16, 0), Direction.Axis.Z, 8, 24, 0, x.angle(45)));
                }));
        event.registerBlockEntityRenderer(dev.denfry.promptcritical.mod.manufacturing.SmithyBlockEntities.WIRE_MILL.get(),
                c -> new SmithyRealRenderer<>(new FoundryMachineRenderer<>(null, Motion.STATIC, 0, 0, 1), true,
                        x -> x.put(SmithyRealRenderer.Ctx.spin(x.at(WM_DRUM, 0, 0, 0), Direction.Axis.Y, 8, 0, 7, x.angle(20)))));
        event.registerBlockEntityRenderer(dev.denfry.promptcritical.mod.manufacturing.SmithyBlockEntities.SHEET_MILL.get(),
                c -> new SmithyRealRenderer<>(new FoundryMachineRenderer<>(null, Motion.STATIC, 0, 0, 1), true, x -> {
                    x.put(SmithyRealRenderer.Ctx.spin(x.at(SM_TOP, 0, 0, 0), Direction.Axis.X, 0, 19, 8, x.angle(30)));
                    x.put(SmithyRealRenderer.Ctx.spin(x.at(SM_BOTTOM, 0, 0, 0), Direction.Axis.X, 0, 13, 8, -x.angle(30)));
                }));
        event.registerBlockEntityRenderer(dev.denfry.promptcritical.mod.manufacturing.SmithyBlockEntities.WINDING_MACHINE.get(),
                c -> new SmithyRealRenderer<>(new FoundryMachineRenderer<>(null, Motion.STATIC, 0, 0, 1), true, x -> {
                    x.put(SmithyRealRenderer.Ctx.spin(x.at(WD_FACEPLATE, 0, 0, 0), Direction.Axis.X, 0, 18, 8, x.angle(15)));
                    x.put(SmithyRealRenderer.Ctx.spin(x.at(WD_SPOOL, 0, 0, 0), Direction.Axis.X, 0, 22, 8, -x.angle(25)));
                }));
        event.registerBlockEntityRenderer(dev.denfry.promptcritical.mod.manufacturing.SmithyBlockEntities.NOTCHING_PRESS.get(),
                c -> new SmithyRealRenderer<>(new FoundryMachineRenderer<>(null, Motion.STATIC, 0, 0, 1), true, x -> {
                    // ползун 1 px за ход, стол поворачивается на паз за ход, маховик — вокруг z через (x 8, y 26)
                    float step = x.on() ? (float) Math.floor(x.t() / 8) * 10 % 360 : 0;
                    x.put(x.at(NP_RAM, 0, -x.stroke(8), 0));
                    x.put(SmithyRealRenderer.Ctx.spin(x.at(NP_INDEX, 0, 0, 0), Direction.Axis.Y, 8, 0, -8, step));
                    x.put(SmithyRealRenderer.Ctx.spin(x.at(NP_FLYWHEEL, 0, 0, 0), Direction.Axis.Z, 8, 26, 0, x.angle(150)));
                }));
        // ручная ступень: у старых блоков только модели блока; у реальных — свечение и мехи, пока горит
        event.registerBlockEntityRenderer(dev.denfry.promptcritical.mod.manufacturing.SmithyBlockEntities.BLOOMERY.get(),
                c -> new SmithyRealRenderer<>(null, false, x -> {
                    if (x.on()) x.put(x.at(BL_GLOW, 0, 0, 0));
                    x.put(x.at(BL_BELLOWS, 0, -1.6 * x.stroke(30), 0));
                }));
        event.registerBlockEntityRenderer(dev.denfry.promptcritical.mod.manufacturing.SmithyBlockEntities.FORGE_HEARTH.get(),
                c -> new SmithyRealRenderer<>(null, false, x -> {
                    if (x.on()) x.put(x.at(FH_GLOW, 0, 0, 0));
                    // мехи сохранены на 16 px ниже
                    x.put(x.at(FH_BELLOWS, 0, 16 + 2.4 * x.stroke(40), 0));
                }));
        event.registerBlockEntityRenderer(dev.denfry.promptcritical.mod.manufacturing.SmithyBlockEntities.HEATING_FURNACE.get(),
                c -> new SmithyRealRenderer<>(null, false, x -> {
                    x.put(x.at(HF_DOOR, 0, 0, 0));
                    if (x.on()) x.put(x.at(HF_GLOW, 0, 0, 0));
                }));
        event.registerBlockEntityRenderer(dev.denfry.promptcritical.mod.manufacturing.SmithyBlockEntities.HARDENING_FURNACE.get(),
                c -> new SmithyRealRenderer<>(null, false, x -> {
                    x.put(x.at(HD_DOOR, 0, 0, 0));
                    if (x.on()) x.put(x.at(HD_GLOW, 0, 0, 0));
                }));
    }

    private static void tooltip(ItemTooltipEvent event) {
        PartSpec p = event.getItemStack().get(ManufacturingComponents.PART_SPEC.get());
        if (p != null) ManufacturingText.tooltip(p,
                event.getItemStack().getOrDefault(ManufacturingComponents.INSPECTION_CHECKS.get(), 0), event.getToolTip());
        var passport = event.getItemStack().get(ManufacturingComponents.MACHINE_PASSPORT.get());
        if (passport != null) ManufacturingText.passportTooltip(passport, event.getToolTip());
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
