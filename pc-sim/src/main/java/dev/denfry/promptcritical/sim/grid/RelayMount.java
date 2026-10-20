package dev.denfry.promptcritical.sim.grid;

import java.util.List;
import java.util.Objects;

/** Куда включено реле: откуда берёт измерения и чем действует (07 §7.5.2). */
public sealed interface RelayMount permits RelayMount.Feeder, RelayMount.Generator, RelayMount.Busbar,
        RelayMount.Load, RelayMount.Motor {
    /**
     * Присоединение: ток ветви с конца {@code atFrom} и напряжение этой шины; отключает выключатель
     * ветви. Дифзащита (87T, 87L) сравнивает токи обоих концов.
     */
    record Feeder(BranchId branch, boolean atFrom) implements RelayMount {
        public Feeder {
            Objects.requireNonNull(branch, "branch");
        }
    }

    /** Генератор: ток, мощность и сопротивление на выводах машины; отключает выключатель её присоединения. */
    record Generator(MachineId machine, BranchId breaker) implements RelayMount {
        public Generator {
            Objects.requireNonNull(machine, "machine");
            Objects.requireNonNull(breaker, "breaker");
        }
    }

    /** Шина (87B, 27, 81): отключает все выключатели {@code trips}. */
    record Busbar(BusId bus, List<BranchId> trips) implements RelayMount {
        public Busbar {
            Objects.requireNonNull(bus, "bus");
            trips = List.copyOf(trips);
        }
    }

    /** Нагрузка (АЧР и ЧАПВ, 27): отключает и включает её. */
    record Load(LoadId load) implements RelayMount {
        public Load {
            Objects.requireNonNull(load, "load");
        }
    }

    /** Двигатель (49, 27, АЧР): отключает контактор. */
    record Motor(MotorId motor) implements RelayMount {
        public Motor {
            Objects.requireNonNull(motor, "motor");
        }
    }
}
