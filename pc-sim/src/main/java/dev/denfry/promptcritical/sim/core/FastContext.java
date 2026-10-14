package dev.denfry.promptcritical.sim.core;

/** Контекст FAST-шага подсистем. Один объект на мир, переиспользуется каждый тик. */
public final class FastContext {
    private final SimulationWorld world;
    private ExternalInputs inputs = ExternalInputs.NONE;

    FastContext(SimulationWorld world) {
        this.world = world;
    }

    public SimClock clock() {
        return world.clock();
    }

    /** Шаг FAST, с. */
    public double dt() {
        return SimClock.FAST_DT;
    }

    public long tick() {
        return world.clock().tick();
    }

    public ExternalInputs inputs() {
        return inputs;
    }

    public EventBuffer events() {
        return world.events();
    }

    public PublicState publicState() {
        return world.publicState();
    }

    public SimConfig config() {
        return world.config();
    }

    public TopologyEditor topology() {
        return world.topology();
    }

    /** Служба, которую биндинг передал миру (например, {@code DamageBudget}); {@code null}, если её нет. */
    public <T> T service(Class<T> type) {
        return world.service(type);
    }

    void setInputs(ExternalInputs inputs) {
        this.inputs = inputs;
    }
}
