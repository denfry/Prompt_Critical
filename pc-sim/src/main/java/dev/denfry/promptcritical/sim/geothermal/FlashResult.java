package dev.denfry.promptcritical.sim.geothermal;

/**
 * Итог флеша в сепараторе (15 §15.14).
 *
 * @param steamFraction массовая доля пара x, 0…1
 * @param steamKgS      пар на турбину, кг/с (без НКГ)
 * @param brineKgS      отсепарированный рассол на закачку, кг/с
 * @param ncgKgS        неконденсирующиеся газы, ушедшие с паром, кг/с
 */
public record FlashResult(double steamFraction, double steamKgS, double brineKgS, double ncgKgS) {
    public static final FlashResult NONE = new FlashResult(0, 0, 0, 0);
}
