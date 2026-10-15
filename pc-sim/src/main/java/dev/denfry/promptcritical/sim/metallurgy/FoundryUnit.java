package dev.denfry.promptcritical.sim.metallurgy;

import dev.denfry.promptcritical.sim.core.DeviceSubsystem;
import dev.denfry.promptcritical.sim.core.FastContext;

/** Двойник устройства литейки или механического цеха (08 §8.9–8.10) в {@link FoundrySubsystem}. */
public interface FoundryUnit extends DeviceSubsystem.Unit {
    /**
     * Шаг FAST.
     *
     * @param shop   подсистема (общие модели {@link Casting}, {@link Machining})
     * @param ctx    контекст тика
     * @param slowDt &gt; 0 — в этом тике и шаг SLOW печей и форм, физ. с (08 §8.0.3, класс 1)
     */
    void step(FoundrySubsystem shop, FastContext ctx, double slowDt);

    /** Источник расплава — вагранка или тигель: выпуск в форму. */
    interface MeltSource {
        /** Сплав в печи; {@code null} — пусто. */
        CastAlloy meltAlloy();

        /** Жидкий металл, готовый к выпуску, кг (0, пока не расплавлен целиком). */
        double liquidKg();

        double superheatK();

        /** Выпустить до {@code kg}; возвращает выпущенное. */
        double tap(double kg);
    }
}
