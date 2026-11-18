package dev.denfry.promptcritical.mod.scada.device;

/**
 * Отказ вызова периферии устройства: код из 03b §3b.1.7 и пояснение. Периферия CC отдаёт его Lua ошибкой
 * {@code "<CODE>: <detail>"}, которую программа ловит {@code pcall}.
 */
public final class DeviceException extends Exception {
    public static final String ACCESS = "E_ACCESS", HARDWARE_ONLY = "E_HARDWARE_ONLY", RANGE = "E_RANGE", ARG = "E_ARG",
            RATE = "E_RATE", INTERLOCK = "E_INTERLOCK", BUSY = "E_BUSY", FAULT = "E_FAULT", UNFORMED = "E_UNFORMED",
            UNLOADED = "E_UNLOADED", UNKNOWN_TAG = "E_UNKNOWN_TAG";

    private final String code;

    public DeviceException(String code, String detail) {
        super(code + ": " + detail, null, false, false);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
