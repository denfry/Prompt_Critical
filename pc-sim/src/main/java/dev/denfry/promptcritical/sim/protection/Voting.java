package dev.denfry.promptcritical.sim.protection;

/**
 * Логика голосования «k из n» (01 §1.11.2). Выведенный канал ({@code BYPASS}) исключается из n,
 * k не меняется: 2 из 3 → 2 из 2, 1 из 2 → 1 из 1.
 */
public enum Voting {
    ONE_OF_ONE(1, 1), ONE_OF_TWO(1, 2), TWO_OF_TWO(2, 2), TWO_OF_THREE(2, 3), TWO_OF_FOUR(2, 4);

    private final int required;
    private final int channels;

    Voting(int required, int channels) {
        this.required = required;
        this.channels = channels;
    }

    /** k — сколько каналов группы должно сказать «да». */
    public int required() {
        return required;
    }

    /** n — каналов в группе (на петлю, ПГ, насос). */
    public int channels() {
        return channels;
    }

    /** Можно ли вывести один канал, не потеряв функцию: после вывода остаётся не меньше k каналов. */
    public boolean allowsBypass() {
        return channels - 1 >= required;
    }
}
