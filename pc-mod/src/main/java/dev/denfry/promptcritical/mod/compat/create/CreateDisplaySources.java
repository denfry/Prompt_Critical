package dev.denfry.promptcritical.mod.compat.create;

import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.api.registry.CreateRegistries;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.boiler.PressureGaugeBlockEntity;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.scada.AnnunciatorPanels;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Источники Display Link Create (16 §16.2.1). Create — жёсткая зависимость (16 N1), поэтому без проверки
 * {@code isLoaded}. Источник «показание прибора» вешается на каждый тип блок-сущности мода со щитом и на манометр
 * котла, «сигнализации» — на светосигнальное табло.
 */
public final class CreateDisplaySources {
    public static final DeferredRegister<DisplaySource> SOURCES = DeferredRegister.create(CreateRegistries.DISPLAY_SOURCE, PromptCritical.MOD_ID);
    public static final DeferredHolder<DisplaySource, PanelReadingDisplaySource> PANEL_READING =
            SOURCES.register("panel_reading", PanelReadingDisplaySource::new);
    public static final DeferredHolder<DisplaySource, AlarmListDisplaySource> ALARMS = SOURCES.register("alarms", AlarmListDisplaySource::new);

    private CreateDisplaySources() {
    }

    public static void register(IEventBus modBus) {
        SOURCES.register(modBus);
        modBus.addListener(FMLCommonSetupEvent.class, e -> e.enqueueWork(CreateDisplaySources::bind));
    }

    private static void bind() {
        Set<BlockEntityType<?>> panels = new LinkedHashSet<>(), alarms = new LinkedHashSet<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (!(block instanceof EntityBlock entity) || !PromptCritical.MOD_ID.equals(BuiltInRegistries.BLOCK.getKey(block).getNamespace()))
                continue;
            BlockEntity be;
            try {
                // Тип блок-сущности не знает своего класса; пробный экземпляр без мира, как при загрузке чанка.
                be = entity.newBlockEntity(BlockPos.ZERO, block.defaultBlockState());
            } catch (RuntimeException | LinkageError ex) {
                PromptCritical.LOGGER.debug("Display Link: no probe block entity for {}", block, ex);
                continue;
            }
            if (be instanceof PanelHost host) {
                panels.add(be.getType());
                if (host.panelLayout() == AnnunciatorPanels.ANNUNCIATOR) alarms.add(be.getType());
            } else if (be instanceof PressureGaugeBlockEntity) {
                panels.add(be.getType());
            }
        }
        panels.forEach(t -> DisplaySource.BY_BLOCK_ENTITY.add(t, PANEL_READING.get()));
        alarms.forEach(t -> DisplaySource.BY_BLOCK_ENTITY.add(t, ALARMS.get()));
        PromptCritical.LOGGER.info("Display Link sources bound to {} block entity types ({} annunciators)", panels.size(), alarms.size());
    }
}
