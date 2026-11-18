package dev.denfry.promptcritical.mod.ops;

import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.ops.EnergyKind;

/**
 * BE блока, принимающего замок LOTO (13 §13.1.3): выключатель, разъединитель, расцепная муфта, заглушка. Положение
 * блока — его дело; реестр замков ({@link Lockouts}) спрашивает, изолирован ли он и есть ли ещё энергия.
 */
public interface IsolationPoint {
    /** Вид энергии, которую точка отсекает. */
    EnergyKind energy();

    /** Блок в изолированном положении: выключатель отключён, муфта расцеплена, заглушка глухая. */
    boolean isolatedPosition();

    /**
     * Проверка отсутствия энергии за точкой (§13.1.6): у электрической точки — по указателю напряжения на стороне
     * присоединения, у механической — вал стоит.
     */
    boolean noEnergy();

    /** Устройство в топологии (для журнала); {@link SimId#NONE} — у точки его нет. */
    SimId simId();
}
