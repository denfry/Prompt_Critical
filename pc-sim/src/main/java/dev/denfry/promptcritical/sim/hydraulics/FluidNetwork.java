package dev.denfry.promptcritical.sim.hydraulics;

import dev.denfry.promptcritical.sim.thermo.FluidPackage;
import java.util.List;

/**
 * Гидросеть на сосредоточенных параметрах (01 §1.1–1.2, §1.12): узлы (M, U), связи ṁ,
 * полунеявная схема по давлению, компоненты связности решаются независимо. Одна сеть —
 * одна среда ({@link FluidPackage}, R11).
 */
public interface FluidNetwork {
    static FluidNetwork create(FluidPackage fluid, HydraulicsConfig config) {
        return new LumpedFluidNetwork(fluid, config);
    }

    /** Сборка или пересборка из спецификаций; при BlockChangedEvent, не в горячем цикле. */
    void rebuild(List<FluidNodeSpec> nodes, List<FlowLinkSpec> links, StateCarryOver carry);

    /** Все бодрствующие компоненты на тик dt; подшаги внутри. Тепло и источники тика сбрасываются. */
    void stepFast(double dt);

    FluidNode node(long id);

    FlowLink link(long id);

    int componentCount();

    /** Компонента узла; −1 у граничных узлов (они не входят в компоненты). */
    int componentOf(long nodeId);

    boolean isAsleep(int component);

    void wake(long nodeOrLinkId);

    /** Регистрирует скаляр; масса в существующих узлах — 0. */
    void registerScalar(PassiveScalar s);

    /** Характеристика насоса для {@link PumpModel.Spec#homologousCurveId()}; до {@link #rebuild}. */
    void registerHomologousCurve(String id, HomologousCurve curve);

    void setEventSink(HydraulicEventSink sink);

    /** Σ M по неграничным узлам, кг. */
    double totalMass();

    /** Σ U по неграничным узлам, Дж. */
    double totalEnergy();

    /** Накопленная с пересборки масса извне: граничные узлы и источники S_M, кг. */
    double externalMassInput();

    /** Накопленная энергия извне: границы, S_E, тепло Q и работа насосов, Дж. */
    double externalEnergyInput();

    /** Накопленная работа насосов (часть {@link #externalEnergyInput()}), Дж. */
    double pumpWorkInput();
}
