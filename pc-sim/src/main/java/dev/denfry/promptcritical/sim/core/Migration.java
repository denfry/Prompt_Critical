package dev.denfry.promptcritical.sim.core;

/**
 * Шаг миграции сохранения с версии {@link #from()} на {@code from() + 1} (05 §5.2.8). Миграции только добавляют,
 * переименовывают и пересчитывают поля; подсистема, которую перенести нельзя, загружается в безопасном состоянии.
 */
public interface Migration {
    int from();

    void apply(MutableStateTree tree);
}
