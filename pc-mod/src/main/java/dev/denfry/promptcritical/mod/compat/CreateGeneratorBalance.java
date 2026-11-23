package dev.denfry.promptcritical.mod.compat;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.infrastructure.config.AllConfigs;
import dev.denfry.promptcritical.mod.config.ServerConfig;
import java.util.function.DoubleSupplier;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Генераторы Create слабее машин мода (решение владельца 2026-11-23): колёса, ветряк и паровая машина на котле Create
 * дают свою ёмкость из конфига Create, умноженную на коэффициент {@code energy.create_generator_factor} (паровая машина
 * Create — на {@code energy.create_steam_engine_factor}). Прямая регистрация в {@link BlockStressValues#CAPACITIES}
 * стоит перед провайдером конфига Create, поэтому значение Create остаётся настраиваемым, а мы только масштабируем его.
 */
public final class CreateGeneratorBalance {
    private CreateGeneratorBalance() {}

    public static void register(IEventBus modBus) {
        modBus.addListener((FMLCommonSetupEvent e) -> e.enqueueWork(CreateGeneratorBalance::apply));
    }

    private static void apply() {
        DoubleSupplier generic = () -> factor(ServerConfig.CREATE_GENERATOR_FACTOR);
        scale(AllBlocks.WATER_WHEEL.get(), generic);
        scale(AllBlocks.LARGE_WATER_WHEEL.get(), generic);
        scale(AllBlocks.WINDMILL_BEARING.get(), generic);
        scale(AllBlocks.STEAM_ENGINE.get(), () -> factor(ServerConfig.CREATE_STEAM_ENGINE_FACTOR));
    }

    private static void scale(Block block, DoubleSupplier factor) {
        BlockStressValues.CAPACITIES.register(block, () -> createCapacity(block) * factor.getAsDouble());
    }

    /** Ёмкость блока из конфига Create (SU на об/мин); 0, пока конфиг сервера не загружен. */
    private static double createCapacity(Block block) {
        try {
            DoubleSupplier s = AllConfigs.server().kinetics.stressValues.getCapacity(block);
            return s == null ? 0 : s.getAsDouble();
        } catch (IllegalStateException | NullPointerException e) {
            return 0;
        }
    }

    private static double factor(net.neoforged.neoforge.common.ModConfigSpec.DoubleValue v) {
        try {
            return v.get();
        } catch (IllegalStateException e) {
            return 1.0;
        }
    }
}
