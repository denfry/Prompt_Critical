package dev.denfry.promptcritical.mod.ops;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.sim.ops.EnergyKind;
import dev.denfry.promptcritical.sim.ops.LockoutRegistry.LockResult;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Тексты LOTO (13 §13.1): ключи перевода и их en/ru значения для datagen. */
public final class LockoutText {
    private static final String P = "message." + PromptCritical.MOD_ID + ".lockout.";
    /** Ключ → {английский, русский}. */
    public static final Map<String, String[]> LANG = new LinkedHashMap<>();
    /** Текст бирки (13 §13.1.2) — в реестре хранится как есть, на языке сервера не переводится. */
    static final String TAG_TEXT = "Do not operate! People at work / Не включать! Работают люди";

    static {
        m("locked", "LOCKED OUT: %s", "ЗАБЛОКИРОВАНО: %s");
        m("breaker_locked", "LOCKED_OUT: a LOTO lock holds the drive", "LOCKED_OUT: привод заперт замком LOTO");
        m("hung", "Lock on: the point is held in its isolated position", "Замок повешен: точка заперта в изолированном положении");
        m("hung_tagged", "Lock and tag on: «Do not operate! People at work»",
                "Замок и бирка повешены: «Не включать! Работают люди»");
        m("removed", "Your lock is off: the point is back to isolated, it can be put in service",
                "Ваш замок снят: точка снова только изолирована, её можно вернуть в работу");
        m("removed_others", "Your lock is off; still locked by %s", "Ваш замок снят; точку ещё держат: %s");
        m("hasp_fitted", "Hasp fitted: up to six crew locks", "Накладка поставлена: до шести замков бригады");
        m("hasp_removed", "Hasp removed", "Накладка снята");
        m("tester_live", "Voltage tester: %s present", "Указатель: %s есть");
        m("tester_dead", "Voltage tester: no %s", "Указатель: %s нет");
        m("verified", "%s — absence of energy verified", "%s — отсутствие энергии проверено");
        m("tag_only", "A tag without a lock is only a warning: hang a padlock with the tag in the other hand",
                "Бирка без замка — только предупреждение: вешайте замок, держа бирку в другой руке");
        m("tamper", "TAMPERING WITH ISOLATION: %s broke a locked point at %s", "ВМЕШАТЕЛЬСТВО В ИЗОЛЯЦИЮ: %s — сломана запертая точка у %s");
        m("admin_cut", "Administrator %s cut the locks of %s at %s: %s", "Администратор %s срезал замки (%s) у %s: %s");
        m("result.ok", "Done", "Готово");
        m("result.not_isolated", "Isolate it first: open the breaker, disengage the coupling", "Сначала изолируйте: отключите аппарат, расцепите муфту");
        m("result.no_room", "There is a lock already: fit a hasp for the crew's locks", "Замок уже висит: для замков бригады поставьте накладку");
        m("result.limit", "All your padlocks are already hung", "Все ваши замки уже развешаны");
        m("result.not_yours", "Not your lock: only its owner takes it off", "Замок не ваш: снять его может только владелец");
        m("result.not_locked", "There is no lock on this point", "На точке нет замка");
        m("result.energy_present", "Energy is still present", "Энергия ещё есть");
        m("result.keys_in_box", "Your keys are in the lockout box and the crew still holds it", "Ваши ключи в ящике блокировки, а бригада ещё держит его");
        m("engaged", "Coupling engaged: the shaft drives through", "Муфта сцеплена: вал передаёт вращение");
        m("disengaged", "Coupling disengaged: the shaft beyond it stops", "Муфта расцеплена: вал за ней остановится");
        m("blind_open", "Line blind open: the line passes", "Заглушка открыта: линия проходит");
        m("blind_closed", "Line blind turned to blind: the line is cut positively", "Заглушка повёрнута глухой стороной: линия отсечена");
        m("keys_in", "Keys of your locks put in the box: the crew hangs their locks on it", "Ключи от ваших замков в ящике: бригада вешает свои замки на него");
        m("keys_out", "Keys taken from the box", "Ключи забраны из ящика");
        m("box_status", "Lockout box: %s locks on it, keys of %s inside", "Ящик блокировки: замков на нём %s, внутри ключи: %s");
        m("result.hasp_busy", "The hasp is already fitted or still holds locks", "Накладка уже стоит или на ней ещё замки");
        m("tip.padlock", "Personal lock: right-click an isolated point; Shift + empty hand takes your own lock off",
                "Личный замок: ПКМ по изолированной точке; Shift + пустая рука снимает свой замок");
        m("tip.tag", "Hold in the other hand when hanging a padlock", "Держите в другой руке, когда вешаете замок");
        m("tip.hasp", "Right-click a point: it takes up to six locks, returned to service only when the last is off",
                "ПКМ по точке: принимает до шести замков, в работу точка вернётся только после последнего");
        m("tip.voltage_tester", "Right-click a breaker or disconnector: voltage on the load side; on a locked point verifies its absence",
                "ПКМ по выключателю или разъединителю: напряжение на стороне присоединения; на запертой точке — проверка отсутствия");
        for (EnergyKind k : EnergyKind.values()) {
            String[] n = switch (k) {
                case ELECTRICAL -> new String[] {"voltage", "напряжения"};
                case MECHANICAL -> new String[] {"rotation", "вращения"};
                case PRESSURE -> new String[] {"pressure", "давления"};
                case THERMAL -> new String[] {"heat", "нагрева"};
                case CHEMICAL -> new String[] {"hazardous medium", "опасной среды"};
                case RADIATION -> new String[] {"radiation", "излучения"};
                case GRAVITY -> new String[] {"raised load", "поднятого груза"};
            };
            m("energy." + k.name().toLowerCase(Locale.ROOT), n[0], n[1]);
        }
        for (LockResult r : LockResult.values())
            if (!LANG.containsKey(P + "result." + r.name().toLowerCase(Locale.ROOT))) throw new IllegalStateException("no text for " + r);
    }

    private LockoutText() {
    }

    private static void m(String key, String en, String ru) {
        LANG.put(P + key, new String[] {en, ru});
    }

    public static MutableComponent msg(String key, Object... args) {
        return Component.translatable(P + key, args);
    }

    static Component energy(EnergyKind k) {
        return msg("energy." + k.name().toLowerCase(Locale.ROOT));
    }
}
