package dev.denfry.promptcritical.sim.manufacturing;

import dev.denfry.promptcritical.sim.metallurgy.HeatTreatState;
import dev.denfry.promptcritical.sim.metallurgy.SteelFamily;
import dev.denfry.promptcritical.sim.metallurgy.SteelGrade;
import dev.denfry.promptcritical.sim.metallurgy.StockState;
import java.util.Map;

/**
 * Сыродутный горн (18 §18.6.1): руда восстанавливается древесным углём ниже плавления железа, металл слипается в
 * губчатую крицу со шлаком. Мехи поднимают температуру горна, уголь горит; выше 1100 °C идёт восстановление, выше
 * 1350 °C железо науглероживается и крица «закозляется» в чугун. Модель — сосредоточенная: температура подходит к
 * уставке мехов с постоянной времени, восстановление первого порядка по остатку руды. Время — секунды игры.
 *
 * <p>Состояние неизменяемое: {@link #step} возвращает новое.
 */
public record Bloomery(double oreKg, double oreFeFrac, double charcoalKg, double tempK, double ironKg,
        double overheatS, boolean castIron) {

    /** Воздух без дутья, К. */
    public static final double AMBIENT_K = 293.15;
    /** Уставка горна без мехов и на полном дутье: 900…1400 °C [сверить: 1100–1300 °C рабочих, 18 §18.6.1]. */
    public static final double FIRE_MIN_K = 1173.15, FIRE_SPAN_K = 500;
    /** Постоянная времени горна по температуре, с [BALANCE]. */
    public static final double TAU_S = 120;
    /** Восстановление идёт от 1100 °C (18 §18.6.1). */
    public static final double REDUCTION_MIN_K = 1373.15;
    /** Скорость восстановления при 1200 °C, доля остатка руды в секунду [BALANCE: заряд за ~15 мин игры]. */
    public static final double REDUCTION_RATE_S = 1.0 / 300;
    /** Перегрев: выше 1350 °C дольше 5 мин — железо науглероживается в чугун (18 §18.6.1) [BALANCE по времени]. */
    public static final double OVERHEAT_K = 1623.15, OVERHEAT_LIMIT_S = 300;
    /** Доля железа руды, которая уходит в крицу; остальное — в фаялитовый шлак [сверить: выход 20–50 %]. */
    public static final double IRON_YIELD = 0.5;
    /** Шлак в сырой крице, доля массы (18 §18.6.1: 30–50 %). */
    public static final double BLOOM_SLAG = 0.4;
    /** Расход угля: кг/с на холостом ходу и на полном дутье [BALANCE: ~1 кг угля на кг руды за плавку]. */
    public static final double COAL_IDLE_KG_S = 0.0008, COAL_BLAST_KG_S = 0.004;
    /** Руда считается выработанной, когда в ней осталось меньше этой доли железа [моё]. */
    public static final double DONE_FRAC = 0.01;

    public Bloomery {
        if (!(oreKg >= 0) || !(charcoalKg >= 0) || !(ironKg >= 0) || !(tempK > 0) || !(overheatS >= 0)
                || !(oreFeFrac >= 0 && oreFeFrac <= 1)) {
            throw new IllegalArgumentException("bloomery state out of range");
        }
    }

    /** Холодный горн с зарядом руды и угля. */
    public static Bloomery charged(double oreKg, double oreFeFrac, double charcoalKg) {
        return new Bloomery(oreKg, oreFeFrac, charcoalKg, AMBIENT_K, 0, 0, false);
    }

    /** Уставка горна при дутье {@code blast} ∈ [0, 1]. */
    public static double fireK(double blast) {
        return FIRE_MIN_K + FIRE_SPAN_K * Math.max(0, Math.min(1, blast));
    }

    /** Шаг {@code dt} с дутьём {@code blast}. Без угля горн остывает к воздуху. */
    public Bloomery step(double blast, double dt) {
        if (!(dt > 0) || !Double.isFinite(dt)) {
            return this;
        }
        double b = Math.max(0, Math.min(1, blast));
        double coal = Math.max(0, charcoalKg - (COAL_IDLE_KG_S + COAL_BLAST_KG_S * b) * dt);
        double target = charcoalKg > 0 ? fireK(b) : AMBIENT_K;
        double t = target + (tempK - target) * Math.exp(-dt / TAU_S);
        double ore = oreKg;
        double iron = ironKg;
        if (t >= REDUCTION_MIN_K && charcoalKg > 0 && ore > 0) {
            // скорость растёт с температурой: при 1100 °C — половина, при 1300 °C — полторы скорости 1200 °C [моё]
            double rate = REDUCTION_RATE_S * Math.max(0.5, Math.min(1.5, 0.5 + (t - REDUCTION_MIN_K) / 200));
            double reduced = ore * (1 - Math.exp(-rate * dt));
            ore -= reduced;
            iron += reduced * oreFeFrac * IRON_YIELD;
        }
        double over = t > OVERHEAT_K ? overheatS + dt : overheatS;
        return new Bloomery(ore, oreFeFrac, coal, t, iron, over, castIron || over > OVERHEAT_LIMIT_S);
    }

    /** Заряд выработан: руды почти не осталось, крицу можно вынимать. */
    public boolean done() {
        return ironKg > 0 && oreKg * oreFeFrac <= DONE_FRAC * (oreKg * oreFeFrac + ironKg / IRON_YIELD);
    }

    /** Масса крицы вместе со шлаком, кг. */
    public double bloomKg() {
        return ironKg / (1 - BLOOM_SLAG);
    }

    /**
     * Крица как заготовка: кричное железо, квадрат по объёму металла и шлака (плотность стали), горячая, шлак —
     * дефект {@link PartDefect#SLAG_INCLUSION} тяжестью {@code BLOOM_SLAG / 0,5}. Закозлившаяся крица заготовкой
     * не бывает — это чугун.
     */
    public Workpiece bloom() {
        if (castIron || !(ironKg > 0)) {
            throw new IllegalStateException("no forgeable bloom");
        }
        double side = Math.cbrt(bloomKg() / StockState.DENSITY);
        SteelGrade iron = SteelGrade.of(SteelFamily.WROUGHT_IRON, SteelFamily.WROUGHT_IRON.designation,
                Map.of("C", 0.05f), HeatTreatState.AS_ROLLED);
        return Workpiece.bar(iron, Workpiece.Section.SQUARE, side, side, side, tempK)
                .withDefect(PartDefect.SLAG_INCLUSION, BLOOM_SLAG / 0.5);
    }
}
