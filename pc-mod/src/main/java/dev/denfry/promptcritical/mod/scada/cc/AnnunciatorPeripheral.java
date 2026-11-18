package dev.denfry.promptcritical.mod.scada.cc;

import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dev.denfry.promptcritical.mod.scada.AnnunciatorBlockEntity;
import dev.denfry.promptcritical.mod.scada.AnnunciatorBlockEntity.AnnunciatorException;
import dev.denfry.promptcritical.sim.alarm.Alarm;
import dev.denfry.promptcritical.sim.alarm.AlarmEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

/**
 * Периферия {@code pc_annunciator} — сервер сигнализации ISA-18.2 (03b §3b.2.14, автомат 03c §3c.6.2) поверх
 * {@link AnnunciatorBlockEntity}. Команды возвращают число или логическое вместо {@code ticket} 03b: тикетов у
 * устройств мода пока нет, команда исполняется сразу на потоке сервера. Права ({@code OPERATOR},
 * {@code SHIFT_SUPERVISOR}, {@code ENGINEER}) не проверяются — ключ-карт нет; запрет шельфинга P1 ({@code E_ACCESS})
 * действует. Расширения сверх 03b: {@code getTags()}, {@code readMany(tags)} (пакетное чтение для {@code pcui}),
 * {@code reset(id)} для самоподхвата, {@code reportScreen(level, tags)} — экран {@code pcui} сообщает о себе.
 *
 * <p>События: {@code pc_alarm}, {@code pc_alarm_clear}, {@code pc_alarm_ack}, {@code pc_alarm_flood}; первый аргумент —
 * имя периферии, второй — таблица.
 *
 * <pre>
 * local ann = peripheral.find("pc_annunciator")
 * for _, a in ipairs(ann.getAlarms({state = "UNACK"})) do print(a.priority, a.message) end
 * ann.acknowledge("ALL")
 * local id = ann.defineAlarm({kind = "SOFT", priority = 2, message = "Feed pump not running"})
 * ann.raise(id)
 * </pre>
 */
public final class AnnunciatorPeripheral implements IPeripheral {
    private final AnnunciatorBlockEntity ann;

    public AnnunciatorPeripheral(AnnunciatorBlockEntity ann) {
        this.ann = ann;
    }

    @Override
    public String getType() {
        return "pc_annunciator";
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof AnnunciatorPeripheral p && p.ann == ann;
    }

    @Override
    public Object getTarget() {
        return ann;
    }

    @Override
    public void attach(IComputerAccess computer) {
        ann.sink(computer, e -> event(computer, e));
    }

    @Override
    public void detach(IComputerAccess computer) {
        ann.sink(computer, null);
    }

    private void event(IComputerAccess computer, AlarmEvent e) {
        Map<String, Object> m = new LinkedHashMap<>();
        String name;
        switch (e.type()) {
            case RAISE -> {
                name = "pc_alarm";
                Alarm a = ann.engine().get(e.id());
                if (a != null) m.putAll(ann.describe(a));
            }
            case CLEAR -> {
                name = "pc_alarm_clear";
                Alarm a = ann.engine().get(e.id());
                m.put("id", e.id());
                if (a != null) m.put("tag", a.definition().tag());
            }
            case ACK -> {
                name = "pc_alarm_ack";
                m.put("id", e.id());
            }
            case FLOOD_ON, FLOOD_OFF -> {
                name = "pc_alarm_flood";
                m.put("active", e.type() == AlarmEvent.Type.FLOOD_ON);
                m.put("rate10min", (int) e.value());
            }
            default -> {
                return;
            }
        }
        if (e.type() != AlarmEvent.Type.FLOOD_ON && e.type() != AlarmEvent.Type.FLOOD_OFF) m.put("state", e.state().name());
        computer.queueEvent(name, computer.getAttachmentName(), m);
    }

    private static <T> T call(Supplier<T> f) throws LuaException {
        try {
            return f.get();
        } catch (AnnunciatorException e) {
            throw new LuaException(e.getMessage());
        }
    }

    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> getAlarms(IArguments args) throws LuaException {
        Map<?, ?> filter = args.optTable(0).orElse(null);
        return call(() -> ann.getAlarms(filter));
    }

    @LuaFunction(mainThread = true)
    public final int acknowledge(IArguments args) throws LuaException {
        Object target = args.get(0);
        String group = args.optString(1).orElse(null);
        return call(() -> ann.acknowledge(target, group));
    }

    @LuaFunction(mainThread = true)
    public final boolean silence() throws LuaException {
        return call(() -> {
            ann.silenceLua();
            return true;
        });
    }

    @LuaFunction(mainThread = true)
    public final boolean shelve(int id, double seconds) throws LuaException {
        return call(() -> ann.shelve(id, seconds));
    }

    @LuaFunction(mainThread = true)
    public final boolean unshelve(int id) throws LuaException {
        return call(() -> ann.unshelve(id));
    }

    @LuaFunction(mainThread = true)
    public final boolean reset(int id) throws LuaException {
        return call(() -> ann.engine().reset(id, ann.engine().now()));
    }

    @LuaFunction(mainThread = true)
    public final int defineAlarm(Map<?, ?> spec) throws LuaException {
        return call(() -> ann.defineAlarm(spec));
    }

    @LuaFunction(mainThread = true)
    public final boolean deleteAlarm(int id) throws LuaException {
        return call(() -> ann.deleteAlarm(id));
    }

    @LuaFunction(mainThread = true)
    public final boolean raise(IArguments args) throws LuaException {
        int id = args.getInt(0);
        double value = args.optDouble(1, 1);
        return call(() -> {
            ann.raise(id, value);
            return true;
        });
    }

    @LuaFunction(mainThread = true)
    public final boolean clear(int id) throws LuaException {
        return call(() -> {
            ann.clear(id);
            return true;
        });
    }

    @LuaFunction(mainThread = true)
    public final boolean setSuppression(String group, boolean active) throws LuaException {
        return call(() -> {
            ann.setSuppression(group, active);
            return true;
        });
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Object> getStatistics() throws LuaException {
        return call(ann::getStatistics);
    }

    @LuaFunction(mainThread = true)
    public final boolean setWindow(int n, int alarmId) throws LuaException {
        return call(() -> {
            ann.setWindow(n, alarmId);
            return true;
        });
    }

    @LuaFunction(mainThread = true)
    public final boolean lampTest() throws LuaException {
        return call(() -> {
            ann.lampTestLua();
            return true;
        });
    }

    /** {@code reportScreen(level, tags)}: экран {@code pcui} сообщает уровень страницы и число её тегов (достижение {@code hmi}). */
    @LuaFunction(mainThread = true)
    public final boolean reportScreen(int level, int tags) throws LuaException {
        return call(() -> {
            ann.reportScreen(level, tags);
            return true;
        });
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Object> getTags() throws LuaException {
        return call(ann::getTags);
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Object> readMany(Map<?, ?> tags) throws LuaException {
        return call(() -> ann.readMany(List.copyOf(tags.values())));
    }
}
