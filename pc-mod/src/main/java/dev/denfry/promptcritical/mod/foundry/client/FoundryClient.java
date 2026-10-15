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
        event.registerBlockEntityRenderer(FoundryBlockEntities.CRUCIBLE.get(), c -> new FoundryMachineRenderer<>(CRUCIBLE, Motion.STATIC, 0, 0, 1));
        event.registerBlockEntityRenderer(FoundryBlockEntities.MOLDING_STATION.get(), c -> new FoundryMachineRenderer<>(TABLE, Motion.JOLT, 0, 0, 1));
        event.registerBlockEntityRenderer(FoundryBlockEntities.SHAKEOUT_GRATE.get(), c -> new FoundryMachineRenderer<>(null, Motion.STATIC, 0, 0, 1));
        event.registerBlockEntityRenderer(FoundryBlockEntities.MACHINE_LATHE.get(), c -> new FoundryMachineRenderer<>(LATHE_SPINDLE, Motion.SPIN_X, 14, 8, 1));
        event.registerBlockEntityRenderer(FoundryBlockEntities.DRILL_PRESS.get(), c -> new FoundryMachineRenderer<>(DRILL_SPINDLE, Motion.SPIN_Y, 8, 7, 4));
    }

    private static void tooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        CastingSpec c = stack.get(FoundryComponents.CASTING_SPEC.get());
        if (c != null) FoundryText.castingTooltip(c, event.getToolTip());
        FoundryComponents.Pattern p = stack.get(FoundryComponents.CASTING_PATTERN.get());
        if (p != null) event.getToolTip().add(FoundryText.riser(p.riser()).withStyle(ChatFormatting.GRAY));
        FoundryComponents.Mold m = stack.get(FoundryComponents.SAND_MOLD.get());
        if (m != null) event.getToolTip().add(FoundryText.msg("mold_item", FoundryText.kind(m.kind()), FoundryText.riser(m.riser()),
                FoundryText.num(m.moisture() * 100)).withStyle(ChatFormatting.GRAY));
    }
}
