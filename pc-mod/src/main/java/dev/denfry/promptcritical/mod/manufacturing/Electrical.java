package dev.denfry.promptcritical.mod.manufacturing;

import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.mod.steelworks.SteelworksComponents;
import dev.denfry.promptcritical.sim.manufacturing.CoreStack;
import dev.denfry.promptcritical.sim.manufacturing.ElectricalSheet;
import dev.denfry.promptcritical.sim.manufacturing.Lamination;
import dev.denfry.promptcritical.sim.manufacturing.SheetCoating;
import dev.denfry.promptcritical.sim.manufacturing.Stator;
import dev.denfry.promptcritical.sim.manufacturing.Wire;
import dev.denfry.promptcritical.sim.manufacturing.WireInsulation;
import dev.denfry.promptcritical.sim.metallurgy.AlloyElement;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Электротехнические детали в предметах (18 §18.6.8–18.6.9): стальной лист цеха как подкат, лист, пачка пластин,
 * пакет, статор и покрывные материалы печи.
 */
public final class Electrical {
    /** Подкат толще этого холодным не катают: сначала горячая прокатка в цехе, м [сверить: подкат 2–3 мм]. */
    public static final double HOT_BAND_MAX_M = 0.004;

    private Electrical() {
    }

    /** Стальной лист цеха не толще {@link #HOT_BAND_MAX_M}: подкат стана. */
    public static boolean isHotBand(ItemStack s) {
        var st = s.get(SteelworksComponents.STEEL_STOCK.get());
        return st != null && s.is(PcItems.STEEL_PLATE.get()) && st.thicknessM() <= HOT_BAND_MAX_M;
    }

    /** Подкат как лист 1,0 × 0,5 м его толщины с кремнием плавки; иначе null. */
    @Nullable
    public static ElectricalSheet hotBand(ItemStack s) {
        if (!isHotBand(s)) return null;
        var st = s.get(SteelworksComponents.STEEL_STOCK.get());
        return ElectricalSheet.hotBand(st.thicknessM(), Math.min(0.1, st.grade().frac(AlloyElement.SI)));
    }

    @Nullable
    public static ElectricalSheet sheet(ItemStack s) {
        var d = s.get(ManufacturingComponents.ELECTRICAL_SHEET.get());
        return d == null ? null : d.sheet();
    }

    public static ItemStack sheetStack(ElectricalSheet sh, int count) {
        ItemStack s = new ItemStack(ManufacturingItems.ELECTRICAL_SHEET.get(), count);
        s.set(ManufacturingComponents.ELECTRICAL_SHEET.get(), ElectricalData.Sheet.of(sh));
        return s;
    }

    @Nullable
    public static Lamination lamination(ItemStack s) {
        var d = s.get(ManufacturingComponents.LAMINATION.get());
        return d == null ? null : d.lamination();
    }

    public static ItemStack bundleStack(Lamination l, int count) {
        ItemStack s = new ItemStack(ManufacturingItems.LAMINATION.get(), count);
        s.set(ManufacturingComponents.LAMINATION.get(), ElectricalData.Bundle.of(l));
        return s;
    }

    @Nullable
    public static CoreStack core(ItemStack s) {
        var d = s.get(ManufacturingComponents.STATOR_CORE.get());
        return d == null ? null : d.stack();
    }

    public static ItemStack coreStack(CoreStack c) {
        ItemStack s = new ItemStack(ManufacturingItems.STATOR_CORE.get());
        s.set(ManufacturingComponents.STATOR_CORE.get(), ElectricalData.Core.of(c));
        return s;
    }

    @Nullable
    public static Stator stator(ItemStack s) {
        var d = s.get(ManufacturingComponents.STATOR.get());
        return d == null ? null : d.stator();
    }

    public static ItemStack statorStack(Stator st) {
        ItemStack s = new ItemStack(ManufacturingItems.STATOR.get());
        s.set(ManufacturingComponents.STATOR.get(), ElectricalData.Wound.of(st));
        return s;
    }

    /** Обмоточный провод: проволока в изоляции. */
    public static ItemStack magnetWireStack(Wire w, WireInsulation ins) {
        ItemStack s = new ItemStack(ManufacturingItems.WIRE.get());
        s.set(ManufacturingComponents.WIRE.get(), WireData.of(w, ins));
        return s;
    }

    /** Покрытие листа из материала: бумага — прокладка, фенольная смола — лак; иначе null. */
    @Nullable
    public static SheetCoating sheetCoating(ItemStack s) {
        if (s.is(Items.PAPER)) return SheetCoating.PAPER;
        if (s.is(PcItems.PHENOLIC_RESIN.get())) return SheetCoating.PHENOLIC;
        return null;
    }

    /** Изоляция провода из материала: бумажная обмотка (A) или фенольная эмаль (B); иначе null. */
    @Nullable
    public static WireInsulation wireInsulation(ItemStack s) {
        if (s.is(Items.PAPER)) return WireInsulation.PAPER;
        if (s.is(PcItems.PHENOLIC_RESIN.get())) return WireInsulation.ENAMEL;
        return null;
    }

    public static boolean isCoatingMaterial(ItemStack s) {
        return sheetCoating(s) != null;
    }

    /** Лист, пачка пластин или проволока: идут в печь на отжиг и под лак. */
    public static boolean isElectricalCharge(ItemStack s) {
        return s.has(ManufacturingComponents.ELECTRICAL_SHEET.get()) || s.has(ManufacturingComponents.LAMINATION.get());
    }
}
