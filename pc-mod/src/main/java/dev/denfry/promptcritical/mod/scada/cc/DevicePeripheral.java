package dev.denfry.promptcritical.mod.scada.cc;

import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IDynamicPeripheral;
import dan200.computercraft.api.peripheral.IPeripheral;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.scada.device.DeviceException;
import dev.denfry.promptcritical.mod.scada.device.DeviceRuntime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * Периферия устройства (03b §3b.2, 03d §3d.14): тип профиля ({@code pc_reactor}, {@code pc_pump}, {@code pc_plc},
 * {@code pc_sis}, {@code pc_well}, ...) и общий {@code pc_device}. Методы — имена профиля ({@link DeviceRuntime}); этот
 * класс только переводит Lua ↔ Java: чтения среза и команды идут на потоке компьютера, чтения профиля — задачей
 * главного потока ({@link ILuaContext#executeMainThreadTask}). Ошибка устройства — ошибка Lua
 * {@code "<CODE>: <detail>"}; внутренняя ошибка Java не роняет ни поток компьютера, ни сервер.
 *
 * <pre>
 * local r = peripheral.find("pc_reactor")
 * print(r.getStatus().state)
 * local id = r.watch("npp.power", -math.huge, 1.05, 0.02)
 * local ticket = r.requestTrip("high power")
 * local ev, side, p = os.pullEvent("pc_command_result")
 * </pre>
 */
public final class DevicePeripheral implements IDynamicPeripheral {
    /** Ни один метод не принимает больше шести аргументов; лишнее не копируем. */
    private static final int MAX_ARGS = 8;

    private final DeviceRuntime runtime;
    private final String[] methods;

    public DevicePeripheral(DeviceRuntime runtime) {
        this.runtime = runtime;
        this.methods = runtime.methodNames().toArray(String[]::new);
    }

    @Override
    public String getType() {
        return runtime.profile().type;
    }

    @Override
    public Set<String> getAdditionalTypes() {
        return runtime.profile().additionalTypes;
    }

    @Override
    public Object getTarget() {
        return runtime.blockEntity();
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof DevicePeripheral d && d.runtime == runtime;
    }

    @Override
    public String[] getMethodNames() {
        return methods;
    }

    private record Client(IComputerAccess computer) implements DeviceRuntime.Client {
        @Override
        public Object key() {
            return computer;
        }

        @Override
        public int computerId() {
            return computer.getID();
        }

        @Override
        public void queue(String event, Map<String, Object> payload) {
            try {
                computer.queueEvent(event, computer.getAttachmentName(), payload);
            } catch (RuntimeException e) {
                // компьютер успел отключиться между тиком и доставкой: событие ему уже не нужно
            }
        }
    }

    @Override
    public void attach(IComputerAccess computer) {
        runtime.attach(new Client(computer));
    }

    @Override
    public void detach(IComputerAccess computer) {
        runtime.detach(computer);
    }

    @Override
    public MethodResult callMethod(IComputerAccess computer, ILuaContext context, int method, IArguments arguments) throws LuaException {
        if (method < 0 || method >= methods.length) throw new LuaException(DeviceException.ARG + ": no such method");
        String name = methods[method];
        if (arguments.count() > MAX_ARGS) throw new LuaException(DeviceException.ARG + ": too many arguments");
        Object[] args = arguments.getAll();
        Client client = new Client(computer);
        try {
            double cost = runtime.mainThreadCost(name);
            if (cost >= 0) {
                runtime.charge(client, cost);
                return context.executeMainThreadTask(() -> {
                    try {
                        return runtime.readOnServer(name, args);
                    } catch (DeviceException e) {
                        throw new LuaException(e.getMessage());
                    } catch (RuntimeException e) {
                        PromptCritical.LOGGER.warn("CC read {} failed", name, e);
                        throw new LuaException(DeviceException.FAULT + ": internal error");
                    }
                });
            }
            return MethodResult.of(runtime.call(client, name, args));
        } catch (DeviceException e) {
            throw new LuaException(e.getMessage());
        } catch (RuntimeException e) {
            PromptCritical.LOGGER.warn("CC call {} failed", name, e);
            throw new LuaException(DeviceException.FAULT + ": internal error");
        }
    }

    /** Для тестов: методы Lua этой периферии. */
    public List<String> methods() {
        return List.of(methods);
    }
}
