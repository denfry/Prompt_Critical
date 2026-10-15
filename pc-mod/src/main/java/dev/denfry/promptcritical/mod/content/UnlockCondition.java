package dev.denfry.promptcritical.mod.content;

import net.minecraft.resources.ResourceLocation;

/** Условие открытия главы (11 §11.1.2): отмечает прогресс, ничего не блокирует. */
public sealed interface UnlockCondition permits UnlockCondition.Always, UnlockCondition.AdvancementDone,
        UnlockCondition.ItemObtained, UnlockCondition.BlockPlaced {

    record Always() implements UnlockCondition {
    }

    record AdvancementDone(ResourceLocation advancement) implements UnlockCondition {
    }

    /** Предмет поднят, скрафчен или лежит в инвентаре. */
    record ItemObtained(ResourceLocation item) implements UnlockCondition {
    }

    /** Блок поставлен игроком (статистика «использовано»). */
    record BlockPlaced(ResourceLocation block) implements UnlockCondition {
    }

    UnlockCondition ALWAYS = new Always();
}
