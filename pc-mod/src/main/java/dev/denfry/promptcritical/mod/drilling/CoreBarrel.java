package dev.denfry.promptcritical.mod.drilling;

import dev.denfry.promptcritical.mod.registry.PcDataComponents;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.drilling.FormationFluid;
import dev.denfry.promptcritical.sim.drilling.Well;
import dev.denfry.promptcritical.sim.drilling.WellLogging;
import net.minecraft.world.item.ItemStack;

/**
 * Керноотборник (09 §9.2.6): выбуривает 9 м керна вместо долота. Керн берётся из последних {@link #LENGTH_M} м ствола
 * [допущение: спуско-подъём и отдельный рейс с керноотборником не моделируются — керн из интервала, только что
 * пройденного буровой]; значения — истинные поля залежи в середине интервала.
 */
public final class CoreBarrel {
    public static final double LENGTH_M = 9;

    private CoreBarrel() {
    }

    /** Керн из забоя скважины. */
    public static ItemStack core(Well w) {
        double top = Math.max(w.geometry().shoeDepthM(), w.holeDepthM() - LENGTH_M);
        WellLogging.Point pt = WellLogging.formation(w.prospect(), top + LENGTH_M / 2);
        boolean sand = pt.phi() > 0;
        float k = sand ? (float) w.prospect().trap().permeabilityMd() : 0.001f;
        ItemStack s = new ItemStack(PcItems.RESERVOIR_CORE.get());
        s.set(PcDataComponents.CORE_DATA.get(), new ReservoirCoreItem.Data((float) top, sand, (float) pt.phi(), k, (float) pt.sw(),
                pt.fluid() == FormationFluid.OIL && sand, false, 0, 0, 0));
        return s;
    }
}
