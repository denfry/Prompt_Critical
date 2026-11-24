package dev.denfry.promptcritical.mod.api;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Which station a place belongs to, and which devices a station has: the one seam between the addon API and the station
 * model of 04d §4d.9. {@link DeviceStations} is the stand-in until stations exist; the station implementation replaces it
 * with {@link ApiEvents#stations(StationDirectory)}. Server thread only.
 */
public interface StationDirectory {
    /** Station of block {@code pos}, or {@link dev.denfry.promptcritical.api.ApiEvent#NO_STATION}. */
    UUID stationOf(ServerLevel level, BlockPos pos);

    /** Loaded device block entities of station {@code station} in {@code level}; empty if there are none. */
    List<BlockEntity> devices(ServerLevel level, UUID station);

    /** Stations of {@code level} with at least one loaded device. */
    List<UUID> stations(ServerLevel level);
}
