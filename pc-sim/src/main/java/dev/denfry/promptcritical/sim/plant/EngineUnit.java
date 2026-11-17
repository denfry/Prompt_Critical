package dev.denfry.promptcritical.sim.plant;

import dev.denfry.promptcritical.sim.combustion.FlyballGovernor;
import dev.denfry.promptcritical.sim.combustion.SteamEngineUnit;
import dev.denfry.promptcritical.sim.combustion.SteamTurbine;
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
    /** Паровая машина; у турбины — null. */
    final SteamEngineUnit unit;
    /** Паровая турбина ({@link SteamPlant#addTurbine}); у машины — null. */
    final SteamTurbine turbine;
    SimId boiler = SimId.NONE;
    boolean stopValveOpen, dashpot, seized;
    /** Вал отдаёт обороты в сеть Create (R3); для события {@link SteamPlant#ENGINE_RUNNING}. */
    boolean running;
    double loadPowerW, capacityW;
    FluidNode chest;
    FlowLink throttle;
    double initPressure = Double.NaN, initEnthalpy = Double.NaN;
    /** Противодавление турбины, Па: атмосфера или конденсатор (биндинг). */
    double exhaustPa = SteamPlant.EXHAUST_PA;

    EngineUnit(SimId id) {
        this.id = id;
        this.unit = new SteamEngineUnit(SteamEngineUnit.Spec.reference(1, SteamEngineUnit.FLYWHEEL_INERTIA),
                FlyballGovernor.bare());
        this.turbine = null;
        // машину останавливают не в мёртвой точке, иначе одноцилиндровую пришлось бы сразу проворачивать
        unit.setCrankAngle(Math.PI / 2);
    }

    EngineUnit(SimId id, SteamTurbine turbine) {
        this.id = id;
        this.unit = null;
        this.turbine = turbine;
    }

    /** Противодавление турбины: конденсатор под ней — вакуум, иначе атмосфера {@link SteamPlant#EXHAUST_PA}. */
    public void setExhaustPressure(double pa) {
        exhaustPa = Math.max(1000, pa);
    }

    public double exhaustPressurePa() {
        return exhaustPa;
    }

    /** Турбина, а не поршневая машина. */
    public boolean isTurbine() {
        return turbine != null;
    }

    /** Турбина ({@code null} у паровой машины). */
    public SteamTurbine turbine() {
        return turbine;
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
        return turbine != null ? turbine.omega() : unit.omega();
    }

    public double nominalOmega() {
        return turbine != null ? turbine.spec().nominalOmega() : unit.nominalOmega();
    }

    public double crankAngle() {
        return turbine != null ? 0 : unit.crankAngle();
    }

    /** Мощность на валу при номинальных оборотах и полностью открытой заслонке при нынешнем давлении в котле, Вт. */
    public double capacityW() {
        return capacityW;
    }

    public double shaftPowerW() {
        return turbine != null ? turbine.shaftPowerW() : unit.shaftPowerW();
    }

    public double steamFlowKgS() {
        return turbine != null ? turbine.steamFlowKgS() : unit.steamFlowKgS();
    }

    /** Давление в золотниковой коробке, Па; NaN без подключения к котлу. */
    public double chestPressurePa() {
        return chest != null ? chest.pressure() : Double.NaN;
    }

    public boolean stopValveOpen() {
        return stopValveOpen;
    }

    /** Подшипник заплавлен (13 §13.6.7): вал стоит, пока вкладыш не заменят. Вход биндинга, не сохраняется. */
    public void setSeized(boolean seized) {
        this.seized = seized;
    }

    public boolean seized() {
        return seized;
    }

    public boolean dashpotFitted() {
        return dashpot;
    }

    public double throttle() {
        return unit.throttle();
    }
}
