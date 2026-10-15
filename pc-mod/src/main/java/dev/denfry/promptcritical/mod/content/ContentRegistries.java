package dev.denfry.promptcritical.mod.content;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.item.CoalAssayData;
import dev.denfry.promptcritical.mod.item.OreAssayData;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcDataComponents;
import dev.denfry.promptcritical.mod.registry.PcItems;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.EmptyLootItem;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Раздел 11 в моде: BE пюпитра, триггер {@code sim_event}, данные игрока, пакет справочника и события мира — выдача
 * книги при входе, инструкция при постройке установки, геологическая карта в сундуках, загрузка справочника.
 */
public final class ContentRegistries {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PromptCritical.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DocumentLecternBlockEntity>> DOCUMENT_LECTERN =
            BLOCK_ENTITIES.register("document_lectern",
                    () -> BlockEntityType.Builder.of(DocumentLecternBlockEntity::new, PcBlocks.DOCUMENT_LECTERN.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WallClockBlock.WallClockBlockEntity>> WALL_CLOCK =
            BLOCK_ENTITIES.register("wall_clock",
                    () -> BlockEntityType.Builder.of(WallClockBlock.WallClockBlockEntity::new, PcBlocks.WALL_CLOCK.get()).build(null));

    /** Сундуки, где попадается геологическая карта (11 §11.4.1; своих структур у M2 нет). */
    public static final Set<ResourceLocation> MAP_CHESTS = Set.of(
            ResourceLocation.withDefaultNamespace("chests/abandoned_mineshaft"),
            ResourceLocation.withDefaultNamespace("chests/village/village_cartographer"),
            ResourceLocation.withDefaultNamespace("chests/shipwreck_map"),
            ResourceLocation.withDefaultNamespace("chests/simple_dungeon"));

    private ContentRegistries() {
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
        PcTriggers.TRIGGERS.register(modBus);
        ContentAttachments.TYPES.register(modBus);
        modBus.addListener(ContentRegistries::payloads);
        NeoForge.EVENT_BUS.addListener(ContentRegistries::reload);
        NeoForge.EVENT_BUS.addListener(ContentRegistries::login);
        NeoForge.EVENT_BUS.addListener(ContentRegistries::placed);
        NeoForge.EVENT_BUS.addListener(ContentRegistries::loot);
        NeoForge.EVENT_BUS.addListener(ContentRegistries::tooltip);
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        event.registrar(PromptCritical.MOD_ID).versioned("1").playToClient(OpenHandbookPayload.TYPE, OpenHandbookPayload.STREAM_CODEC,
                (payload, ctx) -> {
                    if (FMLEnvironment.dist.isClient())
                        ctx.enqueueWork(() -> dev.denfry.promptcritical.mod.content.client.HandbookClient.open(payload.json()));
                });
    }

    private static void reload(AddReloadListenerEvent event) {
        event.addListener(new HandbookManager());
    }

    private static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) HandbookService.giveOnJoin(p);
    }

    private static void placed(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) HandbookService.issueProcedure(p, event.getPlacedBlock().getBlock());
    }

    /** Анализ угля и руды на предмете (08 §8.17): то, что игрок читает в главе «Уголь: марки и анализ». */
    private static void tooltip(ItemTooltipEvent event) {
        CoalAssayData c = event.getItemStack().get(PcDataComponents.COAL_ASSAY.get());
        if (c != null) {
            event.getToolTip().add(Component.translatable(ContentText.COAL_ASSAY, c.volatilePct(), c.ashPct(), c.moisturePct(),
                    String.format(java.util.Locale.ROOT, "%.1f", c.sulfurPm() / 10.0)).withStyle(ChatFormatting.GRAY));
        }
        OreAssayData o = event.getItemStack().get(PcDataComponents.ORE_ASSAY.get());
        if (o != null) {
            event.getToolTip().add(Component.translatable(ContentText.ORE_GRADE, o.gradeClass(),
                    Component.translatable(ContentText.vein(o.veinId()))).withStyle(ChatFormatting.GRAY));
            if (!o.byproduct().isEmpty()) {
                event.getToolTip().add(Component.translatable(ContentText.ORE_BYPRODUCT, o.byproduct(),
                        String.format(java.util.Locale.ROOT, "%.0f", Math.pow(10, o.byproductQ() / 4.0))).withStyle(ChatFormatting.GRAY));
            }
        }
    }

    private static void loot(LootTableLoadEvent event) {
        if (!MAP_CHESTS.contains(event.getName())) return;
        event.getTable().addPool(LootPool.lootPool().name("promptcritical:geological_map").setRolls(ConstantValue.exactly(1))
                .add(LootItem.lootTableItem(PcItems.GEOLOGICAL_MAP.get()).setWeight(1))
                .add(EmptyLootItem.emptyItem().setWeight(2)).build());
    }
}
