package dev.denfry.promptcritical.mod.compat.journeymap;

import dev.denfry.promptcritical.mod.content.ContentText;
import dev.denfry.promptcritical.mod.content.MapTargetData;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Клиентская сторона §16.5.1: прочитанная {@code geological_map} в руке ставит путевую точку один раз за сеанс. Без
 * JourneyMap цель остаётся в компоненте {@code map_target}, и карта работает как карта сокровищ. Вызывать только
 * при загруженном JourneyMap: класс ссылается на его API.
 */
public final class JourneyMapWaypoints {
    private static final Set<String> OFFERED = new HashSet<>();

    private JourneyMapWaypoints() {}

    public static void held(MapTargetData target, ResourceKey<Level> dimension) {
        String key = dimension.location() + "@" + target.pos().asLong();
        if (OFFERED.contains(key)) return;
        String name = Component.translatable(ContentText.vein(target.vein())).getString();
        if (PcJourneyMapPlugin.offer(target, dimension, name)) OFFERED.add(key);
    }
}
