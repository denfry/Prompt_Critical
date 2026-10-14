package dev.denfry.promptcritical.sim.core;

import static org.assertj.core.api.Assertions.assertThat;

import dev.denfry.promptcritical.sim.core.exec.WorkerPool;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class SimulationWorldTest {
    private static final long SEED = 0x5EEDL;

    /** Подсистема-образец: FAST-релаксация, SLOW из 4 порций, команда, сохранение входов задания. */
    static final class Accumulator implements Subsystem {
        final String id;
        final boolean delayOnWorkers;
        final Job job = new Job();
        final long[] mergeTicks = new long[64];
        int merges;
        SimulationWorld world;
        int slot = -1;
        double x = 1.0;
        double demand;
        double slowSum;
        boolean safe;

        Accumulator(String id, boolean delayOnWorkers) {
            this.id = id;
            this.delayOnWorkers = delayOnWorkers;
        }

        final class Job implements SlowJob, SlowResult {
            double input;
            final double[] partial = new double[4];

            @Override
            public int portions() {
                return partial.length;
            }

            @Override
            public void run(int portion) {
                if (delayOnWorkers && Thread.currentThread().getName().startsWith("PC-Sim-Worker")) {
                    try {
                        Thread.sleep(30);                                 // T-05-04: задание опаздывает
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                partial[portion] = StrictMath.exp(-input * (portion + 1)) + 1.0 / (1.0 + input * portion);
            }

            @Override
            public SlowResult result() {
                return this;
            }

            @Override
            public void save(StateWriter out) {
                out.putDouble("input", input);
            }
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public void stepFast(FastContext ctx) {
            if (slot < 0) return;
            // StrictMath.sin вне [−π/4, π/4] выделяет память (fdlibm rem_pio2) — здесь пилообразный вход
            x = x * 0.999 + demand * 1e-3 + 2e-6 * ((ctx.tick() % 100) - 50) + 1e-6 * slowSum;
            ctx.publicState().set(slot, x, Quality.GOOD);
        }

        @Override
        public SlowJob prepareSlow(SlowContext ctx) {
            job.input = x;
            return job;
        }

        @Override
        public void mergeSlow(SlowResult r) {
            Job j = (Job) r;
            for (double p : j.partial) slowSum += p;
            if (world != null && merges < mergeTicks.length) mergeTicks[merges] = world.clock().tick();
            merges++;
        }

        @Override
        public SlowJob restoreSlow(StateReader in) {
            job.input = in.getDouble("input");
            return job;
        }

        @Override
        public CommandStatus apply(SimCommand command, CommandSource source) {
            if (command instanceof SimCommand.SetValveDemand d) {
                demand = d.position();
                return CommandStatus.OK;
            }
            return CommandStatus.NOT_HANDLED;
        }

        @Override
        public void save(StateWriter out) {
            out.putDouble("x", x);
            out.putDouble("demand", demand);
            out.putDouble("slow_sum", slowSum);
        }

        @Override
        public void load(StateReader in) {
            x = in.getDouble("x");
            demand = in.getDouble("demand");
            slowSum = in.getDouble("slow_sum");
        }

        @Override
        public void resetToSafeState() {
            safe = true;
            demand = 0;
        }
    }

    private static SimulationWorld world(WorkerPool pool, Accumulator... subs) {
        SimulationWorld w = new SimulationWorld(SimConfig.defaults(), SEED, 0, pool);
        for (Accumulator a : subs) {
            w.register(a);
            a.world = w;
            a.slot = w.publicState().register(a.id + ".x");
        }
        return w;
    }

    private static void run(SimulationWorld w, int ticks) {
        for (int i = 0; i < ticks; i++) {
            if (w.clock().tick() == 33) {
                w.commands().submit(new SimCommand.SetValveDemand(SimId.of(SimId.Kind.VALVE, 1), 0.4),
                        new CommandSource.Lua(1, null), 0);
            }
            w.tick(ExternalInputs.NONE);
        }
    }

    @Test
    void slowResultIsMergedExactlyTwentyTicksAfterPrepare() {
        Accumulator a = new Accumulator("a", false);
        Accumulator b = new Accumulator("b", false);
        try (WorkerPool pool = WorkerPool.create(2)) {
            SimulationWorld w = world(pool, a, b);
            run(w, 81);
            assertThat(a.merges).isEqualTo(4);
            assertThat(java.util.Arrays.copyOf(a.mergeTicks, 4)).containsExactly(20, 40, 60, 80);
            assertThat(java.util.Arrays.copyOf(b.mergeTicks, 3)).containsExactly(21, 41, 61);   // фаза 1
            w.close();
        }
    }

    @Test
    @Tag("T-05-04")
    void lateWorkerIsStolenFromAndStateIsIdentical() {
        long reference;
        try (WorkerPool direct = WorkerPool.direct()) {
            SimulationWorld w = world(direct, new Accumulator("a", false));
            run(w, 200);
            reference = w.stateHash();
            w.close();
        }
        try (WorkerPool pool = WorkerPool.create(1)) {
            SimulationWorld w = world(pool, new Accumulator("a", true));
            run(w, 200);
            assertThat(w.stateHash()).isEqualTo(reference);
            w.close();
            assertThat(pool.stolenPortions()).isPositive();
        }
    }

    @Test
    @Tag("T-05-03")
    void saveAndLoadMidSlowJobContinuesBitForBit() {
        long reference;
        MutableStateTree saved;
        try (WorkerPool pool = WorkerPool.create(2)) {
            SimulationWorld w = world(pool, new Accumulator("a", false), new Accumulator("b", false));
            run(w, 110);                                  // задания SLOW обеих подсистем в работе
            saved = w.saveTree();
            run(w, 150);
            reference = w.stateHash();
            w.close();
        }
        try (WorkerPool pool = WorkerPool.create(2)) {
            SimulationWorld w = world(pool, new Accumulator("a", false), new Accumulator("b", false));
            w.load(SimulationWorld.upgrade(saved, new MigrationRegistry()));
            assertThat(w.clock().tick()).isEqualTo(110);
            run(w, 150);
            assertThat(w.stateHash()).isEqualTo(reference);
            w.close();
        }
    }

    @Test
    void commandsAndConfigApplyAtTickBoundaryWithResults() {
        Accumulator a = new Accumulator("a", false);
        try (WorkerPool pool = WorkerPool.direct()) {
            SimulationWorld w = world(pool, a);
            SimConfig next = SimConfig.defaults().withTime(36.0, 1000.0);
            long t1 = w.commands().submit(new SimCommand.ReloadConfig(next), new CommandSource.Admin(new java.util.UUID(1, 2)), 0);
            long t2 = w.commands().submit(new SimCommand.SetValveDemand(SimId.of(SimId.Kind.VALVE, 9), 0.7),
                    new CommandSource.Panel(new java.util.UUID(3, 4), 0L), 0);
            long t3 = w.commands().submit(new SimCommand.SetDeviceMode(SimId.of(SimId.Kind.DEVICE, 99), DeviceMode.REMOTE),
                    new CommandSource.Lua(2, null), 0);
            assertThat(w.clock().kSlow()).isEqualTo(72.0);
            w.tick(ExternalInputs.NONE);
            assertThat(w.clock().kSlow()).isEqualTo(36.0);
            assertThat(a.demand).isEqualTo(0.7);
            List<SimEvent> events = new ArrayList<>();
            w.drainEvents(events::add);
            assertThat(events).extracting(e -> ((SimEvent.CommandResult) e).ticket(), e -> ((SimEvent.CommandResult) e).status())
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(t1, CommandStatus.OK),
                            org.assertj.core.groups.Tuple.tuple(t2, CommandStatus.OK),
                            org.assertj.core.groups.Tuple.tuple(t3, CommandStatus.NO_TARGET));
            w.close();
        }
    }

    @Test
    void deviceRemovalTakesEffectAtNextTick() {
        List<SimId> removed = new ArrayList<>();
        Subsystem watcher = new Subsystem() {
            @Override
            public String id() {
                return "watcher";
            }

            @Override
            public void onDeviceRemoved(SimId device) {
                removed.add(device);
            }

            @Override
            public void save(StateWriter out) {
            }

            @Override
            public void load(StateReader in) {
            }
        };
        try (WorkerPool pool = WorkerPool.direct()) {
            SimulationWorld w = new SimulationWorld(SimConfig.defaults(), SEED, 0, pool);
            w.register(watcher);
            SimId boiler = w.topology().addDevice("promptcritical:fire_tube_boiler", new GridPos(10, 64, -3));
            SimId stoker = w.topology().addDevice("promptcritical:grate_stoker", new GridPos(10, 63, -3));
            assertThat(boiler.kind()).isEqualTo(SimId.Kind.DEVICE);
            assertThat(w.topology().devicePos(boiler)).isEqualTo(new GridPos(10, 64, -3));
            w.topology().removeDevice(boiler);
            assertThat(w.topology().exists(boiler)).isTrue();
            w.tick(ExternalInputs.NONE);
            assertThat(w.topology().exists(boiler)).isFalse();
            assertThat(removed).containsExactly(boiler);
            assertThat(w.topology().deviceType(stoker)).isEqualTo("promptcritical:grate_stoker");

            MutableStateTree t = w.saveTree();
            SimulationWorld w2 = new SimulationWorld(SimConfig.defaults(), SEED, 0, pool);
            w2.register(watcher);
            w2.load(t);
            assertThat(w2.topology().deviceCount()).isEqualTo(1);
            SimId next = w2.topology().addDevice("promptcritical:chimney", new GridPos(0, 0, 0));
            assertThat(next.serial()).isEqualTo(3);                      // номера не переиспользуются
        }
    }

    @Test
    @Tag("T-05-13")
    void schemaZeroSaveMigratesAndMissingSubsystemGoesSafe() {
        // «Тестовая» схема 0: тик под другим ключом, подсистемы — в "systems".
        MutableStateTree v0 = new MutableStateTree().put("time_ticks", 500L);
        new TreeStateWriter(v0.childOrCreate("topology")).putLong("next_serial", 1);
        TreeStateWriter dev = new TreeStateWriter(v0.child("topology"));
        dev.beginObject("devices");
        dev.putInt("count", 0);
        dev.putLongs("id", new long[0], 0);
        dev.putLongs("pos", new long[0], 0);
        dev.putInts("type", new int[0], 0);
        dev.putBytes("mode", new byte[0], 0);
        dev.beginObject("types");
        dev.endObject();
        dev.endObject();
        MutableStateTree systems = v0.childOrCreate("systems");
        systems.childOrCreate("a").put("x", 2.5).put("demand", 0.3).put("slow_sum", 7.0);
        systems.childOrCreate("legacy").put("foo", 1);

        MigrationRegistry reg = new MigrationRegistry();
        reg.register(new Migration() {
            @Override
            public int from() {
                return 0;
            }

            @Override
            public void apply(MutableStateTree tree) {
                tree.rename("time_ticks", "tick");
                tree.rename("systems", "subsystems");
            }
        });

        Accumulator a = new Accumulator("a", false);
        Accumulator boiler = new Accumulator("boiler", false);
        try (WorkerPool pool = WorkerPool.direct()) {
            SimulationWorld w = world(pool, a, boiler);
            w.load(SimulationWorld.upgrade(v0, reg));
            assertThat(w.clock().tick()).isEqualTo(500);
            assertThat(a.x).isEqualTo(2.5);
            assertThat(a.demand).isEqualTo(0.3);
            assertThat(a.slowSum).isEqualTo(7.0);
            assertThat(a.safe).isFalse();
            assertThat(boiler.safe).isTrue();

            w.tick(ExternalInputs.NONE);
            List<String> journal = new ArrayList<>();
            w.drainEvents(e -> {
                if (e instanceof SimEvent.Journal j && j.severity() == Severity.WARNING) journal.add(j.message());
            });
            assertThat(journal).anyMatch(m -> m.contains("'boiler'") && m.contains("safe state"));
            assertThat(journal).anyMatch(m -> m.contains("'legacy'") && m.contains("dropped"));
        }
    }

    @Test
    @Tag("T-05-02")
    void tickDoesNotAllocate() {
        try (WorkerPool pool = WorkerPool.direct()) {
            SimulationWorld w = world(pool, new Accumulator("a", false), new Accumulator("b", false));
            com.sun.management.ThreadMXBean mx =
                    (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
            long thread = Thread.currentThread().threadId();
            for (int k = 0; k < 5_000; k++) w.tick(ExternalInputs.NONE);
            long before = mx.getThreadAllocatedBytes(thread);
            for (int k = 0; k < 20_000; k++) w.tick(ExternalInputs.NONE);
            long allocated = mx.getThreadAllocatedBytes(thread) - before;
            assertThat(allocated / 20_000).as("bytes per tick (total %d B)", allocated).isZero();
        }
    }

    @Test
    void randomStreamsAreSeededDeterministically() {
        SimId id = SimId.of(SimId.Kind.DEVICE, 5);
        assertThat(SimRandom.of(SEED, id, 0).nextLong()).isEqualTo(SimRandom.of(SEED, id, 0).nextLong());
        assertThat(SimRandom.mix(SEED, id.raw(), 0)).isNotEqualTo(SimRandom.mix(SEED, id.raw(), 1));
    }
}
