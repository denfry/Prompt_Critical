package dev.denfry.promptcritical.mod.ironworks;

import dev.denfry.promptcritical.mod.item.OreAssayData;
import dev.denfry.promptcritical.mod.registry.PcDataComponents;
import dev.denfry.promptcritical.mod.registry.PcItems;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Химия железосодержащего предмета: Fe, SiO₂, CaO (доли). Агломерат — по {@code sinter_assay}; концентрат —
 * 66 % Fe, 8 % SiO₂; руда — Fe по {@code ore_assay}, пустая порода — кварц (моё: магнетит в кварците 90 % SiO₂
 * породы, гематит — 80 %), CaO 2 % породы.
 */
public record OreChem(double fe, double sio2, double cao) {
    static final double MAGNETITE_FE = 0.7236, HEMATITE_FE = 0.6994;
    static final OreChem CONCENTRATE = new OreChem(0.66, 0.08, 0.01);

    @Nullable
    public static OreChem of(ItemStack s) {
        if (s.is(PcItems.SINTER.get())) {
            IronworksComponents.SinterAssay a = s.getOrDefault(IronworksComponents.SINTER_ASSAY.get(), IronworksComponents.SinterAssay.DEFAULT);
            return new OreChem(a.fePct() / 100.0, a.sio2Pct() / 100.0, a.caoPct() / 100.0);
        }
        if (s.is(PcItems.IRON_ORE_CONCENTRATE.get())) return CONCENTRATE;
        boolean magnetite = s.is(PcItems.RAW_MAGNETITE.get());
        if (!magnetite && !s.is(PcItems.RAW_HEMATITE.get())) return null;
        OreAssayData a = s.get(PcDataComponents.ORE_ASSAY.get());
        double fe = a != null ? a.toAssay().gradeFrac() : magnetite ? 0.35 : 0.575;
        double gangue = Math.max(0, 1 - fe / (magnetite ? MAGNETITE_FE : HEMATITE_FE));
        return new OreChem(fe, gangue * (magnetite ? 0.9 : 0.8), gangue * 0.02);
    }

    /** Магнетит (идёт на магнитную сепарацию). */
    public static boolean magnetic(ItemStack s) {
        return s.is(PcItems.RAW_MAGNETITE.get());
    }
}
