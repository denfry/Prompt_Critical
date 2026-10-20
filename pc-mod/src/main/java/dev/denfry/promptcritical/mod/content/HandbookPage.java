package dev.denfry.promptcritical.mod.content;

import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * Страница главы (11 §11.1.2): текст, иллюстрация, рецепт, мультиблок, формула, Ponder; {@code register_map} (из
 * {@code DeviceDescriptor}, вместе с Modbus) и {@code lesson} добавляются в своих вехах.
 */
public sealed interface HandbookPage permits HandbookPage.TextPage, HandbookPage.ImagePage, HandbookPage.RecipePage,
        HandbookPage.MultiblockPage, HandbookPage.FormulaPage, HandbookPage.PonderPage {

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

    /**
     * Формула, отрисованная при сборке из {@code src/main/formulas/<путь>.tex} в
     * {@code textures/gui/handbook/formula/<путь>.png} (задача {@code renderHandbookFormulas}); {@code vars} —
     * расшифровка обозначений.
     */
    record FormulaPage(ResourceLocation formula, List<Variable> vars, Localized caption) implements HandbookPage {
        public ResourceLocation texture() {
            return ResourceLocation.fromNamespaceAndPath(formula.getNamespace(), "textures/gui/handbook/formula/" + formula.getPath() + ".png");
        }
    }

    record Variable(String symbol, Localized text) {
    }

    /** Кнопка «Показать»: Ponder-сцены предмета {@code item} (11 §11.1.4). */
    record PonderPage(ResourceLocation item, Localized caption) implements HandbookPage {
    }
}
