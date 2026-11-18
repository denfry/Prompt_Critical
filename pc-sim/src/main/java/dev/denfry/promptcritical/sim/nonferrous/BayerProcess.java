package dev.denfry.promptcritical.sim.nonferrous;

/**
 * Глинозём по Байеру (08 §8.8.1): выщелачивание боксита щёлочью в автоклаве, отделение красного шлама, декомпозиция
 * алюминатного раствора на затравке и прокалка гидроксида. Здесь — химия и балансы, общие для трёх аппаратов
 * ({@link BayerDigester}, {@link AluminaPrecipitator}, {@link AluminaCalciner}).
 *
 * <p>Раствор описан, как принято в отечественной практике, каустическим модулем αк — мольным отношением
 * Na₂O(каустич.)/Al₂O₃: после выщелачивания {@value #MODULUS_PREGNANT}, после декомпозиции {@value #MODULUS_SPENT}
 * (Лайнер А. И., «Производство глинозёма», 1978: 1,6–1,8 и 3,4–3,6). Щёлочь в растворе — {@value #NAOH_G_L} г/л NaOH
 * (08 §8.8.1: 150–250 г/л). Оборотный раствор между автоклавом и декомпозёром несёт «оборотный» глинозём модуля
 * {@value #MODULUS_SPENT} неявно: в трубах учитывается только приращение, которое раствор снимает с боксита и отдаёт
 * на затравку, — {@link #NET_AL2O3_KG_L} кг/л. Так баланс по Al₂O₃ сходится в установившемся режиме.
 *
 * <p>Боксит жилы {@code bauxite} (08 §8.1.2: Гвинея — гиббситовый, СУБР — бёмит-диаспоровый) — смесь: доля бёмита
 * {@value #BOEHMITE_SHARE} от Al₂O₃ [BALANCE]. Гиббсит растворяется при 140–150 °C, бёмит — при 240–250 °C (§8.8.1).
 * Реактивный кремнезём ({@value #REACTIVE_SIO2} массы боксита [BALANCE]) связывает щёлочь и глинозём в
 * гидроалюмосиликат натрия Na₂O·Al₂O₃·2SiO₂ — это и есть потеря 50–100 кг NaOH на тонну глинозёма (§8.8.1).
 */
public final class BayerProcess {
    public static final double M_AL2O3 = 0.101961, M_NAOH = 0.039997, M_NA2O = 0.061979, M_SIO2 = 0.060084, M_H2O = 0.018015,
            M_ALOH3 = 0.078004;
    /** Щёлочь в оборотном растворе, кг NaOH на литр (200 г/л). */
    public static final double NAOH_G_L = 200, NAOH_KG_L = NAOH_G_L / 1000;
    public static final double MODULUS_PREGNANT = 1.7, MODULUS_SPENT = 3.5;
    /** Окна температур выщелачивания, К (08 §8.8.1). */
    public static final double GIBBSITE_LOW_K = 413.15, GIBBSITE_HIGH_K = 423.15, BOEHMITE_LOW_K = 513.15, BOEHMITE_HIGH_K = 523.15;
    /** Ниже окна растворение затухает линейно на этот интервал, К. */
    public static final double RAMP_K = 40;
    public static final double BOEHMITE_SHARE = 0.5, REACTIVE_SIO2 = 0.025;
    /** Боксит без пробы: Al₂O₃ 50 % (медиана закона жилы, 08 §8.1.2). */
    public static final double DEFAULT_AL2O3 = 0.50;
    /** Гидроалюмосиликат: на кг реактивного SiO₂ уходит NaOH, Al₂O₃ и Na₂O в шлам, кг. */
    public static final double DSP_NAOH_PER_SIO2 = 2 * M_NAOH / (2 * M_SIO2), DSP_AL2O3_PER_SIO2 = M_AL2O3 / (2 * M_SIO2),
            DSP_NA2O_PER_SIO2 = M_NA2O / (2 * M_SIO2);
    /** Кристаллизационная вода: гиббсит Al₂O₃·3H₂O, бёмит Al₂O₃·H₂O — кг воды на кг Al₂O₃. */
    public static final double LOI_GIBBSITE = 3 * M_H2O / M_AL2O3, LOI_BOEHMITE = M_H2O / M_AL2O3;
    /** Al(OH)₃ на кг Al₂O₃. */
    public static final double HYDROXIDE_PER_AL2O3 = 2 * M_ALOH3 / M_AL2O3;

    /** Al₂O₃ в растворе модуля αк, кг/л. */
    public static double al2o3KgL(double modulus) {
        return NAOH_KG_L / M_NAOH / 2 / modulus * M_AL2O3;
    }

    /** Приращение глинозёма на литр оборотного раствора: снимается с боксита и отдаётся на затравку, кг/л. */
    public static final double NET_AL2O3_KG_L = al2o3KgL(MODULUS_PREGNANT) - al2o3KgL(MODULUS_SPENT);

    private BayerProcess() {
    }

    /** Доля от 0 до 1: ниже {@code low − RAMP_K} — ноль, от {@code low} — полностью. */
    static double ramp(double tempK, double lowK) {
        return Math.clamp((tempK - (lowK - RAMP_K)) / RAMP_K, 0, 1);
    }

    /** Доля Al₂O₃ боксита, которая переходит в раствор при температуре автоклава (до потерь в шлам). */
    public static double extraction(double tempK) {
        return (1 - BOEHMITE_SHARE) * ramp(tempK, GIBBSITE_LOW_K) + BOEHMITE_SHARE * ramp(tempK, BOEHMITE_LOW_K);
    }

    /** Глинозём в раствор с кг боксита содержания {@code al2o3}, за вычетом связанного кремнезёмом, кг. */
    public static double netAl2o3PerKg(double al2o3, double tempK) {
        return Math.max(0, al2o3 * extraction(tempK) - REACTIVE_SIO2 * DSP_AL2O3_PER_SIO2);
    }

    /** NaOH, связанный в шлам, на кг боксита, кг. */
    public static double naohLossPerKg() {
        return REACTIVE_SIO2 * DSP_NAOH_PER_SIO2;
    }

    /** Сухой красный шлам на кг боксита: порода, нерастворённый глинозём и Na₂O гидроалюмосиликата, кг. */
    public static double redMudPerKg(double al2o3, double tempK) {
        double loi = al2o3 * ((1 - BOEHMITE_SHARE) * LOI_GIBBSITE + BOEHMITE_SHARE * LOI_BOEHMITE);
        double inert = Math.max(0, 1 - al2o3 - loi);
        return inert + (al2o3 - netAl2o3PerKg(al2o3, tempK)) + REACTIVE_SIO2 * DSP_NA2O_PER_SIO2;
    }

    /** Боксит на тонну глинозёма, т; бесконечность — при этой температуре ничего не растворяется. */
    public static double bauxitePerAlumina(double al2o3, double tempK) {
        double n = netAl2o3PerKg(al2o3, tempK);
        return n > 0 ? 1 / n : Double.POSITIVE_INFINITY;
    }
}
