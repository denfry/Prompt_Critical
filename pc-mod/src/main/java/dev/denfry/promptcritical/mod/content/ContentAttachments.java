package dev.denfry.promptcritical.mod.content;

import com.mojang.serialization.Codec;
import dev.denfry.promptcritical.mod.PromptCritical;
import java.util.function.Supplier;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Данные игрока раздела 11: справочник уже выдан (переживает смерть). */
public final class ContentAttachments {
    public static final DeferredRegister<AttachmentType<?>> TYPES = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, PromptCritical.MOD_ID);

    public static final Supplier<AttachmentType<Boolean>> HANDBOOK_GIVEN = TYPES.register("handbook_given",
            () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL).copyOnDeath().build());

    private ContentAttachments() {
    }
}
