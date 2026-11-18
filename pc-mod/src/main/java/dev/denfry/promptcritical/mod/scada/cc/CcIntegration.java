package dev.denfry.promptcritical.mod.scada.cc;

import dan200.computercraft.api.peripheral.PeripheralCapability;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.scada.ScadaBlockEntities;
import dev.denfry.promptcritical.mod.scada.device.DeviceHub;
import dev.denfry.promptcritical.mod.scada.device.DeviceRuntime;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Мост в CC: Tweaked. Загружается только при установленном CC ({@code ScadaBlockEntities} проверяет
 * {@code ModList.isLoaded("computercraft")}), поэтому мод без CC не видит ни одного класса {@code dan200}.
 *
 * <p>Периферии:
 * <ul>
 * <li>своя у пунктов верхнего уровня: шлюз телемеханики — {@code pc_rtu}, историан — {@code pc_historian}, двойник —
 * {@code pc_twin}, биржевой терминал — {@code pc_market}, пульт диспетчера сети — {@code pc_grid} (07 §7.17.4),
 * сервер завода — {@code pc_mes};</li>
 * <li>периферия устройства {@link DevicePeripheral} (03b §3b.2, 03d §3d.14) у каждой машины с профилем
 * {@code mod.scada.device.DeviceProfiles}: реакторы ({@code pc_reactor} + {@code pc_rod_drive} + {@code pc_protection}),
 * насосы ({@code pc_pump}), ПЛК ({@code pc_plc}), ПАЗ ({@code pc_sis}, только чтение), скважины и буровая
 * ({@code pc_well}, {@code pc_drilling}), котлы, генераторы, печи, колонны и прочие типы 00 §0.7.2. Все они — ещё и
 * {@code pc_device} с общими методами 03b §3b.1.9 ({@code read}, {@code readMany}, {@code watch}, квитанции,
 * Modbus-регистры).</li>
 * </ul>
 *
 * <p>Что из 03b §3b.1 сделано и как: потоки §3b.1.3 (чтения — срез на потоке компьютера, команды — очередь на
 * поток сервера, чтения профиля — задача главного потока), стоимость и корзина токенов на компьютер §3b.1.4 и лимит
 * 10 команд/с на устройство, квитанции §3b.1.5 с событием {@code pc_command_result}, коды ошибок §3b.1.7, защита от
 * флуда событий §3b.3.2. Права §3b.1.8: ключа МЕСТН/ДИСТ и карт доступа в моде нет — компьютер в сети устройства
 * действует как игрок у щита и не больше (команды — те же кнопки щита, байпасы и уставки защит — только руками), а
 * устройство под замком LOTO — в режиме {@code MAINTENANCE}, команды отклоняются.
 */
public final class CcIntegration {
    private CcIntegration() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(CcIntegration::capabilities);
        DeviceHub.register();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(PeripheralCapability.get(), ScadaBlockEntities.HISTORIAN.get(), (be, side) -> new HistorianPeripheral(be));
        event.registerBlockEntity(PeripheralCapability.get(), ScadaBlockEntities.TWIN_WORKSTATION.get(), (be, side) -> new TwinPeripheral(be));
        event.registerBlockEntity(PeripheralCapability.get(), ScadaBlockEntities.TELEMETRY_GATEWAY.get(),
                (be, side) -> new RtuPeripheral(be));
        event.registerBlockEntity(PeripheralCapability.get(),
                dev.denfry.promptcritical.mod.market.MarketBlockEntities.MARKET_TERMINAL.get(),
                (be, side) -> new MarketPeripheral(be));
        event.registerBlockEntity(PeripheralCapability.get(), dev.denfry.promptcritical.mod.grid.GridBlockEntities.GRID_DISPATCH_CENTER.get(),
                (be, side) -> new GridPeripheral(be));
        event.registerBlockEntity(PeripheralCapability.get(), dev.denfry.promptcritical.mod.mes.MesBlockEntities.MES_SERVER.get(),
                (be, side) -> new MesPeripheral(be));
        // SCADA/HMI 03c: сервер сигнализации
        event.registerBlockEntity(PeripheralCapability.get(), ScadaBlockEntities.ANNUNCIATOR.get(), (be, side) -> new AnnunciatorPeripheral(be));
        event.registerBlockEntity(PeripheralCapability.get(), ScadaBlockEntities.WATCHDOG_RELAY.get(), (be, side) -> new WatchdogPeripheral(be));
        // периферия устройства: на все типы BE мода; профиль решает по самому блоку, без профиля — нет периферии
        for (BlockEntityType<?> type : BuiltInRegistries.BLOCK_ENTITY_TYPE) {
            ResourceLocation id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type);
            if (id == null || !PromptCritical.MOD_ID.equals(id.getNamespace())) continue;
            event.registerBlockEntity(PeripheralCapability.get(), (BlockEntityType) type, (be, side) -> device((BlockEntity) be));
        }
    }

    private static DevicePeripheral device(BlockEntity be) {
        if (be.getLevel() == null || be.getLevel().isClientSide) return null;
        DeviceRuntime r = DeviceHub.runtime(be);
        return r == null ? null : new DevicePeripheral(r);
    }
}
