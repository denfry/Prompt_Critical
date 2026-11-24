package dev.denfry.promptcritical.sim.journal;

import java.util.UUID;

/** Кто совершил действие в журнале станции (04d §4d.8.1). */
public sealed interface Actor permits Actor.Player, Actor.Computer, Actor.Automatic, Actor.ServerPolicy, Actor.Admin {
    /** Строка для экспорта и чата. */
    String display();

    /** Игрок, ответственный за действие ({@code null} у автоматики и политики сервера). */
    UUID responsible();

    record Player(UUID uuid, String nameAtTime) implements Actor {
        public String display() {
            return nameAtTime;
        }

        public UUID responsible() {
            return uuid;
        }
    }

    /** @param cardHolder владелец вставленной ключ-карты, может быть {@code null} */
    record Computer(int computerId, UUID cardHolder, String label) implements Actor {
        public String display() {
            return "computer#" + computerId + (label == null || label.isEmpty() ? "" : " " + label);
        }

        public UUID responsible() {
            return cardHolder;
        }
    }

    /** Автоматика: {@code "PROTECTION"}, {@code "WATCHDOG:WD-1"}. */
    record Automatic(String system) implements Actor {
        public String display() {
            return system;
        }

        public UUID responsible() {
            return null;
        }
    }

    /** Политика сервера, например {@code "SAFE_SHUTDOWN"}. */
    record ServerPolicy(String policy) implements Actor {
        public String display() {
            return "server:" + policy;
        }

        public UUID responsible() {
            return null;
        }
    }

    record Admin(UUID uuid, String command) implements Actor {
        public String display() {
            return "admin:" + command;
        }

        public UUID responsible() {
            return uuid;
        }
    }
}
