package dev.denfry.promptcritical.sim.production;

/**
 * Колонна штанг и плунжерный насос ШГН как динамическая система (09 §9.6.5): волновое уравнение Гиббса в упрощённом
 * виде — колонна из {@link #MASSES} масс на пружинах с вязким демпфированием. Сверху — кинематика полированного штока
 * станка-качалки ({@link BeamPump#position}), снизу — насос с всасывающим и нагнетательным клапанами.
 *
 * <p><b>Штанги.</b> Отрезок длиной L/8: масса m = q_ш·L/8, жёсткость k = EA_ш/(L/8); перемещения отсчитываются от
 * равновесия колонны в жидкости без нагрузки насоса, поэтому нагрузка на полированный шток
 * PRL = W_ш·(1 − ρ_ж/ρ_ст) + k(u₀ − u₁). Демпфирование каждой массы — c = 2ζmω₁, ω₁ = πa/(2L) — первая собственная
 * частота колонны, a = √(EA_ш/q_ш), ζ = {@link #DAMPING_RATIO} [BALANCE: у Гиббса коэффициент затухания подбирают по
 * динамограммам, 0,05–0,15 от критического]. Интегрирование — полунеявный Эйлер с шагом не больше 0,1/ω_max за
 * {@link #CYCLES} цикла от статического равновесия; карта — последний цикл, {@link #POINTS} точек по времени.
 *
 * <p><b>Насос.</b> Давление в камере p_к: ход вверх — нагнетательный клапан закрыт; если на ходе вниз он не
 * открылся, газ в камере (с вредным пространством {@link #DEAD_SPACE_M}) расширяется по Бойлю, пока p_к не упадёт до
 * давления на приёме и не откроется всасывающий, а пока натяжение штанг меньше веса столба, плунжер стоит у нижней
 * мёртвой точки; ход вниз — всасывающий закрыт, газ над жидкостью (доля 1 − f объёма камеры наверху, при давлении
 * приёма) сжимается по Бойлю, пока p_к не дойдёт до p_нагн и не откроется нагнетательный. Сила на плунжере
 * A_п(p_нагн − p_к) при закрытом нагнетательном и 0 при открытом. Отсюда формы карт §9.6.5: полная — норма;
 * при малом давлении на приёме газ сжимается в ноль у самой жидкости — «удар о жидкость» (неполное заполнение);
 * при большом объёме газа нагрузка снимается постепенно — узкая карта, а если газ не сжимается до p_нагн — газовая
 * пробка; утечка нагнетательного клапана λ снимает часть нагрузки к концу хода вверх (наклон линии), обрыв штанг —
 * нагрузка падает до веса штанг выше обрыва.
 */
public final class RodPumpDynamics {
    public static final int MASSES = 8, POINTS = 200, CYCLES = 4;
    public static final double DAMPING_RATIO = 0.1;
    /** Вредное пространство насоса, м хода [BALANCE: клапанные узлы НСН 2–5 дюймов]. */
    public static final double DEAD_SPACE_M = 0.08;
    private static final int STEPS_MIN = 1200;
    private static final double V_EPS = 1e-4;

    private RodPumpDynamics() {
    }

    /**
     * Динамограмма установившегося режима.
     *
     * @param spm        качаний в минуту
     * @param rodLengthM глубина подвески насоса, м
     * @param liquidKgM3 плотность откачиваемой жидкости
     * @param dischargePa давление над плунжером (нагнетание), Па
     * @param intakePa   давление на приёме насоса, Па
     * @param fillage    доля камеры, заполненная жидкостью к верхней мёртвой точке, 0..1
     * @param tvLeak     утечка нагнетательного клапана: доля нагрузки, снятая к концу хода вверх, 0..1
     * @param partDepthM глубина обрыва штанг; NaN — колонна цела
     */
    public static DynoCard card(double spm, double rodLengthM, double liquidKgM3, double dischargePa, double intakePa, double fillage,
            double tvLeak, double partDepthM) {
        double n = Math.max(spm, 0.5), w = n * 2 * Math.PI / 60, period = 60 / n;
        double seg = rodLengthM / MASSES;
        double m = BeamPump.ROD_KG_PER_M * seg, k = BeamPump.STEEL_E * BeamPump.ROD_AREA_M2 / seg;
        double a = Math.sqrt(BeamPump.STEEL_E * BeamPump.ROD_AREA_M2 / BeamPump.ROD_KG_PER_M);
        double w1 = Math.PI * a / (2 * rodLengthM), c = 2 * DAMPING_RATIO * m * w1;
        int nodes = MASSES;
        boolean parted = !Double.isNaN(partDepthM);
        if (parted) nodes = Math.clamp((int) Math.floor(partDepthM / seg), 1, MASSES);
        double wrf = BeamPump.ROD_KG_PER_M * seg * nodes * BeamPump.G * (1 - liquidKgM3 / BeamPump.STEEL_KGM3);
        double ap = BeamPump.PLUNGER_AREA_M2, pd = Math.max(dischargePa, 0), pin = Math.clamp(intakePa, 1e4, pd);
        double fo = ap * (pd - pin), fill = Math.clamp(fillage, 0, 1), leak = Math.clamp(tvLeak, 0, 1);
        double omegaMax = 2 * Math.sqrt(k / m);
        int steps = Math.max(STEPS_MIN, (int) Math.ceil(period * omegaMax / 0.1));
        double dt = period / steps;

        double[] u = new double[nodes + 1], v = new double[nodes + 1];
        // статика: под нагрузкой насоса каждый отрезок растянут на F/k (перемещения — от равновесия без насоса)
        double f0 = parted ? 0 : fo;
        for (int i = 0; i <= nodes; i++) u[i] = BeamPump.position(0) - i * f0 / k;
        // насос: камера между клапанами, газ в ней подчиняется закону Бойля (p·V газа неизменно, пока клапаны закрыты)
        boolean goingUp = true, travellingOpen = false, standingOpen = true;
        double yBottom = u[nodes] - BeamPump.position(0), yTop = yBottom + BeamPump.strokeM(), yRef = u[nodes];
        double liquidM3 = 0, gasRefV = 0, gasRefP = pd, pump = f0;
        double yMin = Double.MAX_VALUE, yMax = -Double.MAX_VALUE;
        double[] pos = new double[POINTS], load = new double[POINTS];
        double peak = -Double.MAX_VALUE, min = Double.MAX_VALUE, work = 0, plungerMin = Double.MAX_VALUE, plungerMax = -Double.MAX_VALUE;
        double[] prl = new double[steps], x0 = new double[steps];
        for (int cyc = 0; cyc < CYCLES; cyc++) {
            boolean last = cyc == CYCLES - 1;
            for (int s = 0; s < steps; s++) {
                double theta = w * (s + 1) * dt;
                u[0] = BeamPump.position(theta);
                v[0] = BeamPump.velocity(theta, n);
                if (!parted) {
                    double y = u[nodes];
                    // ход плунжера меняется, когда меняется знак его скорости; у нижней мёртвой точки его держит столб
                    if (!goingUp && v[nodes] > V_EPS) {
                        // нижняя мёртвая точка: начинается ход вверх
                        goingUp = true;
                        if (travellingOpen) {
                            // газ ушёл через нагнетательный клапан первым: во вредном пространстве — жидкость
                            liquidM3 = 0;
                            gasRefV = 0;
                            gasRefP = pd;
                        }
                        travellingOpen = standingOpen = false;
                        yRef = y;
                    } else if (goingUp && v[nodes] < -V_EPS) {
                        // верхняя мёртвая точка: камера заполнена жидкостью на f, остальное — газ при давлении приёма
                        goingUp = false;
                        if (standingOpen) {
                            double vTop = ap * (Math.max(0, y - yBottom) + DEAD_SPACE_M);
                            liquidM3 = fill * vTop;
                            gasRefV = vTop - liquidM3;
                            gasRefP = pin;
                        }
                        travellingOpen = standingOpen = false;
                    }
                    double vg = ap * (Math.max(0, y - yBottom) + DEAD_SPACE_M) - liquidM3;
                    double pc;
                    if (goingUp) {
                        if (standingOpen || gasRefV <= 1e-12) {
                            pc = pin;
                            standingOpen = true;
                        } else {
                            pc = gasRefP * gasRefV / Math.max(vg, 1e-12);
                            if (pc <= pin) {
                                pc = pin;
                                standingOpen = true;
                            }
                        }
                        double lift = Math.max(1e-6, yTop - yBottom);
                        pump = ap * (pd - pc) * (1 - leak * Math.clamp((y - yRef) / lift, 0, 1));
                    } else {
                        if (travellingOpen) {
                            pc = pd;
                        } else {
                            pc = vg <= 1e-9 ? pd : gasRefP * gasRefV / vg;
                            if (pc >= pd) {
                                pc = pd;
                                travellingOpen = true;
                            }
                        }
                        pump = travellingOpen ? 0 : ap * (pd - pc);
                    }
                } else {
                    pump = 0;
                }
                // плунжер с закрытым нагнетательным клапаном не пойдёт вверх, пока натяжение штанг не поднимет столб,
                // и не пойдёт вниз (клапан откроется): стоит, а нагрузка насоса равна натяжению нижнего отрезка
                double tension = k * (u[nodes - 1] - u[nodes]);
                boolean held = !parted && goingUp && v[nodes] <= 0 && tension >= 0 && tension < pump;
                if (held) pump = tension;
                // штанги: полунеявный Эйлер
                for (int i = 1; i <= nodes; i++) {
                    if (held && i == nodes) {
                        v[i] = 0;
                        continue;
                    }
                    double up = k * (u[i - 1] - u[i]);
                    double down = i < nodes ? k * (u[i] - u[i + 1]) : pump;
                    v[i] += (up - down - c * v[i]) / m * dt;
                }
                for (int i = 1; i <= nodes; i++) u[i] += v[i] * dt;
                double rodLoad = wrf + k * (u[0] - u[1]);
                if (last) {
                    prl[s] = rodLoad;
                    x0[s] = u[0];
                    peak = Math.max(peak, rodLoad);
                    min = Math.min(min, rodLoad);
                    work += rodLoad * v[0] * dt;
                    plungerMin = Math.min(plungerMin, u[nodes]);
                    plungerMax = Math.max(plungerMax, u[nodes]);
                } else {
                    yMin = Math.min(yMin, u[nodes]);
                    yMax = Math.max(yMax, u[nodes]);
                }
            }
            if (!last) {
                yBottom = yMin;
                yTop = yMax;
                yMin = Double.MAX_VALUE;
                yMax = -Double.MAX_VALUE;
            }
        }
        for (int j = 0; j < POINTS; j++) {
            int s = (int) ((long) j * steps / POINTS);
            pos[j] = x0[s];
            load[j] = prl[s];
        }
        return new DynoCard(period, pos, load, peak, min, Math.max(0, work / period), parted ? 0 : plungerMax - plungerMin, prl, x0, wrf,
                parted ? 0 : fo);
    }

    /**
     * Динамограмма за цикл: {@link #POINTS} точек положения, м, и нагрузки, Н; период, с; наибольшая и наименьшая
     * нагрузка; средняя мощность на полированном штоке, Вт; ход плунжера, м.
     */
    public record DynoCard(double periodS, double[] positionM, double[] loadN, double peakN, double minN, double rodPowerW,
            double plungerStrokeM, double[] fullLoadN, double[] fullPositionM, double rodWeightInFluidN, double fluidLoadN) {

        /**
         * Наибольший момент на выходном валу редуктора, Н·м, при уравновешивании {@code counterbalanceN}: T = TF·(PRL − C),
         * TF = dx/dθ — коэффициент момента (API Spec 11E, метод коэффициентов момента); противовес приведён к штоку.
         */
        public double peakTorqueNm(double counterbalanceN) {
            int n = fullLoadN.length;
            double peakT = 0;
            for (int i = 0; i < n; i++) {
                double tf = (fullPositionM[(i + 1) % n] - fullPositionM[(i - 1 + n) % n]) / (2 * 2 * Math.PI / n);
                peakT = Math.max(peakT, Math.abs(tf * (fullLoadN[i] - counterbalanceN)));
            }
            return peakT;
        }
    }
}
