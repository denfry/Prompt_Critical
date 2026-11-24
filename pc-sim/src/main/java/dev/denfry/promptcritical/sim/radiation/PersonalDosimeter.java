package dev.denfry.promptcritical.sim.radiation;

/**
 * Электронный индивидуальный дозиметр (ЭПД, 04b §4b.7.1): кремниевый диод, копит индивидуальный эквивалент дозы
 * H_p(10) с последнего сброса и подаёт сигнал по двум уставкам — мощности дозы (сигнал держится, пока поле выше) и
 * накопленной дозе (сигнал фиксируется до сброса). Уставки выбирают пресетом. Плановая работа — по пресету
 * {@link Preset#ROUTINE}; аварийные пресеты — уровни 04b §4b.5.2 (до 100 мЗв при аварийном реагировании, до 500 мЗв
 * при спасении жизни). Диапазон мощности — до {@value #MAX_SV_H} Зв/ч, выше прибор показывает «перегрузку».
 */
public final class PersonalDosimeter {
    /** Верх диапазона мощности дозы, Зв/ч. */
    public static final double MAX_SV_H = 10;

    /**
     * Уставки сигнализации.
     *
     * @param doseSv накопленная доза, Зв
     * @param rateSvH мощность дозы, Зв/ч
     */
    public enum Preset {
        /**
         * Плановая работа в ЗКД [BALANCE]: 1 мЗв за выход (двадцатая доля годового предела персонала) и 100 мкЗв/ч —
         * больше восьми проектных мощностей необслуживаемых помещений (04b §4b.12.1).
         */
        ROUTINE(1e-3, 1e-4),
        /** Аварийное реагирование: 100 мЗв (IAEA GSR Part 3), мощность 100 мЗв/ч. */
        EMERGENCY_100(0.1, 0.1),
        /** Спасение жизни, добровольно: 500 мЗв, мощность 1 Зв/ч. */
        LIFESAVING_500(0.5, 1);

        public final double doseSv, rateSvH;

        Preset(double doseSv, double rateSvH) {
            this.doseSv = doseSv;
            this.rateSvH = rateSvH;
        }

        public Preset next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    /** Состояние сигнализации. */
    public enum Alarm {
        NONE, RATE, DOSE, BOTH;

        public boolean any() {
            return this != NONE;
        }
    }

    private PersonalDosimeter() {
    }

    /** Сигнал при накопленной {@code doseSv} и мощности {@code rateSvH}. */
    public static Alarm alarm(Preset p, double doseSv, double rateSvH) {
        boolean dose = doseSv >= p.doseSv, rate = rateSvH >= p.rateSvH;
        return dose && rate ? Alarm.BOTH : dose ? Alarm.DOSE : rate ? Alarm.RATE : Alarm.NONE;
    }

    /** Показание мощности: выше диапазона прибор упирается в верх шкалы. */
    public static double displayedRateSvH(double rateSvH) {
        return Math.min(Math.max(rateSvH, 0), MAX_SV_H);
    }

    /** Мощность выше диапазона: прибор показывает перегрузку. */
    public static boolean overRange(double rateSvH) {
        return rateSvH > MAX_SV_H;
    }
}
