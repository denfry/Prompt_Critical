package dev.denfry.promptcritical.mod.grid;

import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimConfigs;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.sim.grid.GridConfig;
import dev.denfry.promptcritical.sim.grid.GridSubsystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LightningBolt;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Привязка электросети I1 (этап 8 M3): реестры BE и компонентов, подсистема {@link GridSubsystem} в каждом измерении,
 * удары молнии в пролёты ВЛ.
 */
public final class GridBinding {
    private GridBinding() {
    }

    public static void register(IEventBus modBus) {
        GridBlockEntities.BLOCK_ENTITIES.register(modBus);
        GridComponents.COMPONENTS.register(modBus);
        SimBindings.registerSubsystem(level -> new GridSubsystem(
                SimConfigs.fromServerConfig().section(GridConfig.class, GridConfig.defaults())));
        NeoForge.EVENT_BUS.addListener(GridBinding::onEntityJoin);
    }

    private static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof LightningBolt bolt) || !(event.getLevel() instanceof ServerLevel s)) return;
        SimLevelBinding b = SimBindings.get(s);
        if (b != null) b.grids().lightning(bolt.position());
    }
}
