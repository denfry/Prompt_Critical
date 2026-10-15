package dev.denfry.promptcritical.mod.content;

import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * Страница главы (11 §11.1.2). Вехе M2 — базовые типы: текст, иллюстрация, рецепт, мультиблок; {@code ponder},
 * {@code formula}, {@code register_map} и {@code lesson} добавляются в своих вехах.
 */
public sealed interface HandbookPage permits HandbookPage.TextPage, HandbookPage.ImagePage, HandbookPage.RecipePage,
        HandbookPage.MultiblockPage {

    /** Упрощённый Markdown из {@code pages/<lang>/<глава>/<file>.md}. */
    record TextPage(String file) implements HandbookPage {
    }

    /**
     * Схема из {@code textures/gui/handbook}; {@code zones} — кликабельные области в пикселях текстуры, ведущие к
     * главам (лестница отраслей 11 §11.1.1).
     */
    record ImagePage(ResourceLocation texture, int width, int height, Localized caption, List<Zone> zones)
            implements HandbookPage {
    }

    record Zone(int x, int y, int w, int h, ResourceLocation entry) {
    }

    /** Рецепт из реестра рецептов (верстак, Create, свои). */
    record RecipePage(ResourceLocation recipe, Localized caption) implements HandbookPage {
    }

    /** Вращаемая схема установки: блоки с состояниями, координаты от угла. */
    record MultiblockPage(Localized name, List<Placed> blocks, Localized caption) implements HandbookPage {
    }

    record Placed(int x, int y, int z, String state) {
    }
}
