package dev.denfry.promptcritical.mod.steelworks;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Ферросплав: вид — компонентом {@code ferroalloy}, название — по виду (08 §8.6.2). */
public final class FerroalloyItem extends Item {
    public FerroalloyItem(Properties props) {
        super(props);
    }

    @Override
    public Component getName(ItemStack stack) {
        String id = stack.get(SteelworksComponents.FERROALLOY.get());
        return id == null ? super.getName(stack) : SteelworksText.msg("alloy." + id);
    }
}
