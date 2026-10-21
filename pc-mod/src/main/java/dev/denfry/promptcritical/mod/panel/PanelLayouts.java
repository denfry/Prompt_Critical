package dev.denfry.promptcritical.mod.panel;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.boiler.BoilerPanels;
import dev.denfry.promptcritical.mod.foundry.FoundryPanels;
import dev.denfry.promptcritical.mod.grid.GridPanels;
import dev.denfry.promptcritical.mod.mine.MinePanels;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Все раскладки щитов по id (клиент находит раскладку по строке из пакета открытия) и тип меню. */
public final class PanelLayouts {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, PromptCritical.MOD_ID);
    public static final DeferredHolder<MenuType<?>, MenuType<PanelMenu>> MENU =
            MENUS.register("machine_panel", () -> IMenuTypeExtension.create(PanelMenu::new));

    private static final Map<String, PanelLayout> BY_ID = new LinkedHashMap<>();

    static {
        for (List<PanelLayout> group : List.of(BoilerPanels.all(), FoundryPanels.all(), MinePanels.all(), GridPanels.all(),
                dev.denfry.promptcritical.mod.ironworks.IronworksPanels.all(),
                dev.denfry.promptcritical.mod.steelworks.SteelworksPanels.all(),
                dev.denfry.promptcritical.mod.automation.AutomationPanels.all())) {
            for (PanelLayout l : group) {
                if (BY_ID.put(l.id, l) != null) throw new IllegalStateException("duplicate panel " + l.id);
            }
        }
    }

    private PanelLayouts() {
    }

    public static void register(IEventBus modBus) {
        MENUS.register(modBus);
    }

    public static PanelLayout get(String id) {
        PanelLayout l = BY_ID.get(id);
        if (l == null) throw new IllegalArgumentException("unknown panel " + id);
        return l;
    }

    public static Map<String, PanelLayout> all() {
        return BY_ID;
    }
}
