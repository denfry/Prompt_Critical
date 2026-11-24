package dev.denfry.promptcritical.sim.radiation;

/**
 * Газоразрядный счётчик (04b §4b.7.1): трубка класса СБМ-20 без коррекции мёртвого времени. Истинная скорость счёта
 * n = S·Ḣ + n₀; наблюдаемая по продлевающейся модели мёртвого времени m = n·e^{−nτ}. Максимум m = 1/(e·τ) при n = 1/τ,
 * дальше счёт падает: в сильном поле дешёвый прибор врёт вниз и при 1 Зв/ч показывает почти ноль (отчасти так
 * появились «3,6 рентгена» в 1986 г.). Профессиональный прибор ({@code dosimeter_radiometer}) поправляет счёт по
 * непродлевающейся модели и выше {@value #CHAMBER_SWITCH_CPS} имп/с переходит на ионизационную камеру.
 *
 * @param cpsPerUSvH чувствительность S, имп/с на мкЗв/ч
 * @param backgroundCps собственный фон трубки n₀, имп/с
 * @param deadTimeS мёртвое время τ, с
 */
public record GeigerCounter(double cpsPerUSvH, double backgroundCps, double deadTimeS) {
    /** СБМ-20 по 04b §4b.7.1: 2,9 имп/с на мкЗв/ч (≈ 175 имп/мин), n₀ = 0,2 имп/с, τ = 190 мкс. */
    public static final GeigerCounter SBM20 = new GeigerCounter(2.9, 0.2, 190e-6);
    /** Порог перехода дозиметра-радиометра на ионизационную камеру, имп/с (поправленный счёт). */
    public static final double CHAMBER_SWITCH_CPS = 2000;

    public GeigerCounter {
        if (!(cpsPerUSvH > 0) || backgroundCps < 0 || !(deadTimeS > 0))
            throw new IllegalArgumentException("counter needs positive sensitivity and dead time");
    }

    /** Истинная скорость счёта в поле {@code uSvH}, имп/с. */
    public double trueCps(double uSvH) {
        return cpsPerUSvH * Math.max(uSvH, 0) + backgroundCps;
    }

    /** Наблюдаемая скорость счёта (продлевающееся мёртвое время), имп/с. */
    public double observedCps(double uSvH) {
        double n = trueCps(uSvH);
        return n * Math.exp(-n * deadTimeS);
    }

    /** Наибольшая скорость счёта, которую трубка вообще может показать, имп/с. */
    public double maxObservedCps() {
        return 1 / (Math.E * deadTimeS);
    }

    /** Показание счётчика без коррекции: наблюдаемый счёт, делённый на чувствительность, мкЗв/ч. */
    public double readingUSvH(double uSvH) {
        return observedCps(uSvH) / cpsPerUSvH;
    }

    /** Счёт, поправленный по непродлевающейся модели n̂ = m/(1 − mτ), имп/с. */
    public double correctedCps(double observedCps) {
        double d = 1 - observedCps * deadTimeS;
        return d <= 0 ? Double.POSITIVE_INFINITY : observedCps / d;
    }

    /** Относительная статистическая погрешность показания при усреднении {@code averagingS}: 1/√(m·τ_ср). */
    public double relativeError(double uSvH, double averagingS) {
        double counts = observedCps(uSvH) * averagingS;
        return counts <= 0 ? Double.POSITIVE_INFINITY : 1 / Math.sqrt(counts);
    }
}
