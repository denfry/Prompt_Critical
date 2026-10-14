package dev.denfry.promptcritical.sim.core.profile;

import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

/** JFR-событие тика (05 §5.4.7); при выключенной записи стоит ≈ 0. */
@Name("promptcritical.SimTick")
@Label("Prompt Critical: sim tick")
@Category({"Prompt Critical", "Simulation"})
@StackTrace(false)
public final class SimTickEvent extends Event {
    @Label("Dimension")
    public int dimension;
    @Label("Tick")
    public long tick;
    @Label("FAST, мкс")
    public long fastMicros;
    @Label("SLOW merge, мкс")
    public long slowMergeMicros;
    @Label("Commands")
    public int commands;
    @Label("Degradation")
    public int degradation;
}
