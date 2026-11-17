package dev.denfry.promptcritical.sim.body;

/**
 * Тело персонажа, которое игрок задаёт перед игрой: пол (для формул физиологии), рост, масса, возраст. Диапазоны —
 * взрослый человек, допущенный к подземным работам, и хитбокс, который проходит в выработку высотой 2 блока.
 *
 * @param sex       пол для формул Надлера и Миффлина–Сан Жеора
 * @param heightCm  рост, см
 * @param massKg    масса тела без снаряжения, кг
 * @param ageYears  возраст, лет
 */
public record BodyProfile(Sex sex, int heightCm, int massKg, int ageYears) {
    public static final int MIN_HEIGHT_CM = 150, MAX_HEIGHT_CM = 200;
    public static final int MIN_MASS_KG = 45, MAX_MASS_KG = 150;
    public static final int MIN_AGE = 18, MAX_AGE = 70;

    /** Опорный человек моделей: под него подобраны {@code canary_uptake_ratio} и масса человека в клети. */
    public static final BodyProfile REFERENCE = new BodyProfile(Sex.MALE, 175, 75, 30);

    public enum Sex { MALE, FEMALE }

    public BodyProfile {
        if (sex == null) throw new IllegalArgumentException("sex");
        if (heightCm < MIN_HEIGHT_CM || heightCm > MAX_HEIGHT_CM) throw new IllegalArgumentException("heightCm: " + heightCm);
        if (massKg < MIN_MASS_KG || massKg > MAX_MASS_KG) throw new IllegalArgumentException("massKg: " + massKg);
        if (ageYears < MIN_AGE || ageYears > MAX_AGE) throw new IllegalArgumentException("ageYears: " + ageYears);
    }

    /** Профиль из непроверенного ввода (пакет клиента, старое сохранение): значения прижимаются к диапазонам. */
    public static BodyProfile clamped(Sex sex, int heightCm, int massKg, int ageYears) {
        return new BodyProfile(sex == null ? Sex.MALE : sex, clamp(heightCm, MIN_HEIGHT_CM, MAX_HEIGHT_CM),
                clamp(massKg, MIN_MASS_KG, MAX_MASS_KG), clamp(ageYears, MIN_AGE, MAX_AGE));
    }

    public double heightM() {
        return heightCm / 100.0;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
