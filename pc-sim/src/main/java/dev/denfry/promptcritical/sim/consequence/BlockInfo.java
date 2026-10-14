package dev.denfry.promptcritical.sim.consequence;

/**
 * Сведения о блоке мира для бюджета (04d §4d.2.2).
 *
 * @param air             пустота: менять нечего
 * @param immune          тег {@code #promptcritical:damage_immune} (бедрок, порталы, блоки по датапаку)
 * @param blastResistance сопротивление взрыву по шкале MC
 */
public record BlockInfo(boolean air, boolean immune, float blastResistance) {
    public static final BlockInfo AIR = new BlockInfo(true, false, 0f);
}
