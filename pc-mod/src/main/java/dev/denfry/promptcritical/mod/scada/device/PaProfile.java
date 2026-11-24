package dev.denfry.promptcritical.mod.scada.device;

import dev.denfry.promptcritical.mod.pa.IntercomStationBlockEntity;
import dev.denfry.promptcritical.mod.pa.PaChannel;
import dev.denfry.promptcritical.mod.pa.PaMessage;
import dev.denfry.promptcritical.mod.pa.PaPanels;
import dev.denfry.promptcritical.mod.pa.PlantLoudspeakerBlockEntity;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.util.StringUtil;

/**
 * Периферия пульта ГГС {@code pc_ops} (13 §13.3.4, §13.11.1): предзаписанное сообщение и текстовый вызов из Lua. Вызов
 * идёт от пульта, к которому подключён компьютер: громкоговорители его команды, его питание и его пауза между
 * вызовами ({@code ops.pa_page_cooldown_s}, общая со щитом). Текст проверяется как в поле щита: без управляющих
 * символов, не пустой и не длиннее строки субтитра.
 *
 * <p>Отклонение от §13.3.4: приоритет сообщения задан его каналом, а не аргументом; ролей 04d в коде пока нет, поэтому
 * {@code SHIFT_SUPERVISOR} и {@code OPERATOR} не проверяются.
 *
 * <pre>
 * local pa = peripheral.find("pc_ops")
 * pa.page("CONTROL", 3, "Pump 3 to maintenance")   -- канал, зона, текст
 * pa.announce("FIRE", 3)                           -- EVACUATE, FIRE, GAS, ALL_CLEAR; зона
 * </pre>
 */
public final class PaProfile {
    private PaProfile() {
    }

    static DeviceProfile build() {
        return DeviceProfile.builder("pc_ops", "PA")
                .command("page", a -> {
                    PaChannel channel = named(PaChannel.class, a, 0);
                    int zone = LuaArgs.integer(a, 1, 1, PlantLoudspeakerBlockEntity.MAX_ZONE);
                    String text = text(a, 2);
                    return be -> {
                        IntercomStationBlockEntity.CallResult r = station(be).call(channel, zone, text);
                        if (r.refusal() != null) throw switch (r.refusal()) {
                            case NO_POWER -> new DeviceException(DeviceException.FAULT, "PA station has no power");
                            case WAIT -> new DeviceException(DeviceException.RATE, "next call in " + (r.waitTicks() + 19) / 20 + " s");
                            case NOT_SENT -> notSent();
                        };
                    };
                })
                .command("announce", a -> {
                    PaMessage msg = named(PaMessage.class, a, 0);
                    int zone = LuaArgs.integer(a, 1, 1, PlantLoudspeakerBlockEntity.MAX_ZONE);
                    return be -> {
                        int sent = station(be).announce(zone, msg);
                        if (sent < 0) throw new DeviceException(DeviceException.FAULT, "PA station has no power");
                        if (sent == 0) throw notSent();
                    };
                }).build();
    }

    private static DeviceException notSent() {
        return new DeviceException(DeviceException.BUSY, "no loudspeakers in the zone, or a higher-priority message is on air");
    }

    private static IntercomStationBlockEntity station(net.minecraft.world.level.block.entity.BlockEntity be) throws DeviceException {
        if (be instanceof IntercomStationBlockEntity ic) return ic;
        throw new DeviceException(DeviceException.UNLOADED, "PA station is gone");
    }

    /** Имя канала или сообщения без учёта регистра. */
    private static <E extends Enum<E>> E named(Class<E> type, Object[] a, int i) throws DeviceException {
        String s = LuaArgs.string(a, i).toUpperCase(Locale.ROOT);
        for (E e : type.getEnumConstants()) if (e.name().equals(s)) return e;
        throw LuaArgs.arg(i, "one of " + Arrays.toString(type.getEnumConstants()));
    }

    /** Текст вызова, как его чистит поле щита ({@code PanelMenu.panelText}); длиннее строки субтитра — отказ, а не обрезка. */
    static String text(Object[] a, int i) throws DeviceException {
        String text = StringUtil.filterText(LuaArgs.string(a, i)).strip();
        if (text.isEmpty()) throw LuaArgs.arg(i, "non-empty text");
        if (text.length() > PaPanels.PAGE_MAX_CHARS)
            throw new DeviceException(DeviceException.RANGE, "argument #" + (i + 1) + " longer than " + PaPanels.PAGE_MAX_CHARS);
        return text;
    }
}
