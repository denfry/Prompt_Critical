package dev.denfry.promptcritical.mod.steelworks;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Слиток, прокат и трубы с компонентом {@code steel_stock}: горячий металл остывает сам (температура — по времени
 * выдачи), в инвентаре выше 100 °C жжёт, выше 500 °C поджигает; остывший теряет горячие поля и стекается с холодным.
 */
public class SteelStockItem extends Item {
    public static final double BURN_K = 373.15, IGNITE_K = 773.15;

    public SteelStockItem(Properties props) {
        super(props);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || level.getGameTime() % 20 != 0) return;
        SteelworksComponents.SteelStock s = stack.get(SteelworksComponents.STEEL_STOCK.get());
        if (s == null || !s.hot()) return;
        long now = level.getGameTime();
        double t = s.tempK(now);
        if (t <= SteelworksComponents.COLD_K) {
            stack.set(SteelworksComponents.STEEL_STOCK.get(), s.withTemp(t, now));
            return;
        }
        if (t < BURN_K || !(entity instanceof LivingEntity le) || entity instanceof Player p && p.getAbilities().instabuild) return;
        le.hurt(level.damageSources().hotFloor(), t > IGNITE_K ? 2 : 1);
        if (t > IGNITE_K) le.igniteForSeconds(2);
    }
}
