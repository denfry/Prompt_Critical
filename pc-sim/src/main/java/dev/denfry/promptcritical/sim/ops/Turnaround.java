package dev.denfry.promptcritical.sim.ops;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import java.util.EnumSet;
import java.util.Set;

/**
 * Остановочный ремонт установки (13 §13.6.2): событие из фаз {@link TurnaroundPhase}, которое игрок проводит от
 * останова до пуска. Переход в следующую фазу — по команде игрока, когда выполнены условия фазы; мир считает их и
 * подаёт {@link Inputs}.
 *
 * <table>
 * <caption>Условия перехода</caption>
 * <tr><th>из</th><th>в</th><th>условие</th></tr>
 * <tr><td>PLANNING</td><td>SHUTDOWN</td><td>— (игрок начинает останов)</td></tr>
 * <tr><td>SHUTDOWN</td><td>FREEING</td><td>ни одна машина установки не работает</td></tr>
 * <tr><td>FREEING</td><td>OPEN</td><td>газоанализ установки после освобождения в норме</td></tr>
 * <tr><td>OPEN</td><td>BOX_UP</td><td>хотя бы одна работа окончена по наряду-допуску</td></tr>
 * <tr><td>BOX_UP</td><td>PSSR</td><td>все наряды установки закрыты</td></tr>
 * <tr><td>PSSR</td><td>STARTUP</td><td>предпусковая проверка (OSHA 1910.119(i)): нарядов нет, замков нет, байпасов ПАЗ
 * нет, проверки ПАЗ не просрочены</td></tr>
 * <tr><td>STARTUP</td><td>COMPLETE</td><td>машина установки снова работает</td></tr>
 * </table>
 *
 * <p>Заглушки, пропарка, азот, пирофорные отложения и испытание на плотность фазы BOX_UP в моде пока не
 * моделируются отдельно: их роль здесь — наряды и LOTO, которые эти работы и оформляют.
 */
public final class Turnaround {
    /** Состояние установки для условий фаз. */
    public record Inputs(int runningMachines, int openPermits, int completedPermits, int locksHung, int sisBypassed,
            int sisOverdue, boolean gasFree) {
    }

    /** Невыполненное условие перехода. */
    public enum Missing { MACHINES_RUNNING, GAS_TEST, NO_REPAIRS, PERMITS_OPEN, LOCKS_HUNG, SIS_BYPASSED, SIS_OVERDUE, NOT_RUNNING, DONE }

    private TurnaroundPhase phase = TurnaroundPhase.PLANNING;
    private long startTick = -1, phaseTick, completeTick = -1;
    private int repairs;

    public TurnaroundPhase phase() {
        return phase;
    }

    /** Тик начала останова; −1 — ремонт ещё не начат. */
    public long startTick() {
        return startTick;
    }

    public long phaseTick() {
        return phaseTick;
    }

    public long completeTick() {
        return completeTick;
    }

    /** Работ, оконченных по нарядам за ремонт (снимок на выходе из OPEN). */
    public int repairs() {
        return repairs;
    }

    public boolean complete() {
        return phase == TurnaroundPhase.COMPLETE;
    }

    /** Чего не хватает для перехода из текущей фазы. */
    public Set<Missing> missing(Inputs in) {
        EnumSet<Missing> out = EnumSet.noneOf(Missing.class);
        switch (phase) {
            case PLANNING -> {
            }
            case SHUTDOWN -> {
                if (in.runningMachines() > 0) out.add(Missing.MACHINES_RUNNING);
            }
            case FREEING -> {
                if (!in.gasFree()) out.add(Missing.GAS_TEST);
            }
            case OPEN -> {
                if (in.completedPermits() < 1) out.add(Missing.NO_REPAIRS);
            }
            case BOX_UP -> {
                if (in.openPermits() > 0) out.add(Missing.PERMITS_OPEN);
            }
            case PSSR -> {
                if (in.openPermits() > 0) out.add(Missing.PERMITS_OPEN);
                if (in.locksHung() > 0) out.add(Missing.LOCKS_HUNG);
                if (in.sisBypassed() > 0) out.add(Missing.SIS_BYPASSED);
                if (in.sisOverdue() > 0) out.add(Missing.SIS_OVERDUE);
            }
            case STARTUP -> {
                if (in.runningMachines() < 1) out.add(Missing.NOT_RUNNING);
            }
            case COMPLETE -> out.add(Missing.DONE);
        }
        return out;
    }

    /**
     * Перейти в следующую фазу.
     *
     * @return невыполненные условия; пусто — переход сделан
     */
    public Set<Missing> advance(Inputs in, long tick) {
        Set<Missing> m = missing(in);
        if (!m.isEmpty()) return m;
        if (phase == TurnaroundPhase.PLANNING) startTick = tick;
        if (phase == TurnaroundPhase.OPEN) repairs = in.completedPermits();
        phase = TurnaroundPhase.values()[phase.ordinal() + 1];
        phaseTick = tick;
        if (phase == TurnaroundPhase.COMPLETE) completeTick = tick;
        return m;
    }

    /** Новый ремонт после завершённого. */
    public void reset() {
        phase = TurnaroundPhase.PLANNING;
        startTick = -1;
        completeTick = -1;
        phaseTick = 0;
        repairs = 0;
    }

    public void save(StateWriter out) {
        out.putInt("phase", phase.ordinal());
        out.putLong("start", startTick);
        out.putLong("phase_tick", phaseTick);
        out.putLong("complete", completeTick);
        out.putInt("repairs", repairs);
    }

    public void load(StateReader in) {
        phase = TurnaroundPhase.values()[Math.floorMod(in.optInt("phase", 0), TurnaroundPhase.values().length)];
        startTick = in.optLong("start", -1);
        phaseTick = in.optLong("phase_tick", 0);
        completeTick = in.optLong("complete", -1);
        repairs = in.optInt("repairs", 0);
    }
}
