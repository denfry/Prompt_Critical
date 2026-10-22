package dev.denfry.promptcritical.mod.pa;

import dev.denfry.promptcritical.mod.PromptCritical;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Звуки громкой связи: {@code pa.<язык>.<фраза>} на каждый озвученный язык. */
public final class PaSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, PromptCritical.MOD_ID);

    private static final Map<String, DeferredHolder<SoundEvent, SoundEvent>> BY_ID = new HashMap<>();

    static {
        for (PaLine line : PaLine.values()) {
            for (String lang : PaLine.VOICED) {
                String id = id(line, lang);
                BY_ID.put(id, SOUNDS.register(id,
                        () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(PromptCritical.MOD_ID, id))));
            }
        }
    }

    private PaSounds() {
    }

    static Holder<SoundEvent> get(PaLine line, String lang) {
        return BY_ID.get(id(line, lang));
    }

    private static String id(PaLine line, String lang) {
        return "pa." + lang + "." + line.key;
    }
}
