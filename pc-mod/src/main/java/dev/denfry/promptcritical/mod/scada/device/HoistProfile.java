package dev.denfry.promptcritical.mod.scada.device;

import dev.denfry.promptcritical.mod.mine.MineHoistBlockEntity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Периферия подъёмной машины {@code pc_hoist} (08 §8.2.8, 03b §3b.1): клеть вызывают на посадку, читают её глубину,
 * груз и дверь, ждут событий прибытия и отправления. Посадки в Lua нумеруются с 1 (1 — верхняя), как и всё в Lua.
 * Блокировки (дверь, запас прочности каната, допуск эксплуатации) проверяет сама машина в {@code call}: программа
 * получает отказ с причиной и не может их обойти.
 *
 * <pre>
 * local h = peripheral.find("pc_hoist")
 * h.call(#h.getLandings())            -- клеть вниз, на дно ствола
 * repeat local ev, name, e = os.pullEvent("pc_hoist_arrived") until e.landing == #h.getLandings()
 * </pre>
 */
public final class HoistProfile {
    private HoistProfile() {
    }

    static DeviceProfile build() {
        return DeviceProfile.builder("pc_hoist", "MINE").also("pc_mine")
                .read("getState", 4, (b, s, a) -> state(hoist(b)))
                .read("getLandings", 2, (b, s, a) -> landings(hoist(b)))
                .command("call", a -> {
                    int landing = LuaArgs.integer(a, 0, 1, 64) - 1;
                    return b -> call(hoist(b), landing);
                })
                .probe(Motion::new)
                .build();
    }

    private static MineHoistBlockEntity hoist(BlockEntity be) throws DeviceException {
        if (be instanceof MineHoistBlockEntity h) return h;
        throw new DeviceException(DeviceException.UNFORMED, "not a hoist");
    }

    static Map<String, Object> state(MineHoistBlockEntity h) {
        Map<String, Object> m = new LinkedHashMap<>();
        int at = h.landingAtCage();
        m.put("moving", h.isMoving());
        m.put("landing", at < 0 ? null : at + 1);
        m.put("depth", h.cageDepthM());
        m.put("doorOpen", h.cageDoorOpen());
        m.put("load", h.cageLoadFraction());
        m.put("landings", h.landingDepths().length);
        return m;
    }

    /** Глубины посадок, м, по порядку номеров: {@code getLandings()[1]} — верхняя. */
    static List<Integer> landings(MineHoistBlockEntity h) {
        List<Integer> out = new ArrayList<>();
        for (int d : h.landingDepths()) out.add(d);
        return out;
    }

    private static void call(MineHoistBlockEntity h, int landing) throws DeviceException {
        switch (h.call(landing, null)) {
            case STARTED, ALREADY_HERE -> {
            }
            case DOOR_OPEN -> throw new DeviceException(DeviceException.INTERLOCK, "DOOR_OPEN");
            case FORBIDDEN -> throw new DeviceException(DeviceException.INTERLOCK, "FORBIDDEN");
            case BLOCKED -> throw new DeviceException(DeviceException.INTERLOCK, "BLOCKED");
            case NO_CAGE -> throw new DeviceException(DeviceException.UNFORMED, "NO_CAGE");
            case BAD_LANDING -> throw new DeviceException(DeviceException.RANGE, "BAD_LANDING");
        }
    }

    /** События {@code pc_hoist_departed {from}} и {@code pc_hoist_arrived {landing}}; номера посадок — с 1. */
    static final class Motion implements DeviceProfile.Probe {
        private int lastLanding = Integer.MIN_VALUE;

        @Override
        public void tick(BlockEntity be, DeviceRuntime.Snapshot s, DeviceProfile.Emitter out) {
            if (!(be instanceof MineHoistBlockEntity h)) return;
            int now = h.isMoving() ? -1 : h.landingAtCage();
            if (lastLanding != Integer.MIN_VALUE && now != lastLanding) {
                Map<String, Object> m = new LinkedHashMap<>();
                if (now < 0) {
                    m.put("from", lastLanding < 0 ? null : lastLanding + 1);
                    out.emit("pc_hoist_departed", m);
                } else {
                    m.put("landing", now + 1);
                    out.emit("pc_hoist_arrived", m);
                }
            }
            lastLanding = now;
        }
    }
}
