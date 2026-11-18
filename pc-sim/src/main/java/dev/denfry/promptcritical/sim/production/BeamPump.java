package dev.denfry.promptcritical.sim.production;

/**
 * Штанговая глубинная насосная установка (ШГН) со станком-качалкой обычной (кривошипно-коромысловой) геометрии.
 *
 * <p><b>Кинематика.</b> Опора балансира — в начале координат; кривошип радиуса R вращается вокруг вала редуктора в
 * точке (−I, −H); шатун длины P соединяет палец кривошипа с задним плечом балансира C; полированный шток висит на
 * головке переднего плеча A. Угол балансира ψ для угла кривошипа θ — из условия |B(ψ) − палец(θ)| = P (четырёхзвенник;
 * API Spec 11E, Annex «Geometry of pumping units»; Gibbs, «Rod Pumping», гл. 4). Ход полированного штока — A·Δsin ψ
 * (≈ 2R·A/C), скорость и ускорение — численным дифференцированием по θ при угловой скорости ω = 2π·n/60.
 *
 * <p><b>Нагрузки и динамограмма</b> (Брэдли, «Petroleum Engineering Handbook», гл. 10; API RP 11L): вес штанг в
 * жидкости W_rf = W_r(1 − ρ_ж/ρ_ст), нагрузка от столба жидкости на плунжер F_o = A_п(p_нагн − p_прием). В начале хода
 * вверх штанги растягиваются на δ = F_oL/(EA_ш), и нагрузка нарастает линейно (приём нагрузки), на ходу вниз так же
 * снимается; инерция колонны — m_r·a. При неполном заполнении насоса (откачка до уровня) снятие нагрузки на ходу вниз
 * запаздывает до встречи плунжера с жидкостью — «удар о жидкость» на карте. Ход плунжера S_п = S − δ, подача
 * q = A_п·S_п·n/60·η_об (η_об = 0,85), механика — без волнового уравнения Гиббса (динамические составляющие сверх
 * инерции не учитываются).
 */
public final class BeamPump {
    public static final double G = 9.806_65, STEEL_E = 2.05e11, STEEL_KGM3 = 7_850;
    /** Геометрия станка: передние/задние плечи, радиус кривошипа, шатун, положение вала редуктора, м. */
    public static final double A = 3.0, C = 2.5, R = 0.8, P = 3.0, I = 2.5, H = 2.9;
    /** Штанги 3/4″ (сечение 2,85 см², 2,43 кг/м с муфтами), плунжер 1¾″. */
    public static final double ROD_AREA_M2 = 2.85e-4, ROD_KG_PER_M = 2.43, PLUNGER_AREA_M2 = Math.PI / 4 * Math.pow(1.75 * 0.0254, 2);
    public static final double VOLUMETRIC_EFFICIENCY = 0.85, SYSTEM_EFFICIENCY = 0.85;
    public static final int CARD_POINTS = 24;
    private static final int SAMPLES = 360;

    private static final double[] POS = new double[SAMPLES];
    private static final double PSI_MIN, STROKE_M;

    static {
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        double[] psi = new double[SAMPLES];
        for (int i = 0; i < SAMPLES; i++) {
            psi[i] = beamAngle(2 * Math.PI * i / SAMPLES);
            min = Math.min(min, psi[i]);
            max = Math.max(max, psi[i]);
        }
        PSI_MIN = min;
        for (int i = 0; i < SAMPLES; i++) POS[i] = A * (Math.sin(psi[i]) - Math.sin(min));
        STROKE_M = A * (Math.sin(max) - Math.sin(min));
    }

    private BeamPump() {
    }

    /** Угол балансира, рад, при угле кривошипа θ: корень условия длины шатуна на ветви около горизонтали. */
    static double beamAngle(double theta) {
        double px = -I + R * Math.cos(theta), py = -H + R * Math.sin(theta);
        double lo = -0.7, hi = 0.7;
        java.util.function.DoubleUnaryOperator f = psi -> {
            double bx = -C * Math.cos(psi), by = -C * Math.sin(psi);
            return Math.hypot(bx - px, by - py) - P;
        };
        // заднее плечо опускается — шатун укорачивается: f убывает по ψ на рабочей ветви
        double flo = f.applyAsDouble(lo);
        for (int i = 0; i < 70; i++) {
            double mid = 0.5 * (lo + hi), fm = f.applyAsDouble(mid);
            if (Math.signum(fm) == Math.signum(flo)) {
                lo = mid;
                flo = fm;
            } else {
                hi = mid;
            }
        }
        return 0.5 * (lo + hi);
    }

    /** Ход полированного штока, м. */
    public static double strokeM() {
        return STROKE_M;
    }

    /** Положение полированного штока над нижней мёртвой точкой при угле кривошипа, м. */
    public static double position(double theta) {
        double t = ((theta % (2 * Math.PI)) + 2 * Math.PI) % (2 * Math.PI);
        double k = t / (2 * Math.PI) * SAMPLES;
        int i0 = (int) Math.floor(k) % SAMPLES, i1 = (i0 + 1) % SAMPLES;
        double w = k - Math.floor(k);
        return POS[i0] * (1 - w) + POS[i1] * w;
    }

    /** Ускорение полированного штока, м/с², при n ходов в минуту. */
    public static double acceleration(double theta, double spm) {
        double h = 2 * Math.PI / SAMPLES, w = spm * 2 * Math.PI / 60;
        return (position(theta + h) - 2 * position(theta) + position(theta - h)) / (h * h) * w * w;
    }

    public static double velocity(double theta, double spm) {
        double h = 2 * Math.PI / SAMPLES, w = spm * 2 * Math.PI / 60;
        return (position(theta + h) - position(theta - h)) / (2 * h) * w;
    }

    /** Растяжение штанг от нагрузки жидкости, м. */
    public static double rodStretchM(double fluidLoadN, double rodLengthM) {
        return fluidLoadN * rodLengthM / (STEEL_E * ROD_AREA_M2);
    }

    /** Подача насоса при n ходов в минуту и ходе плунжера, м³/с. */
    public static double displacementM3s(double spm, double plungerStrokeM) {
        return PLUNGER_AREA_M2 * Math.max(0, plungerStrokeM) * spm / 60 * VOLUMETRIC_EFFICIENCY;
    }

    /**
     * Динамограмма за цикл: {@link #CARD_POINTS} точек по углу кривошипа.
     *
     * @param spm       ходов в минуту
     * @param rodLengthM глубина подвески насоса, м
     * @param liquidKgM3 плотность откачиваемой жидкости
     * @param fluidLoadN нагрузка столба жидкости на плунжер, Н
     * @param fillage   заполнение насоса, доли
     */
    public static Card card(double spm, double rodLengthM, double liquidKgM3, double fluidLoadN, double fillage) {
        double wr = ROD_KG_PER_M * rodLengthM * G, wrf = wr * (1 - liquidKgM3 / STEEL_KGM3), mass = ROD_KG_PER_M * rodLengthM;
        double delta = Math.min(rodStretchM(fluidLoadN, rodLengthM), 0.95 * STROKE_M);
        double fill = Math.clamp(fillage, 0, 1);
        double[] x = new double[CARD_POINTS], f = new double[CARD_POINTS];
        // НМТ: самая низкая точка; от неё — ход вверх
        int bottom = 0;
        for (int i = 1; i < SAMPLES; i++) if (POS[i] < POS[bottom]) bottom = i;
        double power = 0, peak = 0, min = Double.MAX_VALUE;
        for (int k = 0; k < SAMPLES; k++) {
            int i = (bottom + k) % SAMPLES;
            double theta = 2 * Math.PI * i / SAMPLES;
            double pos = POS[i];
            boolean up = velocity(theta, Math.max(spm, 1)) >= 0;
            double fluid;
            if (up) {
                fluid = fluidLoadN * Math.min(1, pos / Math.max(delta, 1e-6));
            } else {
                // ход вниз: нагрузка снимается, когда плунжер дошёл до жидкости в цилиндре
                double fromTop = STROKE_M - pos;
                double gap = (1 - fill) * (STROKE_M - delta);
                fluid = fromTop < gap ? fluidLoadN : fluidLoadN * Math.max(0, 1 - (fromTop - gap) / Math.max(delta, 1e-6));
            }
            double load = wrf + fluid + mass * acceleration(theta, spm);
            power += load * velocity(theta, spm);
            peak = Math.max(peak, load);
            min = Math.min(min, load);
            if (k * CARD_POINTS % SAMPLES == 0) {
                int j = k * CARD_POINTS / SAMPLES;
                x[j] = pos;
                f[j] = load;
            }
        }
        return new Card(x, f, peak, min, Math.max(0, power / SAMPLES) / SYSTEM_EFFICIENCY, STROKE_M - delta);
    }

    /**
     * Динамограмма: положение, м, и нагрузка, Н, по точкам цикла; наибольшая и наименьшая нагрузка, средняя мощность
     * на валу редуктора, Вт, и ход плунжера, м.
     */
    public record Card(double[] positionM, double[] loadN, double peakN, double minN, double shaftPowerW, double plungerStrokeM) {
    }
}
