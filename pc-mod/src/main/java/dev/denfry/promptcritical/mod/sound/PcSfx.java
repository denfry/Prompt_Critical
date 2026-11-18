package dev.denfry.promptcritical.mod.sound;

import dev.denfry.promptcritical.mod.PromptCritical;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Синтезированные звуки машин: {@code sfx.<key>} → {@code sounds/sfx/<key>.ogg}. Звук собирает ffmpeg по рецепту
 * из art/sfx/generate.py (он же дописывает sounds.json); ключи здесь и там должны совпадать.
 */
public enum PcSfx {
    /** Двухтональная сирена АГЗ «Метан»: 660/880 Гц попеременно по 0,25 с, цикл 2 с. */
    AGZ_SIREN("agz_siren", "Gas alarm siren wails", "Воет сирена газовой защиты");

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, PromptCritical.MOD_ID);

    public final String key, en, ru;
    private DeferredHolder<SoundEvent, SoundEvent> holder;

    PcSfx(String key, String en, String ru) {
        this.key = key;
        this.en = en;
        this.ru = ru;
    }

    static {
        for (PcSfx s : values()) {
            String id = "sfx." + s.key;
            s.holder = SOUNDS.register(id, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(PromptCritical.MOD_ID, id)));
        }
    }

    public SoundEvent get() {
        return holder.get();
    }

    public String subtitleKey() {
        return "subtitles.promptcritical.sfx." + key;
    }
}
