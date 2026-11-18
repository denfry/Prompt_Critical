package dev.denfry.promptcritical.sim.reactor;

/**
 * Достижение {@code load_follow} (11 §11.2.2): реактор сутки следует графику диспетчера (суточный график нагрузки
 * 07 §7.6.4) с отклонением меньше 5 %. План — форма графика $\hat p(h)$, приведённая к мощности реактора в момент
 * привязки: $n_{plan} = s\,\hat p(h)$, $s = n/\hat p$ при первом шаге. Мощность сглажена (τ = 5 мин физ.), чтобы шум
 * регулятора не сбрасывал счёт; выход за 5 % заново привязывает план и обнуляет время. Реактор на постоянной мощности
 * сутки не продержится: график ходит от 0,6 до 1.
 */
public final class LoadFollowWatch {
    public static final double WINDOW_S = 86_400, TOLERANCE = 0.05;
    /** Сглаживание мощности, с физ. [BALANCE] — порядок записи мощности в суточной ведомости. */
    public static final double SMOOTH_TAU_S = 300;
    /** Ниже этой мощности реактор графику не следует (останов, разогрев). */
    public static final double MIN_POWER = 0.2;

    private double followS, scale = Double.NaN, smooth = Double.NaN;
    private boolean reported;

    /**
     * @param dtPhysS физический шаг, с
     * @param shape   форма графика $\hat p(h)$ сейчас; NaN — графика нет
     * @param power   мощность, доли номинала
     * @param tripped реактор в АЗ
     * @return {@code true} в шаге, когда набраны сутки следования
     */
    public boolean update(double dtPhysS, double shape, double power, boolean tripped) {
        if (Double.isNaN(shape) || shape <= 0 || tripped || power < MIN_POWER) {
            followS = 0;
            scale = smooth = Double.NaN;
            return false;
        }
        smooth = Double.isNaN(smooth) ? power : smooth + (power - smooth) * (1 - Math.exp(-dtPhysS / SMOOTH_TAU_S));
        if (Double.isNaN(scale)) scale = smooth / shape;
        double plan = scale * shape;
        if (Math.abs(smooth - plan) > TOLERANCE * plan) {
            followS = 0;
            scale = smooth / shape;
            return false;
        }
        followS += dtPhysS;
        if (!reported && followS >= WINDOW_S) {
            reported = true;
            return true;
        }
        return false;
    }

    /** Время непрерывного следования графику, ч. */
    public double hours() {
        return followS / 3600;
    }
}
