package dev.denfry.promptcritical.sim.core;

import java.util.UUID;

/**
 * Источник команды (05 §5.2.6). Класс источника и его номер — первые два ключа сортировки при извлечении из
 * {@link CommandQueue} (05 §5.4.3): автоматика, сторожевое реле, политика сервера и админ — 0, пульт — 1, Lua — 2.
 */
public sealed interface CommandSource {
    /** Класс источника в ключе сортировки. */
    int sortClass();

    /** Номер источника внутри класса; детерминирован (не зависит от потока и момента поступления). */
    long sortId();

    record Lua(int computerId, UUID cardHolder) implements CommandSource {
        @Override
        public int sortClass() {
            return 2;
        }

        @Override
        public long sortId() {
            return computerId;
        }
    }

    record Panel(UUID player, long blockPos) implements CommandSource {
        @Override
        public int sortClass() {
            return 1;
        }

        @Override
        public long sortId() {
            return blockPos;
        }
    }

    record Watchdog(SimId relay) implements CommandSource {
        @Override
        public int sortClass() {
            return 0;
        }

        @Override
        public long sortId() {
            return relay.raw();
        }
    }

    record Automatic(String system) implements CommandSource {
        @Override
        public int sortClass() {
            return 0;
        }

        @Override
        public long sortId() {
            return system.hashCode();
        }
    }

    record ServerPolicy(String policy) implements CommandSource {
        @Override
        public int sortClass() {
            return 0;
        }

        @Override
        public long sortId() {
            return policy.hashCode();
        }
    }

    record Admin(UUID player) implements CommandSource {
        @Override
        public int sortClass() {
            return 0;
        }

        @Override
        public long sortId() {
            return player.getMostSignificantBits() ^ player.getLeastSignificantBits();
        }
    }
}
