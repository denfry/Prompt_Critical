package dev.denfry.promptcritical.sim.hazard;

/**
 * Тепловое излучение пожара и его действие (09 §9.11.4, API Std 521, ГОСТ Р 12.3.047): точечный источник
 * q″ = χr·Q/(4πR²) и пороги плотности потока.
 */
public final class ThermalRadiation {
    /** Безопасно неограниченно, кВт/м². */
    public static final double SAFE_KW_M2 = 1.6;
    /** Боль через 20 с: урон игроку 0,5 ♥/с, кВт/м² (порог 4–5). */
    public static final double PAIN_KW_M2 = 4.0;
    /** Воспламенение дерева, шерсти, листвы, кВт/м². */
    public static final double IGNITION_KW_M2 = 12.5;
    /** Разрушение металлоконструкций за 10 мин, кВт/м². */
    public static final double STEEL_KW_M2 = 37.5;

    /** Урон игроку при боли, ед. здоровья в секунду (0,5 ♥/с, §9.11.4). */
    public static final double PAIN_HP_S = 1.0;
    /**
     * Выше порога воспламенения одежда загорается, урон растёт [BALANCE]: при 12,5 кВт/м² — 2 ед./с, при 37,5 —
     * 4 ед./с (за минуту при 37,5 кВт/м² гибнет любой, API 521).
     */
    public static final double IGNITION_HP_S = 2.0, STEEL_HP_S = 4.0;

    private ThermalRadiation() {
    }

    /** Плотность потока на расстоянии {@code rM} от точечного источника мощностью {@code qW}, кВт/м². */
    public static double pointSourceKwM2(double radiantFraction, double qW, double rM) {
        double r = Math.max(1, rM);
        return radiantFraction * qW / (4 * Math.PI * r * r) / 1000;
    }

    /** Урон игроку за секунду при потоке {@code kwM2}. */
    public static double damagePerSecond(double kwM2) {
        if (kwM2 >= STEEL_KW_M2) return STEEL_HP_S;
        if (kwM2 >= IGNITION_KW_M2) return IGNITION_HP_S;
        if (kwM2 >= PAIN_KW_M2) return PAIN_HP_S;
        return 0;
    }

    /** Поток поджигает дерево, шерсть и листву. */
    public static boolean ignitesWood(double kwM2) {
        return kwM2 >= IGNITION_KW_M2;
    }
}
