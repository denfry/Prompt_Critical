package dev.denfry.promptcritical.mod.compat.journeymap;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.content.MapTargetData;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.common.waypoint.Waypoint;
import journeymap.api.v2.common.waypoint.WaypointFactory;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Путевые точки JourneyMap из {@code geological_map} (16 §16.5.1): прочитанная карта ставит точку на центр рудного
 * тела. JourneyMap находит плагин по аннотации; без него класс не загружается. Вызовы — только с клиента.
 */
@JourneyMapPlugin(apiVersion = IClientAPI.API_VERSION)
public final class PcJourneyMapPlugin implements IClientPlugin {
    private static volatile IClientAPI api;

    @Override
    public String getModId() {
        return PromptCritical.MOD_ID;
    }

    @Override
    public void initialize(IClientAPI clientApi) {
        api = clientApi;
    }

    /** Поставить точку на цель карты, если такой ещё нет; {@code false} — JourneyMap не готов. */
    static boolean offer(MapTargetData target, ResourceKey<Level> dimension, String name) {
        IClientAPI a = api;
        if (a == null) return false;
        for (Waypoint w : a.getWaypoints(PromptCritical.MOD_ID))
            if (w.getX() == target.pos().getX() && w.getY() == target.pos().getY() && w.getZ() == target.pos().getZ()
                    && dimension.location().toString().equals(w.getPrimaryDimension())) return true;
        Waypoint w = WaypointFactory.createClientWaypoint(PromptCritical.MOD_ID, target.pos(), name, dimension, true);
        w.setColor(0xC8A040);
        a.addWaypoint(PromptCritical.MOD_ID, w);
        return true;
    }
}
