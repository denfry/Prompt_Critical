package dev.denfry.promptcritical.mod.ironworks;

/**
 * Источник холодного дутья для домны (08 §8.5.4): старая воздуходувка {@code blast_blower} и паровоздуходувная машина
 * реального масштаба {@code blast_blower_engine}. Домна забирает дутьё всех источников в пределах
 * {@value BlastFurnaceControllerBlockEntity#LINK_RANGE} блоков.
 */
public interface ColdBlastSource {
    /** Холодное дутьё сейчас, нм³/с физ. */
    double coldNm3S();
}
