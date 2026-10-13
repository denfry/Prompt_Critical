package dev.denfry.promptcritical.api;

import java.util.Objects;

/** Хранит реализацию {@link PromptCriticalApi}; её устанавливает мод при конструировании. */
public final class ApiHolder {
    private static volatile PromptCriticalApi instance;

    private ApiHolder() {
    }

    public static PromptCriticalApi get() {
        PromptCriticalApi api = instance;
        if (api == null) throw new IllegalStateException("Prompt Critical API is not initialized yet");
        return api;
    }

    /** Вызывается один раз модом Prompt Critical. */
    public static synchronized void install(PromptCriticalApi api) {
        Objects.requireNonNull(api, "api");
        if (instance != null) throw new IllegalStateException("Prompt Critical API is already installed");
        instance = api;
    }
}
