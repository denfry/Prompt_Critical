package dev.denfry.promptcritical.sim.automation.twin;

import java.util.function.ToDoubleFunction;

/**
 * Условие критерия сценария (11 §11.3.3): «сигнал оператор число», например {@code "level_m < 7.5"}. Операторы
 * {@code < <= > >= == !=}; сравнение — по истинному значению установки, а не по показанию прибора.
 */
public record Condition(String signal, Op op, double value) {
    public enum Op {
        LT("<"), LE("<="), GT(">"), GE(">="), EQ("=="), NE("!=");

        final String text;

        Op(String text) {
            this.text = text;
        }
    }

    public static Condition parse(String s) {
        String[] parts = s.trim().split("\\s+");
        if (parts.length != 3) throw new IllegalArgumentException("condition must be 'signal op value': " + s);
        Op op = null;
        for (Op o : Op.values()) if (o.text.equals(parts[1])) op = o;
        if (op == null) throw new IllegalArgumentException("unknown operator in " + s);
        return new Condition(parts[0], op, Double.parseDouble(parts[2]));
    }

    public boolean test(ToDoubleFunction<String> values) {
        double v = values.applyAsDouble(signal);
        if (Double.isNaN(v)) return false;
        return switch (op) {
            case LT -> v < value;
            case LE -> v <= value;
            case GT -> v > value;
            case GE -> v >= value;
            case EQ -> v == value;
            case NE -> v != value;
        };
    }

    @Override
    public String toString() {
        return signal + " " + op.text + " " + value;
    }
}
