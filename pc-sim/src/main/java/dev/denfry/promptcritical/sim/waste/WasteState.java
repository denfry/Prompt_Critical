package dev.denfry.promptcritical.sim.waste;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.radiation.ActivityInventory;
import dev.denfry.promptcritical.sim.radiation.Emitter;
import java.util.EnumMap;
import java.util.Map;

/** Запись составов отходов по именам нуклидов: новый нуклид в таблице не ломает старые сохранения. */
final class WasteState {
    private WasteState() {
    }

    static void putActivities(StateWriter out, String key, Map<Emitter, Double> bq) {
        out.beginObject(key);
        for (Map.Entry<Emitter, Double> e : bq.entrySet()) out.putDouble(e.getKey().name(), e.getValue());
        out.endObject();
    }

    static EnumMap<Emitter, Double> getActivities(StateReader in, String key) {
        EnumMap<Emitter, Double> m = new EnumMap<>(Emitter.class);
        if (!in.has(key)) return m;
        in.beginObject(key);
        for (String k : in.keys()) {
            try {
                m.put(Emitter.valueOf(k), in.getDouble(k));
            } catch (IllegalArgumentException ignored) {
                // нуклид из другой версии — пропускаем
            }
        }
        in.endObject();
        return m;
    }

    static void putInventory(StateWriter out, String key, WasteInventory w) {
        out.beginObject(key);
        out.putDouble("mass", w.massKg());
        out.putDouble("volume", w.volumeM3());
        putActivities(out, "gamma", w.emitters().activities());
        out.beginObject("long");
        for (Map.Entry<LongLivedNuclide, Double> e : w.longLived().entrySet()) out.putDouble(e.getKey().name(), e.getValue());
        out.endObject();
        out.endObject();
    }

    static WasteInventory getInventory(StateReader in, String key) {
        if (!in.has(key)) return WasteInventory.EMPTY;
        in.beginObject(key);
        double mass = in.optDouble("mass", 0), vol = in.optDouble("volume", 0);
        EnumMap<Emitter, Double> g = getActivities(in, "gamma");
        EnumMap<LongLivedNuclide, Double> l = new EnumMap<>(LongLivedNuclide.class);
        if (in.has("long")) {
            in.beginObject("long");
            for (String k : in.keys()) {
                try {
                    l.put(LongLivedNuclide.valueOf(k), in.getDouble(k));
                } catch (IllegalArgumentException ignored) {
                    // нуклид из другой версии — пропускаем
                }
            }
            in.endObject();
        }
        in.endObject();
        return new WasteInventory(new ActivityInventory(g, mass, vol > 0 ? mass / (vol * 1000) : 1), l, vol);
    }
}
