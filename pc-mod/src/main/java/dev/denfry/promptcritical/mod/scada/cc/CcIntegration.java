package dev.denfry.promptcritical.mod.scada.cc;

import dan200.computercraft.api.peripheral.PeripheralCapability;
import dev.denfry.promptcritical.mod.scada.ScadaBlockEntities;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/** Мост в CC: Tweaked: шлюз телемеханики — периферия {@code pc_rtu}, биржевой терминал — {@code pc_market}, сервер завода — {@code pc_mes}. Загружается только при установленном CC. */
public final class CcIntegration {
    private CcIntegration() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(CcIntegration::capabilities);
    }

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
    }
}
