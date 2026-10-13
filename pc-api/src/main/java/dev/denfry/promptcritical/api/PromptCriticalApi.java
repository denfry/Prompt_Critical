package dev.denfry.promptcritical.api;

/**
 * Точка входа API для аддонов (05 §5.8.1). Доступ к симуляции и события
 * регистрации добавляются по мере появления доменных подсистем.
 */
public interface PromptCriticalApi {
    /** Мажорная версия API: увеличивается при несовместимых изменениях. */
    int CURRENT_API_VERSION = 1;

    static PromptCriticalApi get() {
        return ApiHolder.get();
    }

    int apiVersion();
}
