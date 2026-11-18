package dev.denfry.promptcritical.mod.nonferrous;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Концентрат и штейн: имя по линии ({@link NonferrousComponents#NICKEL_LINE}) — медный или медно-никелевый. */
public class NonferrousLineItem extends Item {
    public NonferrousLineItem(Properties props) {
        super(props);
    }

    @Override
    public Component getName(ItemStack stack) {
        return NonferrousComponents.nickel(stack) ? Component.translatable(getDescriptionId() + ".nickel") : super.getName(stack);
    }
}
