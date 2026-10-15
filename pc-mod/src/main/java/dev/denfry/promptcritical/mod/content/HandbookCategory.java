package dev.denfry.promptcritical.mod.content;

import net.minecraft.resources.ResourceLocation;

/** Раздел справочника: эпоха или отрасль (11 §11.1.2). */
public record HandbookCategory(ResourceLocation id, Localized title, ResourceLocation icon, int sortIndex) {
}
