package dev.denfry.promptcritical.mod.api;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Stand-in station model until 04d §4d.9 lands: every block is its own station, with a name-based UUID of its dimension
 * and position, so the id survives restarts and is the same on every server. A device (reactor, pump, SIS logic solver)
 * is a station of one device; an annunciator or a release point gets its own id too.
 */
public final class DeviceStations implements StationDirectory {
    /** Stable id of the station at {@code pos} in dimension {@code dimension}. */
    public static UUID id(String dimension, BlockPos pos) {
        return UUID.nameUUIDFromBytes(("promptcritical:station/" + dimension + "/" + pos.asLong()).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public UUID stationOf(ServerLevel level, BlockPos pos) {
        return id(level.dimension().location().toString(), pos);
    }

    @Override
    public List<BlockEntity> devices(ServerLevel level, UUID station) {
        String dim = level.dimension().location().toString();
        for (BlockEntity be : ApiDevices.loaded(level)) if (id(dim, be.getBlockPos()).equals(station)) return List.of(be);
        return List.of();
    }

    @Override
    public List<UUID> stations(ServerLevel level) {
        String dim = level.dimension().location().toString();
        Set<UUID> out = new LinkedHashSet<>();
        for (BlockEntity be : ApiDevices.loaded(level)) out.add(id(dim, be.getBlockPos()));
        return new ArrayList<>(out);
    }
}
