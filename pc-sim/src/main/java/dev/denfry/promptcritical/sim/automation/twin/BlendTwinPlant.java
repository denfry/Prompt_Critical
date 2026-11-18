package dev.denfry.promptcritical.sim.automation.twin;

import dev.denfry.promptcritical.sim.refining.GasolineBlending;
import dev.denfry.promptcritical.sim.refining.GasolineBlending.BlendOptimizerInput;
import dev.denfry.promptcritical.sim.refining.GasolineBlending.Component;
import dev.denfry.promptcritical.sim.refining.GasolineBlending.Properties;
import java.util.ArrayList;
import java.util.List;

/**
 * Стенд «станция смешения бензина» для задачи наладчика {@code gasoline_blending_lp} (03d §3d.11.6 № 15, §3d.10.3):
 * регулирующие клапаны компонентов с расходомерами (09 §9.9.14 «исполнение рецепта») в общий коллектор и резервуар
 * смешения; на коллекторе — поточный анализатор качества {@code quality_analyzer} (октановый двигатель): ОЧИ, ОЧМ и
 * ДНП смеси в коллекторе с запаздыванием {@value #ANALYZER_DEAD_S} с, новый результат раз в {@value #ANALYZER_CYCLE_S}
 * с [BALANCE]. Качество в резервуаре — «истинное» ({@link GasolineBlending#actual}), а не линейное ЛП: рецепт,
 * найденный по ЛП, надо подправить по анализатору.
 *
 * <p>Компоненты и марка — эталон 09 §9.9.14 (LSR, риформат, бензин FCC, бутан, алкилат; АИ-92 класса 5). Механизмы —
 * расходы компонентов, м³/ч, от 0 до наличия A_i. Сигналы резервуара: объём, ОЧИ, ОЧМ, ДНП, сера, бензол, ароматика,
 * олефины, плотность, себестоимость у.е./м³, её отношение к оптимуму ЛП при расходе партии {@value #BATCH_M3H} м³/ч
 * ({@code cost_ratio}, и то же превышением в процентах {@code cost_premium_pct}) и признак «в спецификации».
 */
public final class BlendTwinPlant implements TwinPlant {
    public static final String ID = "gasoline_blending";
    /** Расход партии, м³/ч, для оптимума ЛП; время задержки и цикл анализатора, с. */
    public static final double BATCH_M3H = 9, ANALYZER_DEAD_S = 60, ANALYZER_CYCLE_S = 30;
    public static final BlendOptimizerInput INPUT = new BlendOptimizerInput(GasolineBlending.WITH_ALKYLATE, GasolineBlending.AI92);
    public static final String VOLUME = "volume_m3", RON = "ron", MON = "mon", RVP = "rvp_kpa", SULFUR = "sulfur_ppm",
            BENZENE = "benzene_pct", AROMATICS = "aromatics_pct", OLEFINS = "olefins_pct", DENSITY = "density",
            COST = "cost_per_m3", COST_RATIO = "cost_ratio", COST_PREMIUM = "cost_premium_pct", ON_SPEC = "on_spec", FLOW = "flow_m3h", AN_RON = "an_ron",
            AN_MON = "an_mon", AN_RVP = "an_rvp_kpa";
    public static final List<String> SIGNALS = List.of(VOLUME, RON, MON, RVP, SULFUR, BENZENE, AROMATICS, OLEFINS, DENSITY,
            COST, COST_RATIO, COST_PREMIUM, ON_SPEC, FLOW, AN_RON, AN_MON, AN_RVP);
    public static final List<String> ACTUATORS;

    static {
        List<String> a = new ArrayList<>();
        for (Component c : INPUT.components()) a.add(c.id());
        ACTUATORS = List.copyOf(a);
    }

    /** Себестоимость оптимума ЛП, у.е./м³, при расходе {@link #BATCH_M3H}. */
    public static final double LP_COST_PER_M3 = GasolineBlending.minimizeCost(INPUT, BATCH_M3H).objective() / BATCH_M3H;

    private final List<Component> cs = INPUT.components();
    private final double[] flow = new double[cs.size()], tank = new double[cs.size()];
    private final List<double[]> pending = new ArrayList<>();
    private Properties tankProps = GasolineBlending.actual(cs, tank);
    private double t, nextSample, anRon = Double.NaN, anMon = Double.NaN, anRvp = Double.NaN;

    /** Пустой резервуар, клапаны закрыты, анализатор ещё не дал результата. */
    public BlendTwinPlant() {
    }

    @Override
    public String template() {
        return ID;
    }

    @Override
    public List<String> signals() {
        return SIGNALS;
    }

    @Override
    public double read(String signal) {
        Properties p = tankProps;
        return switch (signal) {
            case VOLUME -> p.volume();
            case RON -> p.ron();
            case MON -> p.mon();
            case RVP -> p.rvpKpa();
            case SULFUR -> p.sulfurPpm();
            case BENZENE -> p.benzenePct();
            case AROMATICS -> p.aromaticsPct();
            case OLEFINS -> p.olefinsPct();
            case DENSITY -> p.density();
            case COST -> p.costPerM3();
            case COST_RATIO -> p.volume() > 0 ? p.costPerM3() / LP_COST_PER_M3 : Double.NaN;
            case COST_PREMIUM -> p.volume() > 0 ? 100 * (p.costPerM3() / LP_COST_PER_M3 - 1) : Double.NaN;
            case ON_SPEC -> p.onSpec(INPUT.grade()) ? 1 : 0;
            case FLOW -> totalFlow();
            case AN_RON -> anRon;
            case AN_MON -> anMon;
            case AN_RVP -> anRvp;
            default -> Double.NaN;
        };
    }

    private double totalFlow() {
        double s = 0;
        for (double f : flow) s += f;
        return s;
    }

    @Override
    public List<String> actuators() {
        return ACTUATORS;
    }

    private int index(String actuator) {
        int i = ACTUATORS.indexOf(actuator);
        if (i < 0) throw new IllegalArgumentException("no actuator " + actuator);
        return i;
    }

    @Override
    public double[] range(String actuator) {
        return new double[] {0, cs.get(index(actuator)).availableM3h()};
    }

    @Override
    public boolean discrete(String actuator) {
        return false;
    }

    @Override
    public double actuator(String actuator) {
        int i = ACTUATORS.indexOf(actuator);
        return i < 0 ? Double.NaN : flow[i];
    }

    @Override
    public void actuate(String actuator, double value) {
        int i = index(actuator);
        flow[i] = TwinPlant.clamp(value, range(actuator));
    }

    @Override
    public void step() {
        for (int i = 0; i < flow.length; i++) tank[i] += flow[i] / 3600 * DT;
        tankProps = GasolineBlending.actual(cs, tank);
        t += DT;
        if (t >= nextSample - 1e-9) {
            nextSample += ANALYZER_CYCLE_S;
            Properties h = GasolineBlending.actual(cs, flow);
            pending.add(h.volume() > 0 ? new double[] {t + ANALYZER_DEAD_S, h.ron(), h.mon(), h.rvpKpa()}
                    : new double[] {t + ANALYZER_DEAD_S, Double.NaN, Double.NaN, Double.NaN});
        }
        while (!pending.isEmpty() && pending.getFirst()[0] <= t + 1e-9) {
            double[] r = pending.removeFirst();
            anRon = r[1];
            anMon = r[2];
            anRvp = r[3];
        }
    }

    @Override
    public String failure() {
        return null;
    }
}
