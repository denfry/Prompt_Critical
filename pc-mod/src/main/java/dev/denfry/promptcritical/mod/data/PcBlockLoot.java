package dev.denfry.promptcritical.mod.data;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.foundry.FoundryComponents;
import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.loot.AssayFunction;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
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
    /** Доля U-235 в руде природного реактора, ppm (около 0,60 % против 0,72 % природной, 11 §11.4.2). */
    public static final int OKLO_U235_PPM = 6000;

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
            } else if (db == PcBlocks.OKLO_URANINITE_ORE) {
                // руда природного реактора: сырьё с обеднённым U-235 (11 §11.4.2), шёлк — сам блок
                add(block, createSilkTouchDispatchTable(block, applyExplosionDecay(block, LootItem.lootTableItem(PcItems.RAW_URANINITE.get())
                        .apply(net.minecraft.world.level.storage.loot.functions.SetComponentsFunction.setComponent(
                                dev.denfry.promptcritical.mod.registry.PcDataComponents.U235_PPM.get(), OKLO_U235_PPM)))));
            } else if (db == PcBlocks.SAND_MOLD) {
                // заформованная форма уносит модель и влажность (08 §8.9)
                add(block, LootTable.lootTable().withPool(applyExplosionCondition(block, LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1)).add(LootItem.lootTableItem(block)
                                .apply(CopyComponentsFunction.copyComponents(CopyComponentsFunction.Source.BLOCK_ENTITY)
                                        .include(FoundryComponents.SAND_MOLD.get()))))));
            } else if (db == PcBlocks.VENTILATION_DOOR) {
                // регулятор — свой предмет того же блока (08 §8.2.7)
                add(block, LootTable.lootTable().withPool(applyExplosionCondition(block, LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1))
                        .add(LootItem.lootTableItem(PcItems.VENTILATION_REGULATOR.get())
                                .when(LootItemBlockStatePropertyCondition.hasBlockStateProperties(block).setProperties(
                                        StatePropertiesPredicate.Builder.properties().hasProperty(PcProperties.DOOR_KIND, PcProperties.DoorKind.REGULATOR)))
                                .otherwise(LootItem.lootTableItem(block))))));
            } else if (block instanceof net.minecraft.world.level.block.SlabBlock) {
                add(block, createSlabItemTable(block));
            } else if (block instanceof PcBlock pc && pc.spec().segment() != null) {
                add(block, controllerOnly(pc, PcBlocks.passportMachines().contains(db)));
            } else if (PcBlocks.passportMachines().contains(db)) {
                // машина со стенда уносит паспорт сборки (18 §18.8.3)
                add(block, LootTable.lootTable().withPool(applyExplosionCondition(block, LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1)).add(LootItem.lootTableItem(block).apply(passport())))));
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
    private static CopyComponentsFunction.Builder passport() {
        return CopyComponentsFunction.copyComponents(CopyComponentsFunction.Source.BLOCK_ENTITY)
                .include(dev.denfry.promptcritical.mod.manufacturing.ManufacturingComponents.MACHINE_PASSPORT.get());
    }

    private LootTable.Builder controllerOnly(PcBlock block, boolean passport) {
        var prop = block.spec().segment();
        String first = ((StringRepresentable) prop.getPossibleValues().iterator().next()).getSerializedName();
        return LootTable.lootTable().withPool(applyExplosionCondition(block, LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1))
                .add(passport ? LootItem.lootTableItem(block).apply(passport()) : LootItem.lootTableItem(block))
                .when(LootItemBlockStatePropertyCondition.hasBlockStateProperties(block)
                        .setProperties(StatePropertiesPredicate.Builder.properties().hasProperty(prop, first)))));
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return PcBlocks.BLOCKS.getEntries().stream().map(DeferredHolder::get).map(b -> (Block) b)::iterator;
    }
}
