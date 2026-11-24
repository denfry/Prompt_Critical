package dev.denfry.promptcritical.mod.scada.cc;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dev.denfry.promptcritical.mod.station.Stations;
import dev.denfry.promptcritical.sim.journal.Permission;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Права станции для вызовов Lua (04d §4d.9.2): у игрока, поставившего компьютер. Только поток сервера. */
final class CcAccess {
    private CcAccess() {
    }

    /**
     * Бросает {@code E_ACCESS}, если владельцу компьютера не хватает права на станции устройства {@code be}.
     *
     * @param journal писать ли принятую команду в журнал (квитирование и сброс сигнализации — нет, скрипт зовёт их
     *                часто и вытеснил бы журнал)
     */
    static void gate(IComputerAccess computer, BlockEntity be, Permission p, String command, boolean journal)
            throws LuaException {
        if (!(be.getLevel() instanceof ServerLevel sl)) return;
        String denied = Stations.allowComputer(sl, be.getBlockPos(), computer.getID(),
                Stations.computerOwner(sl.getServer(), computer.getID()), "", p, command, journal);
        if (denied != null) throw new LuaException(denied);
    }
}
