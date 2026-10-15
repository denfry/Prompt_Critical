package dev.denfry.promptcritical.mod.mine;

import com.simibubi.create.api.contraption.ContraptionType;
import com.simibubi.create.api.registry.CreateRegistries;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimConfigs;
import dev.denfry.promptcritical.sim.mining.HoistConfig;
import dev.denfry.promptcritical.sim.mining.HoistSubsystem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Шахтный подъём (08 §8.2.8): меню панели, тип контрапции клети и подсистема подъёмов. */
public final class MineHoisting {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, PromptCritical.MOD_ID);
    public static final DeferredHolder<MenuType<?>, MenuType<HoistMenu>> HOIST_MENU =
            MENUS.register("mine_hoist", () -> IMenuTypeExtension.create(HoistMenu::new));

    public static final DeferredRegister<ContraptionType> CONTRAPTIONS =
            DeferredRegister.create(CreateRegistries.CONTRAPTION_TYPE, PromptCritical.MOD_ID);
    public static final DeferredHolder<ContraptionType, ContraptionType> CAGE_CONTRAPTION =
            CONTRAPTIONS.register("mine_cage", () -> new ContraptionType(CageContraption::new));

    private MineHoisting() {
    }

    public static void register(IEventBus modBus) {
        MENUS.register(modBus);
        CONTRAPTIONS.register(modBus);
        SimBindings.registerSubsystem(level -> new HoistSubsystem(
                SimConfigs.fromServerConfig().section(HoistConfig.class, HoistConfig.defaults())));
    }
}
