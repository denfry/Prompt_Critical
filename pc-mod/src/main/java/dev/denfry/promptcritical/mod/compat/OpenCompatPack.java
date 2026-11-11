package dev.denfry.promptcritical.mod.compat;

import dev.denfry.promptcritical.mod.PromptCritical;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * Встроенный датапак «открытая совместимость» (по умолчанию выключен). Без него руды и сырьё мода лежат только в общих
 * {@code c:ores} и {@code c:raw_materials}, но не в тегах по металлам — переработчики других модов (Mekanism, IE,
 * Create и др.) не удваивают их в слитки мимо цепочки мода. Пак возвращает {@code c:ores/<металл>} и
 * {@code c:raw_materials/<металл>} для сборок, где это нужно: при создании мира или {@code /datapack enable}.
 */
public final class OpenCompatPack {
    /** Корень пака в ресурсах мода; теги генерирует datagen, {@code pack.mcmeta} — в main/resources. */
    public static final String SOURCE = "data/" + PromptCritical.MOD_ID + "/datapacks/open_compat";

    private OpenCompatPack() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(OpenCompatPack::addPack);
    }

    private static void addPack(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) return;
        event.addPackFinders(ResourceLocation.fromNamespaceAndPath(PromptCritical.MOD_ID, SOURCE), PackType.SERVER_DATA,
                Component.translatable("pack." + PromptCritical.MOD_ID + ".open_compat.title"), PackSource.FEATURE, false,
                Pack.Position.TOP);
    }
}
