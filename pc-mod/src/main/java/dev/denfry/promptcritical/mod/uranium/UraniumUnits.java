package dev.denfry.promptcritical.mod.uranium;

import dev.denfry.promptcritical.mod.item.OreAssayData;
import dev.denfry.promptcritical.mod.registry.PcDataComponents;
import net.minecraft.world.item.ItemStack;

/**
 * Единицы передела урана. Газы — 1 мБ = 1 нм³, жидкости — 1 мБ = 1 л. Порошки урана (ДУА, UO₃, UO₂, UF₄) —
 * предмет на {@value #U_PER_ITEM_KG} кг урана. Руды: молотая урановая руда — {@value #ORE_KG} кг с {@value #ORE_U}
 * урана (жильная урановая смолка, богатая по меркам Шинколобве и Яхимова); пирит и флюорит — {@value #MINERAL_KG}
 * кг с содержанием из пробы ({@code ore_assay}); пиролюзит — {@value #PYROLUSITE_KG} кг с {@value #PYROLUSITE_MNO2}
 * MnO₂; смола — {@value #RESIN_L} л анионита; аммиак — жидкий, 1 мБ = 1 л (0,682 кг).
 */
public final class UraniumUnits {
    public static final double U_PER_ITEM_KG = 1, ORE_KG = 50, ORE_U = 0.004, MINERAL_KG = 50, PYROLUSITE_KG = 10,
            PYROLUSITE_MNO2 = 0.5, RESIN_L = 100;
    /** Пирит без пробы — 35 % S, флюорит — 30 % CaF₂ (середины законов руды каталога жил). */
    public static final double PYRITE_S_DEFAULT = 0.35, FLUORITE_DEFAULT = 0.30;
    public static final double ACID_KG_L = 1.84, HF_KG_L = 0.99, F2_KG_NM3 = 37.997 / 22.414, NH3_KG_L = 0.682,
            H2_KG_NM3 = 2.016 / 22.414;
    /** Уран в растворах: продуктивный раствор 1 г/л, элюат 10 г/л. */
    public static final double PLS_KG_U_L = 0.001, ELUATE_KG_U_L = 0.010;
    /** Хвосты: пульпа 50 % твёрдого, л на кг руды. */
    public static final double TAILINGS_L_PER_KG = 1.4;
    /** Игровые часы за тик (тик — 3,6 с игрового времени). */
    public static final double GAME_H_PER_TICK = 3.6 / 3600;
    public static final double FUEL_J_NM3 = 36e6;
    /** Природный уран (Benedict–Pigford–Levi): атомные доли U-235 и U-234. */
    public static final double NATURAL_U235 = 0.00711, NATURAL_U234 = 0.000054;

    private UraniumUnits() {
    }

    /** Содержание по пробе руды или значение по умолчанию. */
    public static double assay(ItemStack s, double fallback) {
        OreAssayData a = s.get(PcDataComponents.ORE_ASSAY.get());
        if (a == null) return fallback;
        double f = a.toAssay().gradeFrac();
        return f > 0 ? f : fallback;
    }
}
