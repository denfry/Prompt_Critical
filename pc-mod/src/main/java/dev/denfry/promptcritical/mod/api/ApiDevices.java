package dev.denfry.promptcritical.mod.api;

import dev.denfry.promptcritical.mod.scada.device.DeviceHub;
import dev.denfry.promptcritical.mod.scada.device.DeviceProfiles;
import dev.denfry.promptcritical.mod.scada.device.DeviceRuntime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The devices the addon API knows about: every Prompt Critical block entity with a peripheral profile, registered when
 * it is added to a server level (mixin on {@link Level#addFreshBlockEntities}). While someone listens to API events, the
 * device event probes (scram lamp, SIS state) run here every {@link #PROBE_PERIOD} ticks even when no CC computer is
 * attached; with a computer attached, {@link DeviceHub} already runs them every tick and the API hears the same events.
 */
public final class ApiDevices {
    /** Probe period without a computer attached, ticks: a trip latches until reset, so 0.5 s does not miss one. */
    public static final int PROBE_PERIOD = 10;
    private static final String PACKAGE = "dev.denfry.promptcritical.";
    private static final Set<BlockEntity> KNOWN = Collections.newSetFromMap(new WeakHashMap<>());

    private ApiDevices() {
    }

    public static void register() {
        DeviceHub.register(); // срезы, часы и уборка устройств нужны и без CC
        NeoForge.EVENT_BUS.addListener(ApiDevices::onServerTick);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            synchronized (KNOWN) {
                KNOWN.clear();
            }
        });
    }

    /** Called by the mixin for block entities entering a level; ignores the client and other mods' blocks. */
    public static void added(Level level, Collection<BlockEntity> list) {
        if (!(level instanceof ServerLevel)) return;
        for (BlockEntity be : list) {
            if (!be.getClass().getName().startsWith(PACKAGE)) continue;
            synchronized (KNOWN) {
                KNOWN.add(be);
            }
        }
    }

    /** Loaded devices of {@code level} that have a peripheral profile. Server thread. */
    public static List<BlockEntity> loaded(ServerLevel level) {
        List<BlockEntity> out = new ArrayList<>();
        synchronized (KNOWN) {
            KNOWN.removeIf(BlockEntity::isRemoved);
            for (BlockEntity be : KNOWN) {
                if (be.getLevel() != level || level.getBlockEntity(be.getBlockPos()) != be) continue;
                if (DeviceProfiles.of(be) != null) out.add(be);
            }
        }
        return out;
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (!ApiEvents.active()) return;
        long now = event.getServer().getTickCount();
        if (now % PROBE_PERIOD != 0) return;
        List<BlockEntity> devices = new ArrayList<>();
        synchronized (KNOWN) {
            KNOWN.removeIf(BlockEntity::isRemoved);
            devices.addAll(KNOWN);
        }
        for (BlockEntity be : devices) {
            if (!(be.getLevel() instanceof ServerLevel)) continue;
            DeviceRuntime r = DeviceHub.runtime(be);
            // с компьютером устройство уже тикает в DeviceHub каждый тик
            if (r != null && r.hasProbe() && !r.hasClients()) r.tick(now);
        }
    }
}
