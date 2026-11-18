package dev.denfry.promptcritical.sim.gasprocessing;

import dev.denfry.promptcritical.sim.refining.CompressorMap;

/**
 * Центробежный компрессор с рециклом и помпажом (09 §9.8.5, карта и линия помпажа 03d §3d.5.12). Динамика — модель
 * Грейтцера (Greitzer, 1976; Moore–Greitzer, 1986) в сосредоточенных параметрах: инерция газа в проточной части и ёмкость
 * нагнетательного коллектора.
 *
 * <pre>
 * I·dQ/dt = p_s·(1 + ψ_c(Q, n)) − p_d          (разгон столба газа в проточной части)
 * V/p_s·dp_d/dt = Q − Q_line − Q_rec            (изотермический коллектор, расходы — в объёме всаса)
 * ψ_c = n²·[ψ₀ + H·(1 + 1,5x − 0,5x³)],  x = Q/(n·W) − 1   (кубическая характеристика Мура–Грейтцера)
 * </pre>
 *
 * Пик характеристики (x = 1) — граница устойчивости: левее него наклон положительный, и при большой ёмкости коллектора
 * (B ≈ 1,7) режим срывается в <b>глубокий помпаж</b> — предельный цикл с реверсом потока и периодом ≈ 1,3 с (в
 * диапазоне 0,5–2 с 09 §9.8.5). Огибающая пиков по частотам — линия помпажа: Q_s = 2Wn, R_c − 1 = n²(ψ₀ + 2H), то
 * есть в приведённых координатах прямая q²_surge = {@link #SURGE_KS}·(R_c − 1).
 *
 * <p>Расходомер — диафрагма на всасе: Δp_o = {@value #DP_DESIGN_KPA} кПа при расчётном расходе; при реверсе
 * дифманометр показывает 0. Нагнетание — в газопровод с давлением {@value #P_LINE_BAR} бар через обратный и линейный
 * клапаны; рецикл (антипомпажный клапан) сбрасывает газ из коллектора на всас, ход клапанов — 2 с (09 §9.13.4
 * {@code setRecycle}). Газ — природный, как в примере 09 §9.8.5 (M = 17,4, k = 1,3, Z₁ = 0,93, η_p = 0,8).
 *
 * <p>Последствия помпажа (09 §9.8.5): реверс потока и удары давления в коллекторе (сама модель), вибрация ротора
 * {@value #VIB_SURGE_MM_S} мм/с поверх нормальной (зона D ISO 10816), износ подшипников и уплотнений и при длительном
 * помпаже — разрушение ({@link #damage()} = 1 за {@value #SURGE_LIFE_S} с помпажа в сумме). Цикл помпажа считается
 * по каждому реверсу потока на вращающемся роторе. Числа без ссылки — [BALANCE] под масштаб мода (≈ 0,4 МВт, как
 * поршневой {@link GasCompressor}).
 */
public final class CentrifugalCompressor implements CompressorMap {
    /** Давление всаса и газопровода, Па; температура всаса, К. */
    public static final double P_SUCTION = 5e5, P_LINE_BAR = 8, P_LINE = P_LINE_BAR * 1e5, T_SUCTION = 300;
    /** Характеристика: напор при нулевом расходе ψ₀, полуамплитуда кубики H, полуширина W (м³/с при n = 1). */
    public static final double PSI0 = 0.3, H = 0.4, W = 0.37;
    /** Инерционность проточной части I, Па·с²/м³; объём коллектора, м³. */
    public static final double INERTANCE = 1e4, VOLUME_M3 = 1.5;
    /** Пропускная способность линейного клапана и рецикла: м³/с всаса на √бар перепада при полном открытии. */
    public static final double C_LINE = 1.4, C_RECYCLE = 0.8;
    /** Расчётный расход всаса, м³/с, и перепад на диафрагме при нём, кПа. */
    public static final double Q_DESIGN = 1.0, DP_DESIGN_KPA = 25;
    /** Линия помпажа q²_surge = KS·(R_c − 1) в единицах кПа/бар (Δp_o в кПа, p_s в бар). */
    public static final double SURGE_KS = DP_DESIGN_KPA / (P_SUCTION / 1e5) * 4 * W * W / (Q_DESIGN * Q_DESIGN) / (PSI0 + 2 * H);
    /** Газ 09 §9.8.5: молярная масса, кг/моль; Z₁; k; политропный КПД. */
    public static final double M = 0.0174, Z1 = 0.93, K = 1.3, ETA_P = 0.8;
    public static final double SIGMA = (K - 1) / (K * ETA_P);
    /** Плотность газа на всасе, кг/м³. */
    public static final double RHO_S = P_SUCTION * M / (Z1 * 8.314462618 * T_SUCTION);
    /** Полный ход клапанов, с. */
    public static final double VALVE_STROKE_S = 2;
    /** Вибрация: нормальная при n = 1 и добавка помпажа, мм/с (ISO 10816-3: зона D > 11,2). */
    public static final double VIB_BASE_MM_S = 1.8, VIB_SURGE_MM_S = 14;
    /** Помпаж «идёт», пока с последнего реверса прошло меньше, с; суммарный помпаж до разрушения, с. */
    public static final double SURGE_HOLD_S = 3, SURGE_LIFE_S = 120;
    private static final int SUBSTEPS = 20;

    private double n, q, pd = P_SUCTION, recycle = 1, line;
    private double qLine, qRecycle, sinceReversal = Double.POSITIVE_INFINITY, intensity, damage, surgeSeconds;
    private long cycles;
    private int cyclesThisStep;
    private boolean forward = true;

    /** Компрессор стоит, рецикл открыт, линейный клапан закрыт, коллектор под давлением всаса. */
    public CentrifugalCompressor() {
    }

    @Override
    public double surgeQ2(double pressureRatio) {
        return SURGE_KS * Math.max(0, pressureRatio - 1);
    }

    /** Безразмерный напор характеристики ψ_c = R_c − 1 при расходе {@code qM3s} и относительной частоте {@code speed}. */
    public static double headCoefficient(double qM3s, double speed) {
        if (speed <= 1e-6) return -50 * qM3s * Math.abs(qM3s); // стоящее колесо — только сопротивление
        double x = qM3s / (speed * W) - 1;
        return speed * speed * (PSI0 + H * (1 + 1.5 * x - 0.5 * x * x * x));
    }

    /** Расход на линии помпажа при частоте {@code speed}, м³/с всаса. */
    public static double surgeFlow(double speed) {
        return 2 * W * Math.max(0, speed);
    }

    /**
     * Политропный напор H_p = Z₁RT₁/σ·((p₂/p₁)^σ − 1), Дж/кг, σ = (k − 1)/(k·η_p) — 09 §9.8.5.
     *
     * @param molarMass кг/моль
     */
    public static double polytropicHead(double z1, double molarMass, double t1, double k, double etaP, double ratio) {
        double sigma = (k - 1) / (k * etaP);
        return z1 * 8.314462618 / molarMass * t1 / sigma * (Math.pow(ratio, sigma) - 1);
    }

    /** Температура нагнетания T₂ = T₁·(p₂/p₁)^σ, К — 09 §9.8.5. */
    public static double dischargeTemp(double t1, double k, double etaP, double ratio) {
        return t1 * Math.pow(ratio, (k - 1) / (k * etaP));
    }

    /**
     * Шаг {@code dt}: частота ротора {@code speed} (доля номинала; разгон задаёт владелец — вал Create или привод
     * двойника), команды рециклу и линейному клапану 0..1 (клапаны идут к ним с ходом {@value #VALVE_STROKE_S} с).
     */
    public void step(double dt, double speed, double recycleCmd, double lineCmd) {
        if (!(dt > 0)) return;
        n = Math.max(0, speed);
        double rate = dt / VALVE_STROKE_S;
        recycle += Math.clamp(clamp01(recycleCmd) - recycle, -rate, rate);
        line += Math.clamp(clamp01(lineCmd) - line, -rate, rate);
        cyclesThisStep = 0;
        double h = dt / SUBSTEPS;
        for (int i = 0; i < SUBSTEPS; i++) {
            qLine = line * C_LINE * Math.sqrt(Math.max(0, pd - P_LINE) / 1e5);
            double dpr = pd - P_SUCTION;
            qRecycle = recycle * C_RECYCLE * Math.signum(dpr) * Math.sqrt(Math.abs(dpr) / 1e5);
            double dq = (P_SUCTION * (1 + headCoefficient(q, n)) - pd) / INERTANCE;
            double dp = P_SUCTION / VOLUME_M3 * (q - qLine - qRecycle);
            q += dq * h;
            pd = Math.max(0.2 * P_SUCTION, pd + dp * h);
            boolean fwd = q > 0;
            if (forward && !fwd && n > 0.05) {
                cycles++;
                cyclesThisStep++;
                sinceReversal = 0;
                intensity = 1;
            }
            forward = fwd;
        }
        sinceReversal += dt;
        intensity *= Math.exp(-dt / 1.0);
        if (surging()) {
            surgeSeconds += dt;
            damage = Math.min(1, damage + dt / SURGE_LIFE_S);
        }
    }

    private static double clamp01(double v) {
        return Double.isNaN(v) ? 0 : Math.clamp(v, 0, 1);
    }

    // ------------------------------------------------------------------ показания

    public double speed() {
        return n;
    }

    /** Расход через компрессор, м³/с всаса; отрицательный — реверс (помпаж). */
    public double flowM3s() {
        return q;
    }

    public double suctionPa() {
        return P_SUCTION;
    }

    public double dischargePa() {
        return pd;
    }

    public double pressureRatio() {
        return pd / P_SUCTION;
    }

    /** Перепад на диафрагме всаса, кПа; при реверсе — 0 (дифманометр в упоре). */
    public double orificeDpKpa() {
        return q > 0 ? DP_DESIGN_KPA * q * q / (Q_DESIGN * Q_DESIGN) : 0;
    }

    /** Приведённый расход q²_r = Δp_o/p_s, кПа/бар. */
    public double q2r() {
        return orificeDpKpa() / (P_SUCTION / 1e5);
    }

    /** Запас S_s = q²_surge(R_c)/q²_r: 1 — на линии помпажа, больше 1 — за ней; без расхода — 0 у стоящего. */
    public double surgeRatio() {
        double q2 = q2r(), s = surgeQ2(pressureRatio());
        if (q2 > 1e-9) return s / q2;
        return s > 0 ? 10 : 0;
    }

    /** Расход в газопровод и на рецикл, м³/с всаса. */
    public double lineFlowM3s() {
        return qLine;
    }

    public double recycleFlowM3s() {
        return Math.max(0, qRecycle);
    }

    public double recyclePosition() {
        return recycle;
    }

    public double linePosition() {
        return line;
    }

    /** Циклов помпажа с пуска модели (реверсов потока на вращающемся роторе). */
    public long surgeCycles() {
        return cycles;
    }

    /** Циклов помпажа за последний шаг. */
    public int surgeCyclesThisStep() {
        return cyclesThisStep;
    }

    /** Помпаж идёт: реверс был меньше {@value #SURGE_HOLD_S} с назад. */
    public boolean surging() {
        return sinceReversal < SURGE_HOLD_S;
    }

    /** Интенсивность помпажа 0..1 (09 §9.13.4 {@code pc_compressor_surge.intensity}): спадает за 1 с после реверса. */
    public double surgeIntensity() {
        return intensity;
    }

    public double surgeSeconds() {
        return surgeSeconds;
    }

    /** СКЗ виброскорости ротора, мм/с. */
    public double vibrationMmS() {
        return VIB_BASE_MM_S * n + VIB_SURGE_MM_S * intensity;
    }

    /** Повреждение проточной части и подшипников от помпажа 0..1; 1 — разрушение. */
    public double damage() {
        return damage;
    }

    public boolean destroyed() {
        return damage >= 1;
    }

    /** Мощность на валу, Вт: ρ_s·Q·H_p/η_p при прямом потоке. */
    public double powerW() {
        if (q <= 0 || n <= 0) return 0;
        return RHO_S * q * polytropicHead(Z1, M, T_SUCTION, K, ETA_P, Math.max(1, pressureRatio())) / ETA_P;
    }

    public double dischargeK() {
        return dischargeTemp(T_SUCTION, K, ETA_P, Math.max(1, pressureRatio()));
    }

    // ------------------------------------------------------------------ сохранение

    /** Состояние для сохранения блока. */
    public double[] state() {
        return new double[] {n, q, pd, recycle, line, Math.min(sinceReversal, 1e9), intensity, damage, surgeSeconds, cycles,
                forward ? 1 : 0};
    }

    public void restore(double[] s) {
        if (s == null || s.length < 11) return;
        n = s[0];
        q = s[1];
        pd = s[2] > 0 ? s[2] : P_SUCTION;
        recycle = clamp01(s[3]);
        line = clamp01(s[4]);
        sinceReversal = s[5];
        intensity = s[6];
        damage = Math.clamp(s[7], 0, 1);
        surgeSeconds = s[8];
        cycles = (long) s[9];
        forward = s[10] != 0;
    }

    /** Новый компрессор после ремонта: повреждение и счётчик сброшены, режим сохранён. */
    public void repair() {
        damage = 0;
        surgeSeconds = 0;
    }
}
