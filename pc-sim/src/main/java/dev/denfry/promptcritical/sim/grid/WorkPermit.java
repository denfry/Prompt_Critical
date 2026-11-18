package dev.denfry.promptcritical.sim.grid;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Наряд-допуск на работы в электроустановке со снятием напряжения — по «Правилам по охране труда при эксплуатации
 * электроустановок» (ПОТ ЭЭ, приказ Минтруда России от 15.12.2020 № 903н): наряд выписывают на аппарат, которым
 * создан разрыв, и до допуска бригады выполняют технические мероприятия в установленном порядке [сверить номера
 * пунктов 903н]:
 * <ol>
 * <li>отключение и меры против ошибочного включения: аппарат отключён, привод заперт ({@link Measure#SWITCHED_OFF});</li>
 * <li>на приводе вывешен запрещающий плакат «Не включать! Работают люди» ({@link Measure#POSTER});</li>
 * <li>проверено отсутствие напряжения указателем ({@link Measure#NO_VOLTAGE});</li>
 * <li>наложено заземление — у разъединителя заземляющие ножи на стороне присоединения ({@link Measure#EARTHED}).</li>
 * </ol>
 * Пока наряд открыт, аппарат не включается ({@link BreakerResponse#PERMIT}): ни ключом, ни АПВ — у выключателя снят
 * оперативный ток привода. Заземление снимают только после закрытия наряда.
 *
 * <p>У ячейки выключателя своих заземляющих ножей нет, а переносные заземления в игре не моделируются: для неё
 * заземление засчитывается, когда напряжения нет с обеих сторон (обе стороны отделены разъединителями).
 */
public final class WorkPermit {
    /** Порог указателя напряжения до 1000 В: ниже ~50 В индикатор не светится (ГОСТ IEC 61243-3) [сверить]. */
    public static final double NO_VOLTAGE_V = 50;

    public enum Stage {
        /** Выписан, аппарат заперт, плакат висит; идёт подготовка рабочего места. */
        PREPARING,
        /** Технические мероприятия выполнены, бригада допущена. */
        ADMITTED,
        /** Работа окончена, наряд закрыт: аппарат свободен. */
        CLOSED
    }

    /** Технические мероприятия подготовки рабочего места. */
    public enum Measure { SWITCHED_OFF, POSTER, NO_VOLTAGE, EARTHED }

    private final int number;
    private Stage stage;

    private WorkPermit(int number, Stage stage) {
        this.number = number;
        this.stage = Objects.requireNonNull(stage, "stage");
    }

    /** Восстановить наряд из документа (аппарат уже держит его с момента выписки). */
    public static WorkPermit restore(int number, Stage stage) {
        return new WorkPermit(number, stage);
    }

    /** Наряд можно выписать: выключатель или разъединитель, сейчас отключён. */
    public static boolean canIssue(SwitchUnit u) {
        return u != null && u.kind() != SwitchUnit.Kind.FUSE && !u.closed();
    }

    /**
     * Выписать наряд № {@code number} на аппарат: привод запирается, вывешивается плакат.
     *
     * @return наряд или {@code null} — аппарат включён или это предохранитель
     */
    public static WorkPermit issue(SwitchUnit u, int number) {
        if (!canIssue(u)) return null;
        u.holdForPermit();
        return new WorkPermit(number, Stage.PREPARING);
    }

    public int number() {
        return number;
    }

    public Stage stage() {
        return stage;
    }

    /** Невыполненные технические мероприятия на аппарате сейчас. */
    public static Set<Measure> unmet(SwitchUnit u) {
        EnumSet<Measure> out = EnumSet.noneOf(Measure.class);
        if (u.closed()) out.add(Measure.SWITCHED_OFF);
        if (u.openPermits() == 0) out.add(Measure.POSTER);
        boolean disconnector = u.kind() == SwitchUnit.Kind.DISCONNECTOR;
        double v = disconnector ? u.voltageV(1) : Math.max(u.voltageV(0), u.voltageV(1));
        if (v >= NO_VOLTAGE_V) out.add(Measure.NO_VOLTAGE);
        if (disconnector ? !u.earthed() : v >= NO_VOLTAGE_V) out.add(Measure.EARTHED);
        return out;
    }

    /**
     * Допуск бригады: только после всех технических мероприятий.
     *
     * @return невыполненные мероприятия; пусто — допуск дан
     */
    public Set<Measure> admit(SwitchUnit u) {
        if (stage != Stage.PREPARING) return EnumSet.noneOf(Measure.class);
        Set<Measure> missing = unmet(u);
        if (missing.isEmpty()) stage = Stage.ADMITTED;
        return missing;
    }

    /**
     * Закрыть наряд: работа окончена, бригада выведена; аппарат отпускается (если на нём нет других нарядов).
     *
     * @return {@code false} — наряд уже закрыт
     */
    public boolean close(SwitchUnit u) {
        if (stage == Stage.CLOSED) return false;
        stage = Stage.CLOSED;
        if (u != null) u.releasePermit();
        return true;
    }
}
