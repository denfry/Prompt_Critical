package dev.denfry.promptcritical.mod.ops;

import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.ops.Baseline;
import dev.denfry.promptcritical.sim.ops.UnitKind;
import net.minecraft.world.phys.Vec3;

/**
 * BE машины I0 с пусконаладкой и обслуживанием (13 §13.6.5–13.6.7): котёл, паровая машина, вентилятор главного
 * проветривания, насос водоотлива, подъёмная машина. Двойник в {@link dev.denfry.promptcritical.sim.ops.OpsSubsystem}
 * — по тому же {@link SimId}, что и устройство машины.
 */
public interface OpsMachine {
    SimId simId();

    UnitKind opsKind();

    /** Условие ремонта {@code STOP} (13 §13.6.7): вал стоит или топка погашена. */
    boolean opsStopped();

    /** Корпус подшипника — точка частиц, стука и замера рукой. */
    Vec3 opsBearingPos();

    /** Текущие показатели для сравнения с паспортом на щите обслуживания (неприменимое — NaN). */
    default Baseline opsCurrent() {
        return Baseline.NONE;
    }
}
