package dev.denfry.promptcritical.sim.production;

import dev.denfry.promptcritical.sim.drilling.NaturalGas;
import dev.denfry.promptcritical.sim.drilling.Trap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Непрерывный газлифт (09 §9.6.4): газ закачивается в затрубье и через газлифтные клапаны (сильфонные, управляемые
 * давлением в затрубье) входит в НКТ, облегчая столб ({@link Tubing#pressureAtPa} с газом выше точки ввода).
 *
 * <p><b>Пуск.</b> Затрубье и НКТ заполнены жидкостью глушения (плотность продукции). Газ под давлением пуска
 * {@link #KICKOFF_PA} оттесняет жидкость до верхнего клапана, если давление газового столба на его глубине выше
 * гидростатики жидкости в НКТ над ним плюс перепад на клапане {@link #VALVE_DP_PA}. Затем скважина работает через
 * этот клапан, давление в НКТ ниже падает, и открывается следующий — пока давления газа хватает (разгрузка сверху вниз).
 *
 * <p><b>Работа.</b> Рабочее давление в затрубье — то, что держит ввод через самый глубокий открытый клапан: давление
 * в НКТ на его глубине плюс перепад. Верхний (пусковой) клапан закрывается, если давление открытия p_tro, заданное
 * при установке, выше давления газа на его глубине при рабочем давлении; иначе он остаётся открытым, газ идёт через
 * него — скважина «застревает» на верхнем клапане. Перепад на клапане 0,7 МПа (100 psi) — правило расстановки
 * (Brown, «The Technology of Artificial Lift Methods», т. 2a, гл. 3); давление пуска — компрессор газлифтной
 * системы 8 МПа [BALANCE]. Пульсации («heading») не моделируются: дебит — установившаяся точка.
 */
public final class GasLift {
    public static final double KICKOFF_PA = 8e6, VALVE_DP_PA = 0.7e6;
    /** Наибольшая закачка, нм³/с [BALANCE]. */
    public static final double MAX_INJECTION_STD_M3S = 1.0;
    /** Клапаны ниже этой отметки над перфорацией не ставятся (пакер), м. */
    public static final double ABOVE_PERFS_M = 10;

    private GasLift() {
    }

    /** Клапан: глубина, м, и давление открытия, Па. */
    public record Valve(double depthM, double ptroPa) {
    }

    /**
     * Точка работы: дебит нефти, м³/с; глубина ввода газа; номер рабочего клапана (−1 — разгрузить не удалось);
     * рабочее давление в затрубье на устье; газ идёт через верхний клапан, который не закрылся.
     */
    public record Point(double oilM3s, double injDepthM, int valve, double casingSurfacePa, boolean stuckHigh) {
        public static final Point NONE = new Point(0, Double.NaN, -1, 0, false);
    }

    /** Давление газа в затрубье на глубине при давлении на устье, Па: столб газа интегрируется по шагам. */
    public static double casingAtDepthPa(double surfacePa, double depthM, double wellDepthM) {
        int n = 20;
        double dz = depthM / n, p = surfacePa;
        for (int i = 0; i < n; i++) {
            double z = (i + 0.5) * dz;
            double t = Tubing.WELLHEAD_K + (Trap.temperatureK(wellDepthM) - Tubing.WELLHEAD_K) * z / Math.max(wellDepthM, 1);
            p += NaturalGas.densityKgM3(p + NaturalGas.STD_PRESSURE_PA, t) * Tubing.G * dz;
        }
        return p;
    }

    /** Клапаны выше перфорации, по глубине. */
    public static List<Valve> usable(List<Valve> valves, double wellDepthM) {
        List<Valve> out = new ArrayList<>();
        for (Valve v : valves) if (v.depthM() > 0 && v.depthM() < wellDepthM - ABOVE_PERFS_M) out.add(v);
        out.sort(Comparator.comparingDouble(Valve::depthM));
        return out;
    }

    /**
     * Точка работы газлифтной скважины при закачке {@code injStdM3s}, нм³/с. {@link Point#NONE} — нет клапанов, нет
     * закачки или газ не может оттеснить жидкость глушения до верхнего клапана.
     */
    public static Point operate(Inflow ipr, double gor, double wc, double wellDepthM, double choke64, double separatorPa,
            double injStdM3s, List<Valve> valves) {
        List<Valve> vs = usable(valves, wellDepthM);
        if (injStdM3s <= 0 || vs.isEmpty()) return Point.NONE;
        double rhoL = (1 - wc) * ProducingReservoir.STOCK_OIL_KGM3 + wc * ProducingReservoir.WATER_KGM3;
        double z0 = vs.getFirst().depthM();
        if (casingAtDepthPa(KICKOFF_PA, z0, wellDepthM) <= separatorPa + rhoL * Tubing.G * z0 + VALVE_DP_PA) return Point.NONE;
        int d = 0;
        double q = rate(ipr, gor, wc, wellDepthM, choke64, separatorPa, injStdM3s, z0);
        while (d + 1 < vs.size() && q > 0) {
            double zn = vs.get(d + 1).depthM();
            double pt = tubingPa(ipr, gor, wc, wellDepthM, choke64, separatorPa, injStdM3s, vs.get(d).depthM(), q, zn);
            if (casingAtDepthPa(KICKOFF_PA, zn, wellDepthM) <= pt + VALVE_DP_PA) break;
            double qn = rate(ipr, gor, wc, wellDepthM, choke64, separatorPa, injStdM3s, zn);
            if (qn <= 0) break;
            d++;
            q = qn;
        }
        double zd = vs.get(d).depthM();
        double target = tubingPa(ipr, gor, wc, wellDepthM, choke64, separatorPa, injStdM3s, zd, q, zd) + VALVE_DP_PA;
        // рабочее давление на устье: столб газа до рабочего клапана даёт target
        double lo = 0, hi = KICKOFF_PA;
        for (int i = 0; i < 40; i++) {
            double mid = 0.5 * (lo + hi);
            if (casingAtDepthPa(mid, zd, wellDepthM) < target) lo = mid;
            else hi = mid;
        }
        double pOp = hi;
        for (int i = 0; i < d; i++) {
            Valve v = vs.get(i);
            if (v.ptroPa() <= casingAtDepthPa(pOp, v.depthM(), wellDepthM)) {
                double qi = rate(ipr, gor, wc, wellDepthM, choke64, separatorPa, injStdM3s, v.depthM());
                return new Point(qi, v.depthM(), i, pOp, true);
            }
        }
        return new Point(q, zd, d, pOp, false);
    }

    private static double rate(Inflow ipr, double gor, double wc, double depth, double choke, double sep, double inj, double injDepth) {
        return Tubing.flowingRateM3s(ipr, gor, wc, depth, choke, sep, inj, injDepth);
    }

    /** Давление в НКТ на глубине {@code z} при дебите {@code q} и вводе газа на {@code injDepth}. */
    private static double tubingPa(Inflow ipr, double gor, double wc, double depth, double choke, double sep, double inj, double injDepth,
            double q, double z) {
        double ql = q / (1 - wc);
        double glr = gor * (1 - wc) + (ql > 0 ? inj / ql : 0);
        double pwh = Math.max(sep, Tubing.gilbertWellheadPa(ql, glr, choke));
        return Tubing.pressureAtPa(pwh, q, gor, wc, depth, z, ipr.bubblePointPa(), inj, injDepth);
    }
}
