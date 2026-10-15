package dev.denfry.promptcritical.mod.content;

import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * Глава справочника (11 §11.8). {@code hint} — намёк у закрытой главы («Получите жёлтый кек»), {@code next} — подсказка
 * «Что делать дальше» в конце главы на уровне {@code TRAINING} (11 §11.1.1).
 */
public record HandbookEntry(ResourceLocation id, ResourceLocation category, Localized title, ResourceLocation icon,
        List<HandbookPage> pages, UnlockCondition unlock, int sortIndex, Localized hint, Localized next) {
}
