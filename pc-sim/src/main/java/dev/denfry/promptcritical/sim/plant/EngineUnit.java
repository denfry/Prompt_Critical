package dev.denfry.promptcritical.sim.plant;

import dev.denfry.promptcritical.sim.combustion.FlyballGovernor;
import dev.denfry.promptcritical.sim.combustion.SteamEngineUnit;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.hydraulics.FlowLink;
import dev.denfry.promptcritical.sim.hydraulics.FluidNode;

/**
 * Паровая машина котельной: один цилиндр двойного действия эталонной машины 07 §7.9.7 (T7.14, ≈ 119 кВт на валу) с
 * маховиком; пар — из котла {@link #boiler()} через дроссельную заслонку регулятора и стопорный вентиль в
 * золотниковую коробку (узел гидросети). Без катаракта регулятор «качает» обороты.
 *
 * <p>Стопорный вентиль при растопке закрыт: открытый пускает пар на низком давлении, машина ползёт и садится в
 * мёртвую точку. Открытие вентиля на стоящей машине ставит кривошип в пусковое положение (за мёртвую точку ломом —
 * обычный порядок пуска одноцилиндровой машины).
 *
 * <p>Вход биндинга — {@link #setLoadPower} (доля нагрузки сети Create, 03a §3a.7.3); выход — обороты и
 * располагаемая мощность при полностью открытой заслонке ({@link #capacityW()}) для ёмкости источника Create.
 */
public final class EngineUnit {
    final SimId id;
    final SteamEngineUnit unit;
    SimId boiler = SimId.NONE;
    boolean stopValveOpen, dashpot;
    double loadPowerW, capacityW;
    FluidNode chest;
    FlowLink throttle;
    double initPressure = Double.NaN, initEnthalpy = Double.NaN;

    EngineUnit(SimId id) {
        this.id = id;
        this.unit = new SteamEngineUnit(SteamEngineUnit.Spec.reference(1, SteamEngineUnit.FLYWHEEL_INERTIA),
                FlyballGovernor.bare());
        // машину останавливают не в мёртвой точке, иначе одноцилиндровую пришлось бы сразу проворачивать
        unit.setCrankAngle(Math.PI / 2);
    }

    public SimId id() {
        return id;
    }

    public SteamEngineUnit unit() {
        return unit;
    }

    /** Котёл, от которого машина берёт пар; {@link SimId#NONE} — не подключена. */
    public SimId boiler() {
        return boiler;
    }

    /** Мощность, которую забирает сеть Create, Вт (0 при перегрузке сети). */
    public void setLoadPower(double watts) {
        loadPowerW = Math.max(0, watts);
    }

    public double omega() {
        return unit.omega();
    }

    public double nominalOmega() {
        return unit.nominalOmega();
    }

    public double crankAngle() {
        return unit.crankAngle();
    }

    /** Мощность на валу при номинальных оборотах и полностью открытой заслонке при нынешнем давлении в котле, Вт. */
    public double capacityW() {
        return capacityW;
    }

    public double shaftPowerW() {
        return unit.shaftPowerW();
    }

    public double steamFlowKgS() {
        return unit.steamFlowKgS();
    }

    /** Давление в золотниковой коробке, Па; NaN без подключения к котлу. */
    public double chestPressurePa() {
        return chest != null ? chest.pressure() : Double.NaN;
    }

    public boolean stopValveOpen() {
        return stopValveOpen;
    }

    public boolean dashpotFitted() {
        return dashpot;
    }

    public double throttle() {
        return unit.throttle();
    }
}
