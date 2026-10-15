package dev.denfry.promptcritical.mod.data;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.foundry.FoundryComponents;
import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.loot.AssayFunction;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import java.util.Map;
import java.util.Set;
import net.minecraft.advancements.critereon.StatePropertiesPredicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount;
import net.minecraft.world.level.storage.loot.functions.CopyComponentsFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Лут блоков: руды — сырьё (шёлковое касание — сам блок, удача — как у ванильных руд); многосегментные
 * конструкции — предмет только с контроллера; остальное — сам блок. Клетки {@code multiblock_part} без лута.
 */
public final class PcBlockLoot extends BlockLootSubProvider {
    public PcBlockLoot(HolderLookup.Provider registries) {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
    }

    @Override
    protected void generate() {
        Map<DeferredBlock<? extends Block>, String> ores = PcBlocks.oreDrops();
        for (DeferredBlock<? extends Block> db : PcBlocks.withItems()) {
            Block block = db.get();
            String raw = ores.get(db);
            if (raw != null) {
                Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(PromptCritical.MOD_ID, raw));
                add(block, assayedOreDrop(block, item));
            } else if (PcBlocks.selfAssayed().contains(db)) {
                add(block, LootTable.lootTable().withPool(applyExplosionCondition(block, LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1)).add(LootItem.lootTableItem(block).apply(AssayFunction.assay())))));
            } else if (db == PcBlocks.SAND_MOLD) {
                // заформованная форма уносит модель и влажность (08 §8.9)
                add(block, LootTable.lootTable().withPool(applyExplosionCondition(block, LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1)).add(LootItem.lootTableItem(block)
                                .apply(CopyComponentsFunction.copyComponents(CopyComponentsFunction.Source.BLOCK_ENTITY)
                                        .include(FoundryComponents.SAND_MOLD.get()))))));
            } else if (block instanceof PcBlock pc && pc.spec().segment() != null) {
                add(block, controllerOnly(pc));
            } else {
                dropSelf(block);
            }
        }
    }

    /** Как ванильная руда (шёлк — блок, удача — больше сырья), сырьё несёт анализ места добычи (08 §8.1.3). */
    private LootTable.Builder assayedOreDrop(Block block, Item item) {
        HolderLookup.RegistryLookup<Enchantment> enchantments = registries.lookupOrThrow(Registries.ENCHANTMENT);
        return createSilkTouchDispatchTable(block, applyExplosionDecay(block, LootItem.lootTableItem(item)
                .apply(ApplyBonusCount.addOreBonusCount(enchantments.getOrThrow(Enchantments.FORTUNE)))
                .apply(AssayFunction.assay())));
    }

    /** Предмет выпадает только с сегмента 0 (контроллера). */
    private LootTable.Builder controllerOnly(PcBlock block) {
        var prop = block.spec().segment();
        String first = ((StringRepresentable) prop.getPossibleValues().iterator().next()).getSerializedName();
        return LootTable.lootTable().withPool(applyExplosionCondition(block, LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1))
                .add(LootItem.lootTableItem(block))
                .when(LootItemBlockStatePropertyCondition.hasBlockStateProperties(block)
                        .setProperties(StatePropertiesPredicate.Builder.properties().hasProperty(prop, first)))));
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return PcBlocks.BLOCKS.getEntries().stream().map(DeferredHolder::get).map(b -> (Block) b)::iterator;
    }
}
