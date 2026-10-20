package dev.denfry.promptcritical.sim.metallurgy;

/** Форма проката (08 §8.7.2): {@code steel_billet}, {@code steel_plate}, {@code steel_rod}, {@code steel_beam}. */
public enum StockShape {
    INGOT("steel_ingot"), BILLET("steel_billet"), PLATE("steel_plate"), ROD("steel_rod"), BEAM("steel_beam");

    /** ID предмета. */
    public final String itemId;

    StockShape(String itemId) {
        this.itemId = itemId;
    }
}
