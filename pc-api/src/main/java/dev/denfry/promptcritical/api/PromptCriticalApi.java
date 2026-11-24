package dev.denfry.promptcritical.api;

/** Entry point of the addon API (05 §5.8.1). */
public interface PromptCriticalApi {
    /** Major API version: raised on incompatible changes. */
    int CURRENT_API_VERSION = 1;

    static PromptCriticalApi get() {
        return ApiHolder.get();
    }

    int apiVersion();

    /**
     * Read-only access to the simulation of dimension {@code dimension} ({@code minecraft:overworld}). The dimension is
     * a resource location string so that this module stays free of Minecraft classes.
     */
    SimulationAccess simulation(String dimension);
}
