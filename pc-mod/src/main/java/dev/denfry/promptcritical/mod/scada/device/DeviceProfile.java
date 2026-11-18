package dev.denfry.promptcritical.mod.scada.device;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Профиль устройства (03b §3b.7.2 {@code DeviceDescriptor}, решение R13): тип периферии CC, подтип, команды профиля
 * (кнопки щита под понятными именами), чтения профиля, «аппаратные» имена, которые есть в API лишь ради понятной
 * ошибки {@code E_HARDWARE_ONLY}, и зонд событий. Общие методы {@code pc_device} есть у всех профилей.
 */
public final class DeviceProfile {
    /**
     * Чтение профиля: исполняется на потоке сервера (класс W 03b §3b.1.3) со свежим срезом {@code s}; аргументы Lua
     * не проверены — проверяет само чтение ({@link LuaArgs}).
     */
    @FunctionalInterface
    public interface Read {
        Object read(BlockEntity be, DeviceRuntime.Snapshot s, Object[] args) throws DeviceException;
    }

    /** Команда профиля без кнопки: проверка аргументов на потоке компьютера, действие — на потоке сервера. */
    @FunctionalInterface
    public interface Command {
        Action prepare(Object[] args) throws DeviceException;
    }

    /** Исполнение команды на потоке сервера; отказ механизма — {@link DeviceException}. */
    @FunctionalInterface
    public interface Action {
        void apply(BlockEntity be) throws DeviceException;
    }

    /** Источник событий устройства; держит своё прошлое состояние, вызывается каждый тик на потоке сервера. */
    public interface Probe {
        void tick(BlockEntity be, DeviceRuntime.Snapshot snapshot, Emitter out);
    }

    public interface Emitter {
        void emit(String event, Map<String, Object> payload);

        /** Lua подавал команду {@code channel} не раньше {@code ticks} тиков назад. */
        boolean luaRequested(String channel, long ticks);
    }

    /** Чтение с ценой в токенах (03b §3b.1.4). */
    public record CostedRead(double cost, Read read) {
    }

    public final String type, subtype;
    public final Set<String> additionalTypes;
    /** Имя метода Lua → кнопка щита. */
    public final Map<String, Integer> buttonCommands;
    /** Кнопки щита, которые Lua не нажимает (байпасы, уставки защит, выгрузка в руки и т.п.). */
    public final Set<Integer> hardwareButtons;
    /** Lua не нажимает ни одной кнопки щита (ПАЗ: только чтение, 03d §3d.14.2). */
    public final boolean readOnly;
    public final Map<String, CostedRead> reads;
    public final Map<String, Command> commands;
    public final Set<String> hardwareMethods;
    @Nullable
    public final Supplier<Probe> probe;

    private DeviceProfile(Builder b) {
        type = b.type;
        subtype = b.subtype;
        Set<String> extra = new LinkedHashSet<>(b.additionalTypes);
        extra.add("pc_device");
        extra.remove(type);
        additionalTypes = Collections.unmodifiableSet(extra);
        buttonCommands = Collections.unmodifiableMap(b.buttonCommands);
        hardwareButtons = Set.copyOf(b.hardwareButtons);
        readOnly = b.readOnly;
        reads = Collections.unmodifiableMap(b.reads);
        commands = Collections.unmodifiableMap(b.commands);
        hardwareMethods = Collections.unmodifiableSet(b.hardwareMethods);
        probe = b.probe;
    }

    public static Builder builder(String type, String subtype) {
        return new Builder(type, subtype);
    }

    public static final class Builder {
        private final String type, subtype;
        private final Set<String> additionalTypes = new LinkedHashSet<>();
        private final Map<String, Integer> buttonCommands = new LinkedHashMap<>();
        private final Set<Integer> hardwareButtons = new LinkedHashSet<>();
        private boolean readOnly;
        private final Map<String, CostedRead> reads = new LinkedHashMap<>();
        private final Map<String, Command> commands = new LinkedHashMap<>();
        private final Set<String> hardwareMethods = new LinkedHashSet<>();
        private Supplier<Probe> probe;

        private Builder(String type, String subtype) {
            this.type = type;
            this.subtype = subtype;
        }

        public Builder also(String... types) {
            additionalTypes.addAll(List.of(types));
            return this;
        }

        public Builder button(String method, int buttonId) {
            buttonCommands.put(method, buttonId);
            return this;
        }

        public Builder hardwareButtons(int... ids) {
            for (int id : ids) hardwareButtons.add(id);
            return this;
        }

        public Builder readOnly() {
            readOnly = true;
            return this;
        }

        public Builder read(String method, double cost, Read read) {
            reads.put(method, new CostedRead(cost, read));
            return this;
        }

        public Builder command(String method, Command command) {
            commands.put(method, command);
            return this;
        }

        public Builder hardware(String... methods) {
            hardwareMethods.addAll(List.of(methods));
            return this;
        }

        public Builder probe(Supplier<Probe> probe) {
            this.probe = probe;
            return this;
        }

        public DeviceProfile build() {
            return new DeviceProfile(this);
        }
    }
}
