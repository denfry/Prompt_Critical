package dev.denfry.promptcritical.sim.radiation;

/**
 * Детерминированные эффекты острого облучения всего тела (Гуськова А. К., Байсоголов Г. Д., «Лучевая болезнь
 * человека», 1971; IAEA/WHO «Diagnosis and Treatment of Radiation Injuries», 1998; ICRP 103). Стохастические эффекты
 * (рак) не моделируются. Острая доза — накопленная за последние часы: организм восстанавливается, у модели — с
 * периодом {@value #RECOVERY_HALF_DAYS} суток.
 */
public final class RadiationSickness {
    public static final double RECOVERY_HALF_DAYS = 30;

    public enum Stage {
        /** До 0,25 Гр — без клинических проявлений. */
        NONE(0),
        /** 0,25–1 Гр: изменения крови, без болезни. */
        BLOOD_CHANGES(0.25),
        /** ОЛБ I степени, 1–2 Гр: тошнота, слабость. */
        MILD(1),
        /** II степень, 2–4 Гр: рвота в первые часы, поражение костного мозга. */
        MODERATE(2),
        /** III степень, 4–6 Гр: LD50/60 без лечения ≈ 4 Гр. */
        SEVERE(4),
        /** IV степень, свыше 6 Гр: без пересадки костного мозга смертельно. */
        EXTREMELY_SEVERE(6);

        public final double fromGy;

        Stage(double fromGy) {
            this.fromGy = fromGy;
        }
    }

    private RadiationSickness() {
    }

    public static Stage stage(double acuteGy) {
        Stage s = Stage.NONE;
        for (Stage st : Stage.values()) if (acuteGy >= st.fromGy) s = st;
        return s;
    }

    /** Остаток острой дозы через {@code days} суток восстановления. */
    public static double recover(double acuteGy, double days) {
        return acuteGy * Math.pow(2, -days / RECOVERY_HALF_DAYS);
    }

    /** Вероятность гибели без лечения (LD50/60 = 4 Гр, крутизна ≈ 0,7 Гр — кривая Lushbaugh). */
    public static double lethality(double acuteGy) {
        if (acuteGy <= 1) return 0;
        double z = (acuteGy - 4) / 0.7;
        return 1 / (1 + Math.exp(-1.7 * z));
    }
}
