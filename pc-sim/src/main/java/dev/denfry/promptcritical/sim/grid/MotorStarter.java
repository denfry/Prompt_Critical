package dev.denfry.promptcritical.sim.grid;

/**
 * Способ пуска асинхронного двигателя — модуль в блоке двигателя (07 §7.6.3).
 * <ul>
 * <li>{@link #DIRECT} — прямой пуск: 5–7 I_н, большой провал напряжения на шине.</li>
 * <li>{@link #STAR_DELTA} — обмотки в звезду до {@link #STAR_DELTA_SWITCH_SPEED}: напряжение фазы U/√3, ток из сети и
 * момент — треть прямого; на переключении бросок тока, сколько даст скольжение в этот момент. Только 0,4 кВ.</li>
 * <li>{@link #SOFT} — тиристорный регулятор: напряжение растёт от {@link #SOFT_INITIAL_VOLTAGE} за {@link #SOFT_RAMP_S}
 * и не даёт току выйти за {@link #SOFT_CURRENT_LIMIT_PU}; момент ∝ (U/U_н)², тяжёлую нагрузку может не стронуть.
 * На скорости {@link #SOFT_BYPASS_SPEED} замыкается шунтирующий контактор.</li>
 * <li>{@link #VFD} — частотный преобразователь: вал идёт за заданной скоростью с моментом до
 * {@link #VFD_TORQUE_LIMIT_PU} номинального с нуля, ток не выше ≈ 1,5 I_н, из сети — активная мощность вала
 * через КПД {@link #VFD_EFFICIENCY} при cos φ {@link #VFD_POWER_FACTOR} (выпрямитель на входе).</li>
 * </ul>
 */
public enum MotorStarter {
    DIRECT, STAR_DELTA, SOFT, VFD;

    /** Скорость переключения звезды на треугольник, о.е. синхронной (реле времени настроено на разгон). */
    public static final double STAR_DELTA_SWITCH_SPEED = 0.9;
    /** Начальное напряжение плавного пуска, доля U_н. */
    public static final double SOFT_INITIAL_VOLTAGE = 0.3;
    /** Время нарастания напряжения плавного пуска, с (уставка 5–30 с). */
    public static final double SOFT_RAMP_S = 10;
    /** Ограничение тока плавного пуска, о.е. I_н (уставка 2,5–3,5). */
    public static final double SOFT_CURRENT_LIMIT_PU = 3.0;
    /** Скорость, на которой плавный пуск шунтируется контактором, о.е. */
    public static final double SOFT_BYPASS_SPEED = 0.95;
    /** Наибольший момент частотного привода, о.е. номинального (ток ≈ 1,5 I_н). */
    public static final double VFD_TORQUE_LIMIT_PU = 1.5;
    /** Время разгона привода от нуля до синхронной скорости при свободном моменте, с. */
    public static final double VFD_RAMP_S = 5;
    public static final double VFD_EFFICIENCY = 0.97;
    public static final double VFD_POWER_FACTOR = 0.95;
}
