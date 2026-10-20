package dev.denfry.promptcritical.mod.content;

import com.mojang.serialization.Codec;
import dev.denfry.promptcritical.mod.PromptCritical;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Данные игрока раздела 11 (переживают смерть): справочник уже выдан, выполненные задания маршрута (11 §11.1.5). */
public final class ContentAttachments {
    public static final DeferredRegister<AttachmentType<?>> TYPES = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, PromptCritical.MOD_ID);

    public static final Supplier<AttachmentType<Boolean>> HANDBOOK_GIVEN = TYPES.register("handbook_given",
            () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL).copyOnDeath().build());

    public static final Supplier<AttachmentType<Set<ResourceLocation>>> ROUTE_DONE = TYPES.register("route_done",
            () -> AttachmentType.<Set<ResourceLocation>>builder(() -> Set.of())
                    .serialize(ResourceLocation.CODEC.listOf().xmap(Set::copyOf, java.util.List::copyOf)).copyOnDeath().build());

    private ContentAttachments() {
    }
}
