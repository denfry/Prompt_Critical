package dev.denfry.promptcritical.mod.manufacturing;

import dev.denfry.promptcritical.sim.manufacturing.Workpiece;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Горячая поковка в инвентаре (18 §18.7): раз в секунду проверяет инвентарь игрока; поковка горячее
 * {@link #BURN_K} без клещей в инвентаре жжёт руки — урон огнём, от которого спасает и огнестойкость. Чем горячее
 * самый горячий кусок, тем сильнее ожог.
 */
public final class HotHandling {
    /** Голой рукой не взять поковку горячее 60 °C [моё]. */
    public static final double BURN_K = Smithy.HANDLE_MAX_K;
    /** Выше красного каления (600 °C) ожог втрое сильнее [BALANCE]. */
    public static final double RED_HOT_K = 873.15;
    /** Урон за секунду: тёплая поковка и раскалённая [BALANCE]. */
    public static final float BURN_DAMAGE = 1, RED_HOT_DAMAGE = 3;

    private HotHandling() {
    }

    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.isCreative() || p.isSpectator() || p.tickCount % 20 != 0) return;
        double hottest = hottestK(p.getInventory(), p.level().getGameTime());
        if (hottest <= BURN_K || hasTongs(p.getInventory())) return;
        if (p.hurt(p.damageSources().hotFloor(), hottest > RED_HOT_K ? RED_HOT_DAMAGE : BURN_DAMAGE))
            p.displayClientMessage(ManufacturingText.msg("hot.burn"), true);
    }

    /** Температура самой горячей поковки в инвентаре, К; без поковок — 0. */
    public static double hottestK(Inventory inv, long now) {
        double max = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.has(ManufacturingComponents.WORKPIECE.get())) continue;
            Workpiece w = Smithy.read(s, now);
            if (w != null) max = Math.max(max, w.tempK());
        }
        return max;
    }

    public static boolean hasTongs(Inventory inv) {
        return inv.hasAnyMatching(s -> s.is(ManufacturingItems.TONGS.get()));
    }
}
