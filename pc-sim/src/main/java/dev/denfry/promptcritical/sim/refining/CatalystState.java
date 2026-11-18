package dev.denfry.promptcritical.sim.refining;

/**
 * Состояние катализатора — компонент {@code catalyst_state} предметов {@code catalyst_*} (GDD 09 §9.9.11, общий для 09,
 * 10 и 02). Итоговая активность a = a_c·a_S·a_perm: a_c — от кокса, a_S — обратимое отравление серой, a_perm —
 * необратимые яды (азот, металлы) и спекание.
 *
 * @param activityCoke  a_c, 0…1
 * @param activitySulfur a_S, 0…1
 * @param activityPerm  a_perm, 0…1
 * @param cokeFrac      кокс на катализаторе, мас. доля
 * @param poisonS       накопленная сера (обратимо), мас. доля
 * @param poisonN       накопленный азот (необратимо), мас. доля
 * @param poisonMetals  никель и ванадий, мас. доля
 * @param sinterLoss    необратимая потеря активности от перегрева, доля
 * @param ageH          время в работе, ч физ.
 * @param state         стадия обращения
 */
public record CatalystState(double activityCoke, double activitySulfur, double activityPerm, double cokeFrac, double poisonS,
        double poisonN, double poisonMetals, double sinterLoss, double ageH, Stage state) {
    /** FRESH, OXIDE (до сульфидирования или восстановления), ACTIVE, SPENT, REGENERATING (§9.9.11). */
    public enum Stage { FRESH, OXIDE, ACTIVE, SPENT, REGENERATING }

    /** Отработанным катализатор считается ниже этой активности [BALANCE: порог регенерации риформинга 09]. */
    public static final double SPENT_BELOW = 0.5;

    public static final CatalystState FRESH = new CatalystState(1, 1, 1, 0, 0, 0, 0, 0, 0, Stage.FRESH);

    public CatalystState {
        activityCoke = Math.clamp(activityCoke, 0, 1);
        activitySulfur = Math.clamp(activitySulfur, 0, 1);
        activityPerm = Math.clamp(activityPerm, 0, 1);
    }

    /** Итоговая активность a = a_c·a_S·a_perm. */
    public double activity() {
        return activityCoke * activitySulfur * activityPerm;
    }

    /**
     * Равновесный катализатор каткрекинга, выведенный из регенератора: кокс на нём выгорел не весь, металлы и
     * гидротермальная дезактивация — в необратимой части.
     */
    public static CatalystState fccEquilibrium(double activity, double cokeFrac, double ageH) {
        double aCoke = Math.exp(-cokeFrac / CatalyticCracking.CRC_REF);
        return new CatalystState(aCoke, 1, activity, cokeFrac, 0, 0, 0, 0, ageH, activity * aCoke < SPENT_BELOW ? Stage.SPENT : Stage.ACTIVE);
    }
}
