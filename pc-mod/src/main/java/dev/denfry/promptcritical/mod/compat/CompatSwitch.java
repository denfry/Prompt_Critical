package dev.denfry.promptcritical.mod.compat;

import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.mod.config.ServerConfig.BodyTemperatureOwner;
import dev.denfry.promptcritical.mod.config.ServerConfig.CompatMode;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Включатели интеграций {@code mod.compat.*} (16 §16.14). Интеграция работает, когда мод загружен и ключ не
 * {@code off}; до загрузки серверного конфига действует значение по умолчанию {@code auto}.
 */
public final class CompatSwitch {
    /** Моды, которые ведут свою температуру тела и наносят свой урон от холода (16 §16.10.1). */
    static final String[] BODY_TEMPERATURE_MODS = {"coldsweat", "toughasnails"};

    private CompatSwitch() {}

    public static boolean active(ModConfigSpec.EnumValue<CompatMode> key, String modId) {
        return ModList.get().isLoaded(modId) && value(key, CompatMode.AUTO) != CompatMode.OFF;
    }

    /** Переохлаждение R49 уступлено чужому моду (R60): иначе игрок получал бы урон от холода дважды. */
    public static boolean bodyTemperatureIsExternal() {
        return switch (value(ServerConfig.COMPAT_BODY_TEMPERATURE, BodyTemperatureOwner.AUTO)) {
            case PROMPTCRITICAL -> false;
            case EXTERNAL -> true;
            case AUTO -> {
                for (String id : BODY_TEMPERATURE_MODS)
                    if (ModList.get().isLoaded(id)) yield true;
                yield false;
            }
        };
    }

    private static <E extends Enum<E>> E value(ModConfigSpec.EnumValue<E> key, E fallback) {
        return ServerConfig.SPEC.isLoaded() ? key.get() : fallback;
    }
}
