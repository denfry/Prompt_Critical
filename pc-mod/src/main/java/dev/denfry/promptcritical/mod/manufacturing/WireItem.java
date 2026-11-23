package dev.denfry.promptcritical.mod.manufacturing;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Пруток или моток проволоки с компонентом {@code wire}: штучный, у каждого свой диаметр и наклёп. */
public final class WireItem extends Item {
    /** Тоньше этого — проволока, толще — пруток, мм [моё]. */
    public static final float ROD_MIN_MM = 5;

    public WireItem(Properties p) {
        super(p.stacksTo(1));
    }

    @Override
    public String getDescriptionId(ItemStack stack) {
        WireData d = stack.get(ManufacturingComponents.WIRE.get());
        return d != null && d.diameterMm() >= ROD_MIN_MM ? super.getDescriptionId(stack) + ".rod" : super.getDescriptionId(stack);
    }
}
