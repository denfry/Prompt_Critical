package dev.denfry.promptcritical.mod.manufacturing;

import dev.denfry.promptcritical.sim.manufacturing.PartDefect;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Поковка (18 §18.7): кусок металла между горном и наковальней с компонентом {@link WorkpieceData}. Крица с невыбитым
 * шлаком называется крицей, остальное — заготовкой; одна штука на стак, у каждой своя история.
 */
public final class WorkpieceItem extends Item {
    public WorkpieceItem(Properties props) {
        super(props.stacksTo(1));
    }

    @Override
    public Component getName(ItemStack stack) {
        WorkpieceData d = stack.get(ManufacturingComponents.WORKPIECE.get());
        if (d != null && d.defects().getOrDefault(PartDefect.SLAG_INCLUSION.name(), 0f) > 0)
            return Component.translatable("item.promptcritical.workpiece.bloom");
        return super.getName(stack);
    }
}
