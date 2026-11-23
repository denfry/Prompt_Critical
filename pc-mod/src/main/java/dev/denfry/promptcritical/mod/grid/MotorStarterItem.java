package dev.denfry.promptcritical.mod.grid;

import dev.denfry.promptcritical.sim.grid.MotorStarter;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Пусковой модуль двигателя (07 §7.6.3): ставится в третье место щита {@code electric_motor}. Звезда–треугольник —
 * только у исполнения 0,4 кВ; без модуля пуск прямой.
 */
public class MotorStarterItem extends Item {
    private final MotorStarter starter;

    public MotorStarterItem(Properties props, MotorStarter starter) {
        super(props);
        this.starter = starter;
    }

    public MotorStarter starter() {
        return starter;
    }

    /** Пускатель по модулю в слоте; пустой слот — прямой пуск. */
    public static MotorStarter of(ItemStack stack) {
        return stack.getItem() instanceof MotorStarterItem m ? m.starter : MotorStarter.DIRECT;
    }
}
