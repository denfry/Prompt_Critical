package dev.denfry.promptcritical.mod.station;

import dev.denfry.promptcritical.mod.api.ApiDevices;
import dev.denfry.promptcritical.mod.api.DeviceStations;
import dev.denfry.promptcritical.mod.api.StationDirectory;
import dev.denfry.promptcritical.sim.journal.StationRecord;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Станции 04d §4d.9 для API дополнений. Устройство в чанке станции принадлежит ей; устройство вне станций остаётся
 * станцией из одного устройства, как у {@link DeviceStations}, чтобы события таких установок не слились в одну.
 */
final class RegistryStationDirectory implements StationDirectory {
    private final DeviceStations loose = new DeviceStations();

    @Override
    public UUID stationOf(ServerLevel level, BlockPos pos) {
        StationRecord s = Stations.stationAt(level, pos);
        return s != null ? s.stationId() : loose.stationOf(level, pos);
    }

    @Override
    public List<BlockEntity> devices(ServerLevel level, UUID station) {
        StationRegistry r = StationRegistry.of(level.getServer());
        StationRecord s = r == null ? null : r.get(station);
        if (s == null) return loose.devices(level, station);
        if (!r.dimensionName(s.dimension()).equals(level.dimension().location().toString())) return List.of();
        List<BlockEntity> out = new ArrayList<>();
        for (BlockEntity be : ApiDevices.loaded(level))
            if (s.chunks().contains(new ChunkPos(be.getBlockPos()).toLong())) out.add(be);
        return out;
    }

    @Override
    public List<UUID> stations(ServerLevel level) {
        Set<UUID> out = new LinkedHashSet<>();
        for (BlockEntity be : ApiDevices.loaded(level)) out.add(stationOf(level, be.getBlockPos()));
        return new ArrayList<>(out);
    }
}
