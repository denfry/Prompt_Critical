package dev.denfry.promptcritical.sim.body;

/**
 * Физиология персонажа по {@link BodyProfile}: всё, чем тело влияет на игру.
 *
 * <ul>
 *   <li>Объём крови — Nadler, Hidalgo, Bloch (1962): V = a·H³ + b·W + c, H в м, W в кг, V в л.</li>
 *   <li>Основной обмен — Mifflin, St Jeor et al. (1990): 10·W + 6,25·H(см) − 5·A + 5 (муж.) / − 161 (жен.),
 *   ккал/сут.</li>
 *   <li>Угарный газ — модель Coburn–Forster–Kane: COHb идёт к равновесию с темпом V̇_A/V_b, а равновесие от
 *   вентиляции не зависит. Вентиляция пропорциональна потреблению O₂, то есть основному обмену × MET нагрузки
 *   (Compendium of Physical Activities, Ainsworth 2011). Отсюда «физиологические часы» отравления
 *   {@link #gasClock}: во сколько раз быстрее опорного человека в покое тело набирает и выводит газ.</li>
 *   <li>Площадь тела — Du Bois (1916): 0,007184·W^0,425·H(см)^0,725, м².</li>
 * </ul>
 */
public final class Physiology {
    /** Рост модели игрока по умолчанию в блоках = метрах: хитбокс 1,8. */
    public static final double DEFAULT_MODEL_HEIGHT_M = 1.8;
    /** Снаряжение горняка поверх массы тела: спецодежда, сапоги, каска, аккумулятор лампы, самоспасатель, кг. */
    public static final double MINER_GEAR_KG = 10;
    private static final double KCAL_PER_DAY_W = 4184.0 / 86_400;

    /** Нагрузка в MET (Ainsworth 2011): стоя, ходьба, бег, плавание, подъём по лестнице. */
    public enum Activity {
        REST(1.3), WALK(3.5), SPRINT(8.0), SWIM(6.0), CLIMB(8.0);

        public final double met;

        Activity(double met) {
            this.met = met;
        }
    }

    private static final double REFERENCE_CLOCK = metabolicFlow(BodyProfile.REFERENCE, Activity.REST);

    private Physiology() {
    }

    /** Объём крови по Надлеру, л. */
    public static double bloodVolumeL(BodyProfile p) {
        double h3 = Math.pow(p.heightM(), 3);
        return p.sex() == BodyProfile.Sex.MALE
                ? 0.3669 * h3 + 0.03219 * p.massKg() + 0.6041
                : 0.3561 * h3 + 0.03308 * p.massKg() + 0.1833;
    }

    /** Основной обмен по Миффлину–Сан Жеору, Вт. */
    public static double basalMetabolicW(BodyProfile p) {
        double kcal = 10.0 * p.massKg() + 6.25 * p.heightCm() - 5.0 * p.ageYears()
                + (p.sex() == BodyProfile.Sex.MALE ? 5 : -161);
        return kcal * KCAL_PER_DAY_W;
    }

    /** Индекс массы тела, кг/м². */
    public static double bodyMassIndex(BodyProfile p) {
        return p.massKg() / (p.heightM() * p.heightM());
    }

    /** Площадь поверхности тела по Дюбуа, м². */
    public static double bodySurfaceM2(BodyProfile p) {
        return 0.007184 * Math.pow(p.massKg(), 0.425) * Math.pow(p.heightCm(), 0.725);
    }

    /**
     * Во сколько раз быстрее опорного человека в покое ({@link BodyProfile#REFERENCE}) тело при нагрузке
     * {@code a} набирает и выводит CO и чувствует нехватку O₂: (обмен × MET / V_крови) относительно опорного.
     * Время модели {@code GasExposure} идёт с этим множителем — равновесное отравление то же, путь к нему короче.
     */
    public static double gasClock(BodyProfile p, Activity a) {
        return metabolicFlow(p, a) / REFERENCE_CLOCK;
    }

    /** Масштаб модели игрока: рост / 1,8 м. Хитбокс 200 см — 2,0 блока, ещё проходит в выработку высотой 2. */
    public static double modelScale(BodyProfile p) {
        return p.heightM() / DEFAULT_MODEL_HEIGHT_M;
    }

    /**
     * Скорость шага относительно опорного роста: при том же числе Фруда скорость ходьбы ∝ √(длины ноги), а нога ∝
     * росту (Alexander 1984).
     */
    public static double strideSpeedFactor(BodyProfile p) {
        return Math.sqrt(p.heightCm() / (double) BodyProfile.REFERENCE.heightCm());
    }

    /**
     * Сопротивление отбрасыванию: удар и взрывная волна передают импульс, Δv = J/m. Относительно опорных 75 кг
     * тяжёлый получает долю 75/m скорости; лёгкий — как опорный (атрибут не бывает отрицательным) [допущение].
     */
    public static double knockbackResistance(BodyProfile p) {
        return Math.max(0, Math.min(0.6, 1 - BodyProfile.REFERENCE.massKg() / (double) p.massKg()));
    }

    /** Масса человека в клети подъёма со снаряжением, кг. */
    public static double loadedMassKg(BodyProfile p) {
        return p.massKg() + MINER_GEAR_KG;
    }

    private static double metabolicFlow(BodyProfile p, Activity a) {
        return basalMetabolicW(p) * a.met / bloodVolumeL(p);
    }
}
