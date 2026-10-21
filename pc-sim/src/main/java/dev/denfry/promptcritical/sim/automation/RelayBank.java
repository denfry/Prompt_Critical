package dev.denfry.promptcritical.sim.automation;

import java.util.Arrays;
import java.util.SplittableRandom;

/**
 * Релейная логика A1 станции (03d §3d.3): блоки питания, катушки, контакты, реле времени, шаговые
 * искатели, командоаппараты и реле процесса на общем наборе цепей (узлов). Схема решается синхронно
 * раз за тик (§3d.3.2): контакты — из состояний на начало тика, union-find по замкнутым ключам,
 * напряжение катушек с просадкой источника, новые состояния — со следующего тика. Пересчёт идёт
 * только для «грязных» цепей — связных частей схемы, в которых сменился ключ или вход источника.
 *
 * <p>Элементы адресуются индексами {@code int}: узлы, ключи (контакты), нагрузки (катушки, лампы),
 * источники, реле, искатели, командоаппараты, реле процесса. Узел {@code -1} — клемма не подключена.
 * Добавление элементов — между тиками; горячий цикл {@link #stepFast()} без аллокаций.
 */
public final class RelayBank {
    /** Мощность катушки промежуточного реле 24 В (03d §3d.3.2): R_coil = 576 Ом. */
    public static final double COIL_W = 1;
    /** Мощность лампы-индикатора, Вт. */
    public static final double LAMP_W = 2;
    /** Мощность магнитов шага и возврата искателя, Вт [BALANCE] (в GDD не задана). */
    public static final double STEPPER_COIL_W = 5;
    public static final double NOMINAL_V = 24;
    public static final int STEPPER_POSITIONS = 25, STEPPER_WIPERS = 3;
    public static final int CAMS = 12, CAM_SEGMENTS = 72;
    /** Доли режимов случайного отказа реле (03d §3d.3.3). */
    static final double P_COIL_OPEN = 0.40, P_WELDED = 0.25, P_STUCK_PICKED = 0.15;

    private static final byte SW_EXTERNAL = 0, SW_RELAY = 1, SW_STEPPER = 2, SW_CAM = 3, SW_PROCESS = 4;
    private static final byte LOAD_LAMP = 0, LOAD_COIL = 1, LOAD_RESET = 2, LOAD_STEP = 3, LOAD_RETURN = 4;
    private static final byte NO_FAULT = -1;
    private static final RelayState[] STATES = RelayState.values();

    private final AutomationConfig cfg;
    private final double dt;
    private final SplittableRandom rng;
    private final ContactGraph graph = new ContactGraph();
    private long tick;

    private int nodes;

    // Ключи (контакты)
    private int switches;
    private int[] swA = new int[16], swB = new int[16], swOwner = new int[16], swAux = new int[16];
    private byte[] swType = new byte[16];
    private boolean[] swNo = new boolean[16], swClosed = new boolean[16], swExt = new boolean[16];

    // Нагрузки: катушки и лампы
    private int loads;
    private int[] ldA = new int[16], ldB = new int[16], ldOwner = new int[16], ldSupply = new int[16];
    private byte[] ldType = new byte[16];
    private double[] ldR = new double[16], ldV = new double[16];
    private boolean[] ldOpen = new boolean[16];

    // Источники оперативного тока
    private int supplies;
    private int[] supPlus = new int[4], supZero = new int[4];
    private double[] supRatedA = new double[4], supNomV = new double[4], supInput = new double[4];
    private double[] supV = new double[4], supI = new double[4];
    private boolean[] supShorted = new boolean[4];

    // Реле
    private int relays;
    private RelayKind[] kind = new RelayKind[8];
    private byte[] state = new byte[8], pendingFault = new byte[8];
    private int[] opTimer = new int[8], coilLoad = new int[8], resetLoad = new int[8], weldedGroup = new int[8];
    private int[] cabinet = new int[8];
    private double[] ratedV = new double[8];
    private long[] opsCount = new long[8];
    private int[] opsSinceSlow = new int[8];
    private boolean[] weldArmature = new boolean[8];
    // Реле времени (tMode = null — обычное реле)
    private TimeRelayMode[] tMode = new TimeRelayMode[8];
    private TimeRelayType[] tType = new TimeRelayType[8];
    private double[] tSetS = new double[8], tBias = new double[8];
    private int[] tTarget = new int[8], tCount = new int[8];
    private boolean[] tOut = new boolean[8], tPrev = new boolean[8];

    // Шаговые искатели
    private int steppers;
    private int[] stPos = new int[2], stStepLoad = new int[2], stReturnLoad = new int[2], stCabinet = new int[2];
    private long[] stLastStep = new long[2], stSteps = new long[2], stMissed = new long[2];
    private int[] stStepsSinceSlow = new int[2];
    private boolean[] stPrevStep = new boolean[2], stDebounced = new boolean[2], stStuck = new boolean[2];
    private int[] stepperCommons = new int[0];

    // Командоаппараты
    private int cams;
    private double[] camAngle = new double[1], camRatio = new double[1], camRpm = new double[1];
    private boolean[] camProfile = new boolean[CAMS * CAM_SEGMENTS];

    // Реле процесса
    private int procs;
    private ProcessSwitchKind[] psKind = new ProcessSwitchKind[2];
    private boolean[] psHigh = new boolean[2], psOut = new boolean[2], psStuck = new boolean[2];
    private double[] psSet = new double[2], psReturn = new double[2], psBias = new double[2], psSigma = new double[2];
    private double[] psInput = new double[2], psLag = new double[2], psMeasured = new double[2];
    private int[] psCabinet = new int[2];

    private double[] cabinetTempK = new double[1];

    // Цепи (связные части схемы), CSR
    private boolean topologyDirty = true;
    private int circuits;
    private int[] nodeCircuit = new int[0], swCircuit = new int[0], supCircuit = new int[0];
    private int[] circNodeOff, circNodes, circSwOff, circSw, circLoadOff, circLoads, circSupOff, circSup;
    private boolean[] circDirty = new boolean[0];

    public RelayBank(AutomationConfig cfg, double dtS, long seed) {
        if (!(dtS > 0)) throw new IllegalArgumentException("dtS");
        this.cfg = cfg;
        this.dt = dtS;
        this.rng = new SplittableRandom(seed);
        Arrays.fill(cabinetTempK, 298.15);
    }

    // ---------------------------------------------------------------- построение

    public int newNode() {
        topologyDirty = true;
        return nodes++;
    }

    /** Блок питания 24 В DC номинального тока I_n (03d §3d.3.2); вход от шины 07 — {@link #setSupplyInput}. */
    public int addSupply(int plus, int zero, double ratedA) {
        checkNode(plus);
        checkNode(zero);
        if (!(ratedA > 0) || plus == zero) throw new IllegalArgumentException("supply");
        int s = supplies++;
        if (s == supPlus.length) {
            int n = s * 2;
            supPlus = Arrays.copyOf(supPlus, n);
            supZero = Arrays.copyOf(supZero, n);
            supRatedA = Arrays.copyOf(supRatedA, n);
            supNomV = Arrays.copyOf(supNomV, n);
            supInput = Arrays.copyOf(supInput, n);
            supV = Arrays.copyOf(supV, n);
            supI = Arrays.copyOf(supI, n);
            supShorted = Arrays.copyOf(supShorted, n);
        }
        supPlus[s] = plus;
        supZero[s] = zero;
        supRatedA[s] = ratedA;
        supNomV[s] = NOMINAL_V;
        supInput[s] = 1;
        topologyDirty = true;
        return s;
    }

    /** Промежуточное реле ({@code GENERAL} или {@code FORCE_GUIDED}) с катушкой A1–A2 на 24 В. */
    public int addRelay(RelayKind kind, int cabinet, int coilA, int coilB) {
        if (kind == RelayKind.LATCHING) throw new IllegalArgumentException("use addLatchingRelay");
        int r = newRelay(kind, cabinet);
        coilLoad[r] = addLoad(coilA, coilB, NOMINAL_V * NOMINAL_V / COIL_W, LOAD_COIL, r);
        return r;
    }

    /** Бистабильное реле: катушки SET (A1) и RESET (B1); RESET приоритетнее. */
    public int addLatchingRelay(int cabinet, int setA, int setB, int resetA, int resetB) {
        int r = newRelay(RelayKind.LATCHING, cabinet);
        coilLoad[r] = addLoad(setA, setB, NOMINAL_V * NOMINAL_V / COIL_W, LOAD_COIL, r);
        resetLoad[r] = addLoad(resetA, resetB, NOMINAL_V * NOMINAL_V / COIL_W, LOAD_RESET, r);
        return r;
    }

    /**
     * Реле времени (03d §3d.3.4): группа 0 — контакты с выдержкой (15-16-18), группа 1 — мгновенные
     * (25-26-28). Ошибка выдержки b_t ~ U(±δ_t) разыгрывается здесь и сохраняется при смене уставки.
     */
    public int addTimeRelay(TimeRelayType type, TimeRelayMode mode, double setpointS, int cabinet, int coilA, int coilB) {
        checkTimeSetpoint(type, setpointS);
        int r = newRelay(RelayKind.GENERAL, cabinet);
        coilLoad[r] = addLoad(coilA, coilB, NOMINAL_V * NOMINAL_V / type.coilW, LOAD_COIL, r);
        tMode[r] = mode;
        tType[r] = type;
        tBias[r] = (2 * rng.nextDouble() - 1) * type.spread;
        setTimeSetpoint(r, setpointS);
        return r;
    }

    /**
     * Контакт реле: группа 0 (11-12-14) или 1 (21-22-24); {@code normallyOpen} — НО (11-14), иначе НЗ
     * (11-12). Переключающая группа — два вызова с общим узлом.
     */
    public int addContact(int relay, int group, boolean normallyOpen, int a, int b) {
        checkRelay(relay);
        if (group < 0 || group > 1) throw new IllegalArgumentException("group");
        return newSwitch(a, b, SW_RELAY, relay, group, normallyOpen);
    }

    /** Ключ, управляемый извне: кнопка, переключатель, ключ-бирка пульта 03c, концевик механизма. */
    public int addSwitch(int a, int b, boolean closed) {
        int s = newSwitch(a, b, SW_EXTERNAL, -1, 0, true);
        swExt[s] = closed;
        return s;
    }

    /** Лампа, сирена или другая нагрузка мощностью {@code watts} при 24 В. */
    public int addLamp(int a, int b, double watts) {
        if (!(watts > 0)) throw new IllegalArgumentException("watts");
        return addLoad(a, b, NOMINAL_V * NOMINAL_V / watts, LOAD_LAMP, -1);
    }

    /**
     * Шаговый искатель на 25 положений × 3 ламели (03d §3d.3.5): катушка шага, катушка возврата,
     * общие щётки {@code commons[3]} (узел или -1). Поле — {@link #setStepperBank}. {@code debounced} —
     * вход шага с подавлением дребезга (RC-цепочка в GUI); иначе каждое замыкание даёт k_b лишних шагов.
     */
    public int addSteppingSwitch(int cabinet, int stepA, int stepB, int returnA, int returnB, int[] commons,
            boolean debounced) {
        if (commons.length != STEPPER_WIPERS) throw new IllegalArgumentException("commons");
        int s = steppers++;
        if (s == stPos.length) {
            int n = s * 2;
            stPos = Arrays.copyOf(stPos, n);
            stStepLoad = Arrays.copyOf(stStepLoad, n);
            stReturnLoad = Arrays.copyOf(stReturnLoad, n);
            stCabinet = Arrays.copyOf(stCabinet, n);
            stLastStep = Arrays.copyOf(stLastStep, n);
            stSteps = Arrays.copyOf(stSteps, n);
            stMissed = Arrays.copyOf(stMissed, n);
            stStepsSinceSlow = Arrays.copyOf(stStepsSinceSlow, n);
            stPrevStep = Arrays.copyOf(stPrevStep, n);
            stDebounced = Arrays.copyOf(stDebounced, n);
            stStuck = Arrays.copyOf(stStuck, n);
        }
        countRelaySlot();
        stPos[s] = 1;
        stCabinet[s] = checkCabinet(cabinet);
        stLastStep[s] = Long.MIN_VALUE / 2;
        stDebounced[s] = debounced;
        double r = NOMINAL_V * NOMINAL_V / STEPPER_COIL_W;
        stStepLoad[s] = addLoad(stepA, stepB, r, LOAD_STEP, s);
        stReturnLoad[s] = addLoad(returnA, returnB, r, LOAD_RETURN, s);
        stepperCommons = Arrays.copyOf(stepperCommons, steppers * STEPPER_WIPERS);
        for (int w = 0; w < STEPPER_WIPERS; w++) stepperCommons[s * STEPPER_WIPERS + w] = commons[w];
        return s;
    }

    /** Перемычка поля искателя: в положении {@code position} (1…25) ламель {@code wiper} соединяет общую щётку с узлом. */
    public int setStepperBank(int stepper, int position, int wiper, int node) {
        checkIndex(stepper, steppers, "stepper");
        if (position < 1 || position > STEPPER_POSITIONS || wiper < 0 || wiper >= STEPPER_WIPERS)
            throw new IllegalArgumentException("position/wiper");
        int common = stepperCommons[stepper * STEPPER_WIPERS + wiper];
        if (common < 0) throw new IllegalArgumentException("wiper has no common");
        return newSwitch(common, node, SW_STEPPER, stepper, position * STEPPER_WIPERS + wiper, true);
    }

    /** Командоаппарат: 12 кулачков на барабане, передаточное число вал Create → барабан 1:1…1:3600. */
    public int addCamController(double ratio) {
        if (!(ratio >= 1 && ratio <= 3600)) throw new IllegalArgumentException("ratio");
        int c = cams++;
        if (c == camAngle.length) {
            int n = c * 2;
            camAngle = Arrays.copyOf(camAngle, n);
            camRatio = Arrays.copyOf(camRatio, n);
            camRpm = Arrays.copyOf(camRpm, n);
            camProfile = Arrays.copyOf(camProfile, n * CAMS * CAM_SEGMENTS);
        }
        camRatio[c] = ratio;
        return c;
    }

    /** Контакт кулачка: НО замкнут, пока кулачок «включён» на текущем угле барабана; НЗ — наоборот. */
    public int addCamContact(int controller, int cam, boolean normallyOpen, int a, int b) {
        checkIndex(controller, cams, "controller");
        checkIndex(cam, CAMS, "cam");
        return newSwitch(a, b, SW_CAM, controller, cam, normallyOpen);
    }

    /** Профиль кулачка: включён на [fromDeg, toDeg) с шагом 5° (через 0°, если from &gt; to). */
    public void setCamInterval(int controller, int cam, double fromDeg, double toDeg) {
        checkIndex(controller, cams, "controller");
        checkIndex(cam, CAMS, "cam");
        int base = (controller * CAMS + cam) * CAM_SEGMENTS;
        int from = Math.floorMod((int) Math.round(fromDeg / 5), CAM_SEGMENTS);
        int to = Math.floorMod((int) Math.round(toDeg / 5), CAM_SEGMENTS);
        for (int i = from; i != to; i = (i + 1) % CAM_SEGMENTS) camProfile[base + i] = true;
    }

    public void clearCam(int controller, int cam) {
        int base = (controller * CAMS + cam) * CAM_SEGMENTS;
        Arrays.fill(camProfile, base, base + CAM_SEGMENTS, false);
    }

    /**
     * Реле процесса (03d §3d.3.6): «высокое» срабатывает при y ≥ x_set и возвращается при y ≤ x_set − Δ,
     * «низкое» — зеркально. Смещение b ~ U(±погрешность) разыгрывается при установке, шум σ = 0,1·погрешность.
     *
     * @param returnZone зона возврата Δ в единицах процесса; NaN — по умолчанию для вида
     */
    public int addProcessSwitch(ProcessSwitchKind kind, boolean high, double setpoint, double span, double returnZone,
            int cabinet) {
        if (!(span > 0)) throw new IllegalArgumentException("span");
        int p = procs++;
        if (p == psKind.length) {
            int n = p * 2;
            psKind = Arrays.copyOf(psKind, n);
            psHigh = Arrays.copyOf(psHigh, n);
            psOut = Arrays.copyOf(psOut, n);
            psStuck = Arrays.copyOf(psStuck, n);
            psSet = Arrays.copyOf(psSet, n);
            psReturn = Arrays.copyOf(psReturn, n);
            psBias = Arrays.copyOf(psBias, n);
            psSigma = Arrays.copyOf(psSigma, n);
            psInput = Arrays.copyOf(psInput, n);
            psLag = Arrays.copyOf(psLag, n);
            psMeasured = Arrays.copyOf(psMeasured, n);
            psCabinet = Arrays.copyOf(psCabinet, n);
        }
        double acc = kind.accuracyAbs(span, setpoint);
        psKind[p] = kind;
        psHigh[p] = high;
        psSet[p] = setpoint;
        psReturn[p] = Double.isNaN(returnZone) ? kind.defaultReturnAbs(span, setpoint) : returnZone;
        if (!(psReturn[p] >= 0)) throw new IllegalArgumentException("returnZone");
        psBias[p] = (2 * rng.nextDouble() - 1) * acc;
        psSigma[p] = 0.1 * acc;
        psLag[p] = Double.NaN;
        psCabinet[p] = checkCabinet(cabinet);
        return p;
    }

    /** Контакт реле процесса 11-12-14: НО замкнут, когда реле сработало. */
    public int addProcessContact(int processSwitch, boolean normallyOpen, int a, int b) {
        checkIndex(processSwitch, procs, "processSwitch");
        return newSwitch(a, b, SW_PROCESS, processSwitch, 0, normallyOpen);
    }

    // ---------------------------------------------------------------- входы

    public void setSwitch(int sw, boolean closed) {
        checkIndex(sw, switches, "switch");
        if (swType[sw] != SW_EXTERNAL) throw new IllegalArgumentException("not an external switch");
        swExt[sw] = closed;
    }

    /** Напряжение на входе блока питания, о.е. шины 07 (0 — нет питания). */
    public void setSupplyInput(int supply, double pu) {
        checkIndex(supply, supplies, "supply");
        if (!(pu >= 0)) throw new IllegalArgumentException("pu");
        if (supInput[supply] != pu && !topologyDirty) circDirty[supCircuit[supply]] = true;
        supInput[supply] = pu;
    }

    /** Измеряемая величина реле процесса (давление Па, уровень м, температура K, расход кг/с — как у узла). */
    public void setProcessValue(int processSwitch, double x) {
        checkIndex(processSwitch, procs, "processSwitch");
        psInput[processSwitch] = x;
    }

    /** Скорость вала Create на командоаппарате, об/мин. */
    public void setCamShaftRpm(int controller, double rpm) {
        checkIndex(controller, cams, "controller");
        camRpm[controller] = rpm;
    }

    public void setCabinetTemperature(int cabinet, double kelvin) {
        checkCabinet(cabinet);
        cabinetTempK[cabinet] = kelvin;
    }

    /** Подстройка уставки реле времени игроком; индивидуальная ошибка b_t сохраняется. */
    public void setTimeSetpoint(int relay, double setpointS) {
        checkRelay(relay);
        if (tMode[relay] == null) throw new IllegalArgumentException("not a time relay");
        checkTimeSetpoint(tType[relay], setpointS);
        tSetS[relay] = setpointS;
        tTarget[relay] = Math.max(1, (int) Math.round(setpointS * (1 + tBias[relay]) / dt));
    }

    // ---------------------------------------------------------------- отказы и ремонт

    /** Наложить отказ (диагностика, сценарии); неприменимый в текущем положении ждёт подходящего перехода. */
    public void injectFault(int relay, RelayState mode, int group) {
        checkRelay(relay);
        if (!mode.isFailed()) throw new IllegalArgumentException("not a failure mode");
        weldedGroup[relay] = group;
        if (!applyFault(relay, mode)) pendingFault[relay] = (byte) mode.ordinal();
    }

    /** Замена реле новым (предмет {@code relay}): состояние DROPPED, счётчик коммутаций с нуля. */
    public void replaceRelay(int relay) {
        checkRelay(relay);
        state[relay] = (byte) RelayState.DROPPED.ordinal();
        pendingFault[relay] = NO_FAULT;
        opsCount[relay] = 0;
        opsSinceSlow[relay] = 0;
        opTimer[relay] = 0;
        tCount[relay] = 0;
        tOut[relay] = false;
        tPrev[relay] = false;
        setCoilOpen(relay, false);
    }

    public void replaceStepper(int stepper) {
        checkIndex(stepper, steppers, "stepper");
        stStuck[stepper] = false;
        stSteps[stepper] = 0;
    }

    public void repairProcessSwitch(int processSwitch) {
        checkIndex(processSwitch, procs, "processSwitch");
        psStuck[processSwitch] = false;
    }

    // ---------------------------------------------------------------- шаги

    /** FAST-шаг (этап 9a): §3d.3.2, пп. 1–4. */
    public void stepFast() {
        if (topologyDirty) compile();
        updateCams();
        updateProcessSwitches();
        refreshSwitches();
        for (int c = 0; c < circuits; c++) {
            if (circDirty[c]) {
                solve(c);
                circDirty[c] = false;
            }
        }
        for (int r = 0; r < relays; r++) updateRelay(r);
        for (int s = 0; s < steppers; s++) updateStepper(s);
        tick++;
    }

    /**
     * SLOW-шаг: пуассоновские отказы λ = λ₀·f_T(T_шк) + n_ops/N_life/Δt (03d §3d.3.3) реле, искателей и
     * реле процесса. Режим отказа реле: COIL_OPEN 0,40, WELDED 0,25, STUCK_PICKED 0,15, STUCK_DROPPED 0,20.
     */
    public void stepSlow(double dtPhysS) {
        double baseRate = cfg.baseFailureRatePerDay() / 86400;
        for (int r = 0; r < relays; r++) {
            int ops = opsSinceSlow[r];
            opsSinceSlow[r] = 0;
            if (STATES[state[r]].isFailed() || pendingFault[r] != NO_FAULT) continue;
            double h = baseRate * cfg.arrheniusFactor(cabinetTempK[cabinet[r]]) * dtPhysS + ops / cfg.electricalLifeOps();
            if (rng.nextDouble() >= -Math.expm1(-h)) continue;
            double u = rng.nextDouble();
            RelayState mode = u < P_COIL_OPEN ? RelayState.COIL_OPEN
                    : u < P_COIL_OPEN + P_WELDED ? RelayState.WELDED
                    : u < P_COIL_OPEN + P_WELDED + P_STUCK_PICKED ? RelayState.STUCK_PICKED : RelayState.STUCK_DROPPED;
            weldedGroup[r] = rng.nextInt(2);
            if (!applyFault(r, mode)) pendingFault[r] = (byte) mode.ordinal();
        }
        for (int s = 0; s < steppers; s++) {
            int n = stStepsSinceSlow[s];
            stStepsSinceSlow[s] = 0;
            if (stStuck[s]) continue;
            double h = baseRate * cfg.arrheniusFactor(cabinetTempK[stCabinet[s]]) * dtPhysS + n / cfg.stepperLifeSteps();
            if (rng.nextDouble() < -Math.expm1(-h)) stStuck[s] = true;
        }
        for (int p = 0; p < procs; p++) {
            if (psStuck[p]) continue;
            double h = baseRate * cfg.arrheniusFactor(cabinetTempK[psCabinet[p]]) * dtPhysS;
            if (rng.nextDouble() < -Math.expm1(-h)) psStuck[p] = true;
        }
    }

    // ---------------------------------------------------------------- выходы

    public long tick() {
        return tick;
    }

    public RelayState relayState(int relay) {
        checkRelay(relay);
        return STATES[state[relay]];
    }

    public boolean isPicked(int relay) {
        return relayState(relay).armaturePicked();
    }

    /** Срабатываний (притяжений якоря) за всё время — износ. */
    public long opsCount(int relay) {
        checkRelay(relay);
        return opsCount[relay];
    }

    /** Напряжение на катушке (A1 у бистабильного), В. */
    public double coilVoltage(int relay) {
        checkRelay(relay);
        return ldV[coilLoad[relay]];
    }

    public boolean contactClosed(int sw) {
        checkIndex(sw, switches, "switch");
        return swClosed[sw];
    }

    public double loadVoltage(int load) {
        checkIndex(load, loads, "load");
        return ldV[load];
    }

    public boolean lampLit(int load) {
        return loadVoltage(load) >= cfg.pickupPu() * NOMINAL_V;
    }

    public double supplyVoltage(int supply) {
        checkIndex(supply, supplies, "supply");
        return supV[supply];
    }

    public double supplyCurrent(int supply) {
        checkIndex(supply, supplies, "supply");
        return supI[supply];
    }

    /** Фактическая выдержка реле времени с учётом b_t, с. */
    public double actualDelayS(int relay) {
        checkRelay(relay);
        if (tMode[relay] == null) throw new IllegalArgumentException("not a time relay");
        return tTarget[relay] * dt;
    }

    public boolean timerOutput(int relay) {
        checkRelay(relay);
        return tOut[relay];
    }

    public int stepperPosition(int stepper) {
        checkIndex(stepper, steppers, "stepper");
        return stPos[stepper];
    }

    /** Диагностика {@code MISSED_STEP}: импульсы шага чаще предела. */
    public long missedSteps(int stepper) {
        checkIndex(stepper, steppers, "stepper");
        return stMissed[stepper];
    }

    public long stepperSteps(int stepper) {
        checkIndex(stepper, steppers, "stepper");
        return stSteps[stepper];
    }

    public boolean stepperStuck(int stepper) {
        checkIndex(stepper, steppers, "stepper");
        return stStuck[stepper];
    }

    public double camAngleDeg(int controller) {
        checkIndex(controller, cams, "controller");
        return camAngle[controller];
    }

    public boolean processOutput(int processSwitch) {
        checkIndex(processSwitch, procs, "processSwitch");
        return psOut[processSwitch];
    }

    /** Показание чувствительного элемента реле процесса после запаздывания, смещения и шума. */
    public double processMeasured(int processSwitch) {
        checkIndex(processSwitch, procs, "processSwitch");
        return psMeasured[processSwitch];
    }

    public boolean processStuck(int processSwitch) {
        checkIndex(processSwitch, procs, "processSwitch");
        return psStuck[processSwitch];
    }

    /** Тепловыделение катушек и ламп шкафа U²/R, Вт (03d §3d.13.1). */
    public double cabinetHeatW(int cabinet) {
        checkCabinet(cabinet);
        double p = 0;
        for (int l = 0; l < loads; l++) {
            if (ldOpen[l] || ldV[l] == 0 || loadCabinet(l) != cabinet) continue;
            p += ldV[l] * ldV[l] / ldR[l];
        }
        return p;
    }

    public int relayCount() {
        return relays;
    }

    // ---------------------------------------------------------------- перенос при пересборке схемы

    /** Память реле для новой схемы (банк только растёт, поэтому мир пересобирает его целиком). */
    double[] memoRelay(int r) {
        checkRelay(r);
        return new double[] {state[r], pendingFault[r], opTimer[r], weldedGroup[r], opsCount[r], weldArmature[r] ? 1 : 0,
                tBias[r], tCount[r], tOut[r] ? 1 : 0, tPrev[r] ? 1 : 0};
    }

    void restoreRelay(int r, double[] m) {
        checkRelay(r);
        if (m == null || m.length < 10) return;
        state[r] = (byte) Math.max(0, Math.min(STATES.length - 1, (int) m[0]));
        pendingFault[r] = (byte) m[1];
        opTimer[r] = (int) m[2];
        weldedGroup[r] = (int) m[3];
        opsCount[r] = (long) m[4];
        weldArmature[r] = m[5] != 0;
        if (tMode[r] != null) {
            tBias[r] = m[6];
            setTimeSetpoint(r, tSetS[r]);
            tCount[r] = (int) m[7];
            tOut[r] = m[8] != 0;
            tPrev[r] = m[9] != 0;
        }
        setCoilOpen(r, STATES[state[r]] == RelayState.COIL_OPEN);
    }

    double[] memoStepper(int s) {
        checkIndex(s, steppers, "stepper");
        return new double[] {stPos[s], stSteps[s], stMissed[s], stStuck[s] ? 1 : 0, stPrevStep[s] ? 1 : 0,
                Math.min(1e6, tick - stLastStep[s])};
    }

    void restoreStepper(int s, double[] m) {
        checkIndex(s, steppers, "stepper");
        if (m == null || m.length < 6) return;
        stPos[s] = Math.max(1, Math.min(STEPPER_POSITIONS, (int) m[0]));
        stSteps[s] = (long) m[1];
        stMissed[s] = (long) m[2];
        stStuck[s] = m[3] != 0;
        stPrevStep[s] = m[4] != 0;
        stLastStep[s] = tick - (long) m[5];
    }

    void restoreCamAngle(int c, double deg) {
        checkIndex(c, cams, "controller");
        if (Double.isFinite(deg)) camAngle[c] = Math.floorMod((long) Math.floor(deg * 1000), 360_000L) / 1000.0;
    }

    double[] memoProcess(int p) {
        checkIndex(p, procs, "processSwitch");
        return new double[] {psBias[p], psLag[p], psOut[p] ? 1 : 0, psStuck[p] ? 1 : 0};
    }

    void restoreProcess(int p, double[] m) {
        checkIndex(p, procs, "processSwitch");
        if (m == null || m.length < 4) return;
        psBias[p] = m[0];
        psLag[p] = m[1];
        psOut[p] = m[2] != 0;
        psStuck[p] = m[3] != 0;
    }

    // ---------------------------------------------------------------- внутреннее

    private int loadCabinet(int l) {
        return switch (ldType[l]) {
            case LOAD_COIL, LOAD_RESET -> cabinet[ldOwner[l]];
            case LOAD_STEP, LOAD_RETURN -> stCabinet[ldOwner[l]];
            default -> 0;
        };
    }

    private void updateCams() {
        for (int c = 0; c < cams; c++) {
            double a = camAngle[c] + camRpm[c] * 6 * dt / camRatio[c];
            a %= 360;
            camAngle[c] = a < 0 ? a + 360 : a;
        }
    }

    private void updateProcessSwitches() {
        for (int p = 0; p < procs; p++) {
            double x = psInput[p];
            double lag = psLag[p];
            lag = Double.isNaN(lag) ? x : lag + (x - lag) * -Math.expm1(-dt / psKind[p].tauS);
            psLag[p] = lag;
            double y = lag + psBias[p] + psSigma[p] * rng.nextGaussian();
            psMeasured[p] = y;
            if (psStuck[p]) continue;
            double set = psSet[p], ret = psReturn[p];
            if (psHigh[p]) {
                if (y >= set) psOut[p] = true;
                else if (y <= set - ret) psOut[p] = false;
            } else {
                if (y <= set) psOut[p] = true;
                else if (y >= set + ret) psOut[p] = false;
            }
        }
    }

    private void refreshSwitches() {
        for (int s = 0; s < switches; s++) {
            boolean closed = switchClosedNow(s);
            if (closed != swClosed[s]) {
                swClosed[s] = closed;
                circDirty[swCircuit[s]] = true;
            }
        }
    }

    private boolean switchClosedNow(int s) {
        int owner = swOwner[s];
        return switch (swType[s]) {
            case SW_EXTERNAL -> swExt[s];
            case SW_RELAY -> relayContactClosed(owner, swAux[s], swNo[s]);
            case SW_STEPPER -> stPos[owner] == swAux[s] / STEPPER_WIPERS;
            case SW_CAM -> {
                int seg = Math.min(CAM_SEGMENTS - 1, (int) (camAngle[owner] / 5));
                yield camProfile[(owner * CAMS + swAux[s]) * CAM_SEGMENTS + seg] == swNo[s];
            }
            case SW_PROCESS -> psOut[owner] == swNo[s];
            default -> false;
        };
    }

    private boolean relayContactClosed(int r, int group, boolean no) {
        RelayState st = STATES[state[r]];
        if (st == RelayState.WELDED) {
            if (group == weldedGroup[r]) return no;
            boolean arm = weldArmature[r];
            if (kind[r] == RelayKind.FORCE_GUIDED && !arm) return false; // якорь не дошёл: НЗ и прочие НО разомкнуты
            return arm == no;
        }
        boolean operated = group == 0 && tMode[r] != null ? tOut[r] : st.armaturePicked();
        return operated == no;
    }

    private void solve(int c) {
        for (int i = circNodeOff[c]; i < circNodeOff[c + 1]; i++) graph.reset(circNodes[i]);
        for (int i = circSwOff[c]; i < circSwOff[c + 1]; i++) {
            int s = circSw[i];
            if (swClosed[s]) graph.union(swA[s], swB[s]);
        }
        for (int i = circSupOff[c]; i < circSupOff[c + 1]; i++) {
            int s = circSup[i];
            supShorted[s] = graph.find(supPlus[s]) == graph.find(supZero[s]);
            supI[s] = 0;
        }
        for (int i = circLoadOff[c]; i < circLoadOff[c + 1]; i++) {
            int l = circLoads[i];
            ldSupply[l] = -1;
            if (ldOpen[l]) continue;
            int ra = graph.find(ldA[l]), rb = graph.find(ldB[l]);
            if (ra == rb) continue;
            for (int j = circSupOff[c]; j < circSupOff[c + 1]; j++) {
                int s = circSup[j];
                if (supShorted[s]) continue;
                int rp = graph.find(supPlus[s]), rz = graph.find(supZero[s]);
                if ((ra == rp && rb == rz) || (ra == rz && rb == rp)) {
                    ldSupply[l] = s;
                    supI[s] += supNomV[s] * supInput[s] / ldR[l];
                    break;
                }
            }
        }
        for (int i = circSupOff[c]; i < circSupOff[c + 1]; i++) {
            int s = circSup[i];
            double u = supNomV[s] * supInput[s];
            if (supShorted[s]) {
                supV[s] = 0;
                supI[s] = supRatedA[s];
            } else if (supI[s] > supRatedA[s]) {
                supV[s] = u * supRatedA[s] / supI[s];
                supI[s] = supRatedA[s];
            } else {
                supV[s] = u;
            }
        }
        for (int i = circLoadOff[c]; i < circLoadOff[c + 1]; i++) {
            int l = circLoads[i];
            ldV[l] = ldSupply[l] < 0 ? 0 : supV[ldSupply[l]];
        }
    }

    /** Команда катушки: 1 — притянуть, -1 — отпустить, 0 — держать (гистерезис). */
    private int coilCommand(int r) {
        double un = ratedV[r];
        if (kind[r] == RelayKind.LATCHING) {
            if (ldV[resetLoad[r]] >= cfg.pickupPu() * un) return -1;
            return ldV[coilLoad[r]] >= cfg.pickupPu() * un ? 1 : 0;
        }
        double u = ldV[coilLoad[r]];
        if (u >= cfg.pickupPu() * un) return 1;
        if (u <= cfg.dropoutPu() * un) return -1;
        return 0;
    }

    private void updateRelay(int r) {
        RelayState st = STATES[state[r]];
        int cmd = coilCommand(r);
        switch (st) {
            case DROPPED -> {
                if (cmd > 0) {
                    opTimer[r] = cfg.operateTicks() - 1;
                    if (opTimer[r] == 0) pick(r);
                    else state[r] = (byte) RelayState.PICKING.ordinal();
                }
            }
            case PICKING -> {
                if (cmd < 0) state[r] = (byte) RelayState.DROPPED.ordinal();
                else if (--opTimer[r] <= 0) pick(r);
            }
            case PICKED -> {
                if (cmd < 0) {
                    opTimer[r] = cfg.operateTicks() - 1;
                    if (opTimer[r] == 0) drop(r);
                    else state[r] = (byte) RelayState.RELEASING.ordinal();
                }
            }
            case RELEASING -> {
                if (cmd > 0) state[r] = (byte) RelayState.PICKED.ordinal();
                else if (--opTimer[r] <= 0) drop(r);
            }
            case WELDED -> {
                if (cmd > 0 && !weldArmature[r]) {
                    weldArmature[r] = true;
                    countOp(r);
                } else if (cmd < 0) {
                    weldArmature[r] = false;
                }
            }
            default -> {
            }
        }
        if (tMode[r] != null) updateTimer(r);
    }

    private void pick(int r) {
        state[r] = (byte) RelayState.PICKED.ordinal();
        countOp(r);
        byte f = pendingFault[r];
        if (f == RelayState.WELDED.ordinal() || f == RelayState.STUCK_PICKED.ordinal()) {
            pendingFault[r] = NO_FAULT;
            applyFault(r, STATES[f]);
        }
    }

    private void drop(int r) {
        state[r] = (byte) RelayState.DROPPED.ordinal();
        if (pendingFault[r] == RelayState.STUCK_DROPPED.ordinal()) {
            pendingFault[r] = NO_FAULT;
            applyFault(r, RelayState.STUCK_DROPPED);
        }
    }

    private void countOp(int r) {
        opsCount[r]++;
        opsSinceSlow[r]++;
    }

    /** Применить отказ, если он возможен в текущем положении якоря. */
    private boolean applyFault(int r, RelayState mode) {
        RelayState st = STATES[state[r]];
        boolean picked = st == RelayState.PICKED || st == RelayState.RELEASING;
        boolean ok = switch (mode) {
            case COIL_OPEN -> true;
            case WELDED, STUCK_PICKED -> picked;
            case STUCK_DROPPED -> st == RelayState.DROPPED || st == RelayState.PICKING;
            default -> false;
        };
        if (!ok) return false;
        state[r] = (byte) mode.ordinal();
        weldArmature[r] = picked;
        if (mode == RelayState.COIL_OPEN) setCoilOpen(r, true);
        return true;
    }

    private void setCoilOpen(int r, boolean open) {
        setLoadOpen(coilLoad[r], open);
        if (kind[r] == RelayKind.LATCHING) setLoadOpen(resetLoad[r], open);
    }

    private void setLoadOpen(int l, boolean open) {
        if (ldOpen[l] == open) return;
        ldOpen[l] = open;
        if (open) ldV[l] = 0;
        if (!topologyDirty) circDirty[nodeCircuit[ldA[l] >= 0 ? ldA[l] : Math.max(ldB[l], 0)]] = true;
    }

    private void updateTimer(int r) {
        boolean picked = STATES[state[r]] == RelayState.WELDED ? weldArmature[r]
                : STATES[state[r]].armaturePicked();
        int n = tTarget[r];
        switch (tMode[r]) {
            case ON_DELAY -> {
                if (picked) {
                    if (tCount[r] < n) tCount[r]++;
                    tOut[r] = tCount[r] >= n;
                } else {
                    tCount[r] = 0;
                    tOut[r] = false;
                }
            }
            case OFF_DELAY -> {
                if (picked) {
                    tOut[r] = true;
                    tCount[r] = 0;
                } else if (tOut[r] && ++tCount[r] >= n) {
                    tOut[r] = false;
                }
            }
            case PULSE -> {
                if (tOut[r]) {
                    if (++tCount[r] >= n) tOut[r] = false;
                } else if (picked && !tPrev[r]) {
                    tOut[r] = true;
                    tCount[r] = 0;
                }
            }
            case FLASHER -> {
                if (!picked) {
                    tOut[r] = false;
                    tCount[r] = 0;
                } else if (!tPrev[r]) {
                    tOut[r] = true;
                    tCount[r] = 0;
                } else if (++tCount[r] >= n) {
                    tOut[r] = !tOut[r];
                    tCount[r] = 0;
                }
            }
        }
        tPrev[r] = picked;
    }

    private void updateStepper(int s) {
        double pu = cfg.pickupPu() * NOMINAL_V;
        boolean stepOn = ldV[stStepLoad[s]] >= pu;
        boolean returnOn = ldV[stReturnLoad[s]] >= pu;
        boolean edge = stepOn && !stPrevStep[s];
        stPrevStep[s] = stepOn;
        if (stStuck[s]) return;
        long minTicks = (long) Math.ceil(1 / (cfg.stepperMaxRateHz() * dt) - 1e-9);
        boolean ready = tick - stLastStep[s] >= minTicks;
        if (returnOn) {
            if (stPos[s] != 1 && ready) advance(s, 1);
        } else if (edge) {
            if (!ready) {
                stMissed[s]++;
            } else {
                int bounce = stDebounced[s] ? 0 : poisson(cfg.bouncePulsesMean());
                advance(s, 1 + bounce);
            }
        }
    }

    private void advance(int s, int n) {
        stPos[s] = (stPos[s] - 1 + n) % STEPPER_POSITIONS + 1;
        stLastStep[s] = tick;
        stSteps[s] += n;
        stStepsSinceSlow[s] += n;
    }

    /** Пуассон по Кнуту: малые средние (k̄_b ≤ 10). */
    private int poisson(double mean) {
        if (mean <= 0) return 0;
        double l = Math.exp(-mean), p = 1;
        int k = 0;
        do {
            k++;
            p *= rng.nextDouble();
        } while (p > l);
        return k - 1;
    }

    /** Разбиение схемы на независимые цепи: все ключи считаются замкнутыми, источник связывает «+» и «0». */
    private void compile() {
        graph.ensure(nodes);
        for (int n = 0; n < nodes; n++) graph.reset(n);
        for (int s = 0; s < switches; s++) graph.union(swA[s], swB[s]);
        for (int l = 0; l < loads; l++) graph.union(ldA[l], ldB[l]);
        for (int s = 0; s < supplies; s++) graph.union(supPlus[s], supZero[s]);
        int[] rootCircuit = new int[nodes];
        Arrays.fill(rootCircuit, -1);
        nodeCircuit = new int[nodes];
        circuits = 0;
        for (int n = 0; n < nodes; n++) {
            int root = graph.find(n);
            if (rootCircuit[root] < 0) rootCircuit[root] = circuits++;
            nodeCircuit[n] = rootCircuit[root];
        }
        swCircuit = new int[switches];
        for (int s = 0; s < switches; s++) swCircuit[s] = nodeCircuit[swA[s]];
        supCircuit = new int[supplies];
        for (int s = 0; s < supplies; s++) supCircuit[s] = nodeCircuit[supPlus[s]];
        int[] ldCircuit = new int[loads];
        for (int l = 0; l < loads; l++) ldCircuit[l] = nodeCircuit[ldA[l]];
        circNodeOff = new int[circuits + 1];
        circNodes = bucket(nodeCircuit, nodes, circNodeOff);
        circSwOff = new int[circuits + 1];
        circSw = bucket(swCircuit, switches, circSwOff);
        circLoadOff = new int[circuits + 1];
        circLoads = bucket(ldCircuit, loads, circLoadOff);
        circSupOff = new int[circuits + 1];
        circSup = bucket(supCircuit, supplies, circSupOff);
        circDirty = new boolean[circuits];
        Arrays.fill(circDirty, true);
        for (int s = 0; s < switches; s++) swClosed[s] = switchClosedNow(s);
        topologyDirty = false;
    }

    private int[] bucket(int[] circuitOf, int n, int[] off) {
        for (int i = 0; i < n; i++) off[circuitOf[i] + 1]++;
        for (int c = 0; c < circuits; c++) off[c + 1] += off[c];
        int[] fill = Arrays.copyOf(off, circuits);
        int[] out = new int[n];
        for (int i = 0; i < n; i++) out[fill[circuitOf[i]]++] = i;
        return out;
    }

    private int newRelay(RelayKind k, int cab) {
        countRelaySlot();
        int r = relays++;
        if (r == kind.length) {
            int n = r * 2;
            kind = Arrays.copyOf(kind, n);
            state = Arrays.copyOf(state, n);
            pendingFault = Arrays.copyOf(pendingFault, n);
            opTimer = Arrays.copyOf(opTimer, n);
            coilLoad = Arrays.copyOf(coilLoad, n);
            resetLoad = Arrays.copyOf(resetLoad, n);
            weldedGroup = Arrays.copyOf(weldedGroup, n);
            cabinet = Arrays.copyOf(cabinet, n);
            ratedV = Arrays.copyOf(ratedV, n);
            opsCount = Arrays.copyOf(opsCount, n);
            opsSinceSlow = Arrays.copyOf(opsSinceSlow, n);
            weldArmature = Arrays.copyOf(weldArmature, n);
            tMode = Arrays.copyOf(tMode, n);
            tType = Arrays.copyOf(tType, n);
            tSetS = Arrays.copyOf(tSetS, n);
            tBias = Arrays.copyOf(tBias, n);
            tTarget = Arrays.copyOf(tTarget, n);
            tCount = Arrays.copyOf(tCount, n);
            tOut = Arrays.copyOf(tOut, n);
            tPrev = Arrays.copyOf(tPrev, n);
        }
        kind[r] = k;
        state[r] = (byte) RelayState.DROPPED.ordinal();
        pendingFault[r] = NO_FAULT;
        cabinet[r] = checkCabinet(cab);
        ratedV[r] = NOMINAL_V;
        resetLoad[r] = -1;
        return r;
    }

    private int usedRelaySlots;

    /** Предел {@code relays.max_per_station}: реле, реле времени и искатели (03d §3d.3.8). */
    private void countRelaySlot() {
        if (usedRelaySlots >= cfg.maxRelaysPerStation()) throw new IllegalStateException("relays.max_per_station");
        usedRelaySlots++;
    }

    private int newSwitch(int a, int b, byte type, int owner, int aux, boolean no) {
        checkNode(a);
        checkNode(b);
        int s = switches++;
        if (s == swA.length) {
            int n = s * 2;
            swA = Arrays.copyOf(swA, n);
            swB = Arrays.copyOf(swB, n);
            swOwner = Arrays.copyOf(swOwner, n);
            swAux = Arrays.copyOf(swAux, n);
            swType = Arrays.copyOf(swType, n);
            swNo = Arrays.copyOf(swNo, n);
            swClosed = Arrays.copyOf(swClosed, n);
            swExt = Arrays.copyOf(swExt, n);
        }
        swA[s] = a;
        swB[s] = b;
        swType[s] = type;
        swOwner[s] = owner;
        swAux[s] = aux;
        swNo[s] = no;
        topologyDirty = true;
        return s;
    }

    private int addLoad(int a, int b, double r, byte type, int owner) {
        checkNode(a);
        checkNode(b);
        int l = loads++;
        if (l == ldA.length) {
            int n = l * 2;
            ldA = Arrays.copyOf(ldA, n);
            ldB = Arrays.copyOf(ldB, n);
            ldOwner = Arrays.copyOf(ldOwner, n);
            ldSupply = Arrays.copyOf(ldSupply, n);
            ldType = Arrays.copyOf(ldType, n);
            ldR = Arrays.copyOf(ldR, n);
            ldV = Arrays.copyOf(ldV, n);
            ldOpen = Arrays.copyOf(ldOpen, n);
        }
        ldA[l] = a;
        ldB[l] = b;
        ldR[l] = r;
        ldType[l] = type;
        ldOwner[l] = owner;
        ldSupply[l] = -1;
        topologyDirty = true;
        return l;
    }

    private void checkNode(int n) {
        if (n < 0 || n >= nodes) throw new IllegalArgumentException("node " + n);
    }

    private void checkRelay(int r) {
        checkIndex(r, relays, "relay");
    }

    private static void checkIndex(int i, int size, String what) {
        if (i < 0 || i >= size) throw new IllegalArgumentException(what + " " + i);
    }

    private int checkCabinet(int cab) {
        if (cab < 0) throw new IllegalArgumentException("cabinet");
        if (cab >= cabinetTempK.length) {
            int old = cabinetTempK.length;
            cabinetTempK = Arrays.copyOf(cabinetTempK, cab + 1);
            Arrays.fill(cabinetTempK, old, cab + 1, 298.15);
        }
        return cab;
    }

    private static void checkTimeSetpoint(TimeRelayType type, double s) {
        if (!(s >= type.minS && s <= type.maxS)) throw new IllegalArgumentException("setpoint out of range for " + type);
    }
}
