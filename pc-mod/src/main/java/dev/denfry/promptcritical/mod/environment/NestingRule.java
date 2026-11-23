package dev.denfry.promptcritical.mod.environment;

import dev.denfry.promptcritical.mod.isotopes.IsotopeComponents;
import dev.denfry.promptcritical.sim.radiation.Exemption;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Запрет вложения источников в переносные и «карманные» контейнеры (04d §4d.6.2, 16 §16.8): предмет выше уровня
 * освобождения (17 §17.3.3, {@link Exemption}) не кладётся в шалкер, мешочек и сундук Края. Иначе источник исчез бы
 * из мира вместе с излучением: содержимое шалкера-предмета и сундука Края сканер контейнеров не видит. Проверку
 * вызывают миксины {@code dev.denfry.promptcritical.mod.mixin}.
 */
public final class NestingRule {
    private NestingRule() {
    }

    /** Можно ли положить предмет внутрь переносного контейнера. */
    public static boolean mayNest(ItemStack stack) {
        if (stack.isEmpty()) return true;
        // источник в защитном контейнере (дефектоскоп) — всегда учтённый источник
        if (stack.has(IsotopeComponents.SHIELDED_SOURCE.get())) return false;
        ActivityData a = stack.get(ActivityData.ACTIVITY.get());
        if (a == null) return true;
        // на клиенте игрового времени сервера нет: берём активность на момент записи — она не меньше нынешней
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        long tick = server == null ? a.refTick() : server.overworld().getGameTime();
        var inv = a.inventory(tick);
        return Exemption.exempt(inv.activities(), inv.massKg());
    }
}
