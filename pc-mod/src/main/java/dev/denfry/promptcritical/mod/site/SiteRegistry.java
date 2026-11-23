package dev.denfry.promptcritical.mod.site;

import dev.denfry.promptcritical.mod.PromptCritical;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Регистрация стройплощадки и чертежей (спецификация real-scale machines, §3a). */
public final class SiteRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(PromptCritical.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PromptCritical.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PromptCritical.MOD_ID);

    public static final DeferredBlock<ConstructionSiteBlock> SITE = BLOCKS.registerBlock("construction_site", ConstructionSiteBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.5f).sound(SoundType.SCAFFOLDING).noOcclusion());
    public static final DeferredItem<BlockItem> SITE_ITEM = ITEMS.registerSimpleBlockItem(SITE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ConstructionSiteBlockEntity>> SITE_BE =
            BLOCK_ENTITIES.register("construction_site", () -> BlockEntityType.Builder.of(ConstructionSiteBlockEntity::new, SITE.get()).build(null));

    public static final DeferredItem<DesignDrawingItem> VVER_REACTOR_DRAWING = drawing("vver_reactor");
    public static final DeferredItem<DesignDrawingItem> VVER_STEAM_GENERATOR_DRAWING = drawing("vver_steam_generator");
    public static final DeferredItem<DesignDrawingItem> VVER_COOLANT_PUMP_DRAWING = drawing("vver_coolant_pump");
    public static final DeferredItem<DesignDrawingItem> VVER_PRESSURIZER_DRAWING = drawing("vver_pressurizer");
    public static final DeferredItem<DesignDrawingItem> BWR240_REACTOR_DRAWING = drawing("bwr240_reactor");
    public static final DeferredItem<DesignDrawingItem> CANDU6_REACTOR_DRAWING = drawing("candu6_reactor");
    public static final DeferredItem<DesignDrawingItem> BN600_REACTOR_DRAWING = drawing("bn600_reactor");

    private SiteRegistry() {}

    private static DeferredItem<DesignDrawingItem> drawing(String plan) {
        return ITEMS.registerItem(plan + "_drawing", p -> new DesignDrawingItem(p, plan), new Item.Properties().stacksTo(1));
    }

    /** Предметы для вкладки творческого режима. */
    public static List<DeferredItem<? extends Item>> items() {
        return List.of(SITE_ITEM, VVER_REACTOR_DRAWING, VVER_STEAM_GENERATOR_DRAWING, VVER_COOLANT_PUMP_DRAWING, VVER_PRESSURIZER_DRAWING,
                BWR240_REACTOR_DRAWING, CANDU6_REACTOR_DRAWING, BN600_REACTOR_DRAWING);
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(SiteRegistry::capabilities);
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, SITE_BE.get(), (be, side) -> be.items());
    }
}
